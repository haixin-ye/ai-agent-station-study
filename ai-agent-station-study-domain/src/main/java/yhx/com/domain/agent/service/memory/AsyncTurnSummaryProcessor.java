package yhx.com.domain.agent.service.memory;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.TurnSummaryInputVO;
import yhx.com.domain.agent.model.valobj.memory.TurnSummaryOutputVO;
import yhx.com.domain.agent.service.node.turnsummary.TurnSummaryNodeService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

public class AsyncTurnSummaryProcessor implements TurnCompletionPublisher {

    private static final int MAX_FAILURE_MESSAGE_CHARS = 4000;

    private final Executor executor;
    private final ITurnRepository turnRepository;
    private final ITurnSummaryRepository summaryRepository;
    private final IMemoryTaskRepository taskRepository;
    private final IPayloadRepository payloadRepository;
    private final TurnSummaryNodeService nodeService;

    public AsyncTurnSummaryProcessor(Executor executor,
                                     ITurnRepository turnRepository,
                                     ITurnSummaryRepository summaryRepository,
                                     IMemoryTaskRepository taskRepository,
                                     IPayloadRepository payloadRepository,
                                     TurnSummaryNodeService nodeService) {
        this.executor = executor;
        this.turnRepository = turnRepository;
        this.summaryRepository = summaryRepository;
        this.taskRepository = taskRepository;
        this.payloadRepository = payloadRepository;
        this.nodeService = nodeService;
    }

    @Override
    public void onTurnCompleted(String turnId) {
        if (turnId == null || turnId.isBlank() || executor == null || taskRepository == null) {
            return;
        }
        String taskId = taskRepository.createTask(AgentMemoryTaskEntity.builder()
                .taskType("TURN_SUMMARY")
                .turnId(turnId)
                .status("PENDING")
                .attemptCount(0)
                .createdAt(LocalDateTime.now())
                .build());
        executor.execute(() -> runTask(taskId, turnId));
    }

    private void runTask(String taskId, String turnId) {
        try {
            taskRepository.markRunning(taskId);
            AgentTurnEntity turn = turnRepository.findByTurnId(turnId)
                    .orElseThrow(() -> new IllegalArgumentException("Turn not found: " + turnId));
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
            summaryRepository.saveSummary(AgentTurnSummaryEntity.builder()
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
                    .build());
            taskRepository.markSucceeded(taskId, summaryRef);
        } catch (Exception e) {
            taskRepository.markFailed(taskId, "TURN_SUMMARY_FAILED", truncate(e.getMessage()));
        }
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
