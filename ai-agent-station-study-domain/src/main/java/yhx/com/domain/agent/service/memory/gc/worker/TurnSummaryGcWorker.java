package yhx.com.domain.agent.service.memory.gc.worker;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.TurnSummaryInputVO;
import yhx.com.domain.agent.model.valobj.memory.TurnSummaryOutputVO;
import yhx.com.domain.agent.service.memory.MemoryVectorIndexingService;
import yhx.com.domain.agent.service.memory.gc.MemoryGcFollowupScheduler;
import yhx.com.domain.agent.service.node.turnsummary.TurnSummaryNodeService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class TurnSummaryGcWorker implements MemoryGcTaskWorker {

    private static final int MAX_FAILURE_MESSAGE_CHARS = 4000;

    private final ITurnRepository turnRepository;
    private final ITurnSummaryRepository summaryRepository;
    private final IMemoryTaskRepository taskRepository;
    private final IPayloadRepository payloadRepository;
    private final TurnSummaryNodeService nodeService;
    private final MemoryVectorIndexingService vectorIndexingService;
    private final MemoryGcFollowupScheduler followupScheduler;

    public TurnSummaryGcWorker(ITurnRepository turnRepository,
                               ITurnSummaryRepository summaryRepository,
                               IMemoryTaskRepository taskRepository,
                               IPayloadRepository payloadRepository,
                               TurnSummaryNodeService nodeService,
                               MemoryVectorIndexingService vectorIndexingService) {
        this(turnRepository, summaryRepository, taskRepository, payloadRepository, nodeService, vectorIndexingService, null);
    }

    public TurnSummaryGcWorker(ITurnRepository turnRepository,
                               ITurnSummaryRepository summaryRepository,
                               IMemoryTaskRepository taskRepository,
                               IPayloadRepository payloadRepository,
                               TurnSummaryNodeService nodeService,
                               MemoryVectorIndexingService vectorIndexingService,
                               MemoryGcFollowupScheduler followupScheduler) {
        this.turnRepository = turnRepository;
        this.summaryRepository = summaryRepository;
        this.taskRepository = taskRepository;
        this.payloadRepository = payloadRepository;
        this.nodeService = nodeService;
        this.vectorIndexingService = vectorIndexingService;
        this.followupScheduler = followupScheduler;
    }

    @Override
    public String taskType() {
        return MemoryTaskTypeEnumVO.TURN_SUMMARY.name();
    }

    @Override
    public void handle(String taskId) {
        try {
            taskRepository.markRunning(taskId);
            AgentMemoryTaskEntity task = taskRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("Memory task not found: " + taskId));
            AgentTurnEntity turn = turnRepository.findByTurnId(task.getTurnId())
                    .orElseThrow(() -> new IllegalArgumentException("Turn not found: " + task.getTurnId()));
            summarize(turn, taskId);
        } catch (Exception e) {
            taskRepository.markFailed(taskId, "TURN_SUMMARY_FAILED", truncate(e.getMessage()));
        }
    }

    public void handleTurn(String taskId, String turnId) {
        try {
            taskRepository.markRunning(taskId);
            AgentTurnEntity turn = turnRepository.findByTurnId(turnId)
                    .orElseThrow(() -> new IllegalArgumentException("Turn not found: " + turnId));
            summarize(turn, taskId);
        } catch (Exception e) {
            taskRepository.markFailed(taskId, "TURN_SUMMARY_FAILED", truncate(e.getMessage()));
        }
    }

    private void summarize(AgentTurnEntity turn, String taskId) {
        String userInput = loadPayloadContent(turn.getUserPayloadRef());
        String finalAnswer = loadPayloadContent(turn.getAssistantPayloadRef());
        TurnSummaryOutputVO output = nodeService.summarize(TurnSummaryInputVO.builder()
                .runId(turn.getRunId())
                .sessionId(turn.getSessionId())
                .turnId(turn.getTurnId())
                .userInput(userInput)
                .finalAnswer(finalAnswer)
                .evidenceIds(List.of())
                .artifactIds(List.of())
                .build(), turn.getAgentId(), null);
        if (output == null || output.getSummary() == null || output.getSummary().isBlank()) {
            throw new IllegalStateException("TURN_SUMMARY returned empty summary.");
        }
        String summaryRef = payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(output))
                .preview(preview(output.getSummary()))
                .createdAt(LocalDateTime.now())
                .build());
        AgentTurnSummaryEntity summary = AgentTurnSummaryEntity.builder()
                .summaryId("turn-summary-" + UUID.randomUUID())
                .turnId(turn.getTurnId())
                .sessionId(turn.getSessionId())
                .runId(turn.getRunId())
                .userId(turn.getUserId())
                .summaryRef(summaryRef)
                .intent(output.getIntent())
                .topicsJson(JSON.toJSONString(output.getTopics() == null ? List.of() : output.getTopics()))
                .entitiesJson(JSON.toJSONString(output.getEntities() == null ? List.of() : output.getEntities()))
                .artifactRefsJson(JSON.toJSONString(output.getArtifactRefs() == null ? List.of() : output.getArtifactRefs()))
                .importanceScore(output.getImportanceScore())
                .requiresLongTermExtraction(Boolean.TRUE.equals(output.getRequiresLongTermExtraction()))
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        summaryRepository.saveSummary(summary);
        if (vectorIndexingService != null) {
            vectorIndexingService.indexTurnSummary(turn, summary, output);
        }
        taskRepository.markSucceeded(taskId, summaryRef);
        scheduleExtractionIfNeeded(turn, output, summaryRef);
    }

    private void scheduleExtractionIfNeeded(AgentTurnEntity turn, TurnSummaryOutputVO output, String summaryRef) {
        if (followupScheduler == null || !Boolean.TRUE.equals(output.getRequiresLongTermExtraction())) {
            return;
        }
        followupScheduler.createAndDispatch(MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name(),
                turn.getTurnId(),
                turn.getRunId(),
                turn.getSessionId(),
                summaryRef);
    }

    private String loadPayloadContent(String payloadRef) {
        if (payloadRepository == null || payloadRef == null || payloadRef.isBlank()) {
            return null;
        }
        return payloadRepository.findPayload(payloadRef)
                .map(payload -> firstNonBlank(payload.getContent(), payload.getPreview()))
                .orElse(null);
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= 200 ? content : content.substring(0, 200);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_FAILURE_MESSAGE_CHARS ? message : message.substring(0, MAX_FAILURE_MESSAGE_CHARS);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }
}
