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
import yhx.com.domain.agent.service.observability.AutoAgentHumanLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class TurnSummaryGcWorker implements MemoryGcTaskWorker {

    private static final int MAX_FAILURE_MESSAGE_CHARS = 5000;
    private static final int DEFAULT_SESSION_TASK_SUMMARY_THRESHOLD = 5;
    private static final int DEFAULT_MEMORY_GOVERNANCE_THRESHOLD = 5;
    private static final int DEFAULT_SUMMARY_SELF_CHECK_THRESHOLD = 3;

    private final ITurnRepository turnRepository;
    private final ITurnSummaryRepository summaryRepository;
    private final IMemoryTaskRepository taskRepository;
    private final IPayloadRepository payloadRepository;
    private final TurnSummaryNodeService nodeService;
    private final MemoryVectorIndexingService vectorIndexingService;
    private final MemoryGcFollowupScheduler followupScheduler;
    private final int sessionTaskSummaryThreshold;
    private final int memoryGovernanceThreshold;
    private final int summarySelfCheckThreshold;

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
        this(turnRepository,
                summaryRepository,
                taskRepository,
                payloadRepository,
                nodeService,
                vectorIndexingService,
                followupScheduler,
                DEFAULT_SESSION_TASK_SUMMARY_THRESHOLD,
                DEFAULT_MEMORY_GOVERNANCE_THRESHOLD,
                DEFAULT_SUMMARY_SELF_CHECK_THRESHOLD);
    }

    public TurnSummaryGcWorker(ITurnRepository turnRepository,
                               ITurnSummaryRepository summaryRepository,
                               IMemoryTaskRepository taskRepository,
                               IPayloadRepository payloadRepository,
                               TurnSummaryNodeService nodeService,
                               MemoryVectorIndexingService vectorIndexingService,
                               MemoryGcFollowupScheduler followupScheduler,
                               int sessionTaskSummaryThreshold) {
        this(turnRepository,
                summaryRepository,
                taskRepository,
                payloadRepository,
                nodeService,
                vectorIndexingService,
                followupScheduler,
                sessionTaskSummaryThreshold,
                DEFAULT_MEMORY_GOVERNANCE_THRESHOLD,
                DEFAULT_SUMMARY_SELF_CHECK_THRESHOLD);
    }

    public TurnSummaryGcWorker(ITurnRepository turnRepository,
                               ITurnSummaryRepository summaryRepository,
                               IMemoryTaskRepository taskRepository,
                               IPayloadRepository payloadRepository,
                               TurnSummaryNodeService nodeService,
                               MemoryVectorIndexingService vectorIndexingService,
                               MemoryGcFollowupScheduler followupScheduler,
                               int sessionTaskSummaryThreshold,
                               int memoryGovernanceThreshold) {
        this(turnRepository,
                summaryRepository,
                taskRepository,
                payloadRepository,
                nodeService,
                vectorIndexingService,
                followupScheduler,
                sessionTaskSummaryThreshold,
                memoryGovernanceThreshold,
                DEFAULT_SUMMARY_SELF_CHECK_THRESHOLD);
    }

    public TurnSummaryGcWorker(ITurnRepository turnRepository,
                               ITurnSummaryRepository summaryRepository,
                               IMemoryTaskRepository taskRepository,
                               IPayloadRepository payloadRepository,
                               TurnSummaryNodeService nodeService,
                               MemoryVectorIndexingService vectorIndexingService,
                               MemoryGcFollowupScheduler followupScheduler,
                               int sessionTaskSummaryThreshold,
                               int memoryGovernanceThreshold,
                               int summarySelfCheckThreshold) {
        this.turnRepository = turnRepository;
        this.summaryRepository = summaryRepository;
        this.taskRepository = taskRepository;
        this.payloadRepository = payloadRepository;
        this.nodeService = nodeService;
        this.vectorIndexingService = vectorIndexingService;
        this.followupScheduler = followupScheduler;
        this.sessionTaskSummaryThreshold = sessionTaskSummaryThreshold <= 0 ? DEFAULT_SESSION_TASK_SUMMARY_THRESHOLD : sessionTaskSummaryThreshold;
        this.memoryGovernanceThreshold = memoryGovernanceThreshold <= 0 ? DEFAULT_MEMORY_GOVERNANCE_THRESHOLD : memoryGovernanceThreshold;
        this.summarySelfCheckThreshold = summarySelfCheckThreshold <= 0 ? DEFAULT_SUMMARY_SELF_CHECK_THRESHOLD : summarySelfCheckThreshold;
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
        AgentTurnSummaryEntity existing = summaryRepository.findSummaryByTurnId(turn.getTurnId()).orElse(null);
        if (existing != null) {
            reindexExistingSummaryIfPossible(turn, existing);
            scheduleLongTermExtraction(turn, existing.getSummaryRef());
            scheduleFollowupsByTurnCount(turn, existing.getSummaryRef());
            taskRepository.markSucceeded(taskId, existing.getSummaryRef());
            return;
        }
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
        scheduleLongTermExtraction(turn, summaryRef);
        scheduleFollowupsByTurnCount(turn, summaryRef);
        taskRepository.markSucceeded(taskId, summaryRef);
    }

    private void reindexExistingSummaryIfPossible(AgentTurnEntity turn, AgentTurnSummaryEntity existing) {
        if (vectorIndexingService == null || existing == null || isBlank(existing.getSummaryRef())) {
            return;
        }
        String payload = loadPayloadContent(existing.getSummaryRef());
        if (isBlank(payload)) {
            return;
        }
        try {
            TurnSummaryOutputVO output = JSON.parseObject(payload, TurnSummaryOutputVO.class);
            vectorIndexingService.indexTurnSummary(turn, existing, output);
        } catch (Exception ignored) {
            // The existing MySQL summary is still valid; failed vector repair should not keep retrying the same turn forever.
        }
    }

    private void scheduleLongTermExtraction(AgentTurnEntity turn, String summaryRef) {
        if (followupScheduler == null) {
            return;
        }
        followupScheduler.createAndDispatchIfNoTurnTask(MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name(),
                turn.getTurnId(),
                turn.getRunId(),
                turn.getSessionId(),
                summaryRef);
    }

    private void scheduleFollowupsByTurnCount(AgentTurnEntity turn, String summaryRef) {
        if (followupScheduler == null || turn.getSessionId() == null || turn.getSessionId().isBlank()) {
            return;
        }
        int activeCount = summaryRepository.countActiveSummaries(turn.getSessionId());
        if (shouldSchedule(activeCount, sessionTaskSummaryThreshold)) {
            followupScheduler.createAndDispatchIfNoOpenSessionTask(MemoryTaskTypeEnumVO.SESSION_TASK_SUMMARY.name(),
                    turn.getTurnId(),
                    turn.getRunId(),
                    turn.getSessionId(),
                    summaryRef);
        }
        scheduleSummarySelfCheck(turn, summaryRef);
        scheduleGlobalMemoryGovernance(turn, summaryRef);
    }

    private void scheduleSummarySelfCheck(AgentTurnEntity turn, String summaryRef) {
        int globalActiveCount = summaryRepository.countAllActiveSummaries();
        int progress = thresholdProgress(globalActiveCount, summarySelfCheckThreshold);
        if (!shouldSchedule(globalActiveCount, summarySelfCheckThreshold)) {
            AutoAgentHumanLog.stage("摘要存储自检", turn.getRunId(), "摘要存储自检未触发："
                    + progress + "/" + summarySelfCheckThreshold
                    + "，检查范围=近50轮"
                    + "，全局ACTIVE摘要=" + globalActiveCount);
            return;
        }
        String taskId = followupScheduler.createAndDispatchIfNoOpenTaskType(MemoryTaskTypeEnumVO.TURN_SUMMARY_SELF_CHECK.name(),
                turn.getTurnId(),
                turn.getRunId(),
                turn.getSessionId(),
                summaryRef);
        if (taskId == null) {
            AutoAgentHumanLog.stage("摘要存储自检", turn.getRunId(), "摘要存储自检达到触发条件但未创建新任务："
                    + summarySelfCheckThreshold + "/" + summarySelfCheckThreshold
                    + "，检查范围=近50轮"
                    + "，原因=已有自检任务待执行或正在执行。");
            return;
        }
        AutoAgentHumanLog.stage("摘要存储自检", turn.getRunId(), "摘要存储自检已触发："
                + summarySelfCheckThreshold + "/" + summarySelfCheckThreshold
                + "，检查范围=近50轮"
                + "，taskId=" + taskId);
    }

    private void scheduleGlobalMemoryGovernance(AgentTurnEntity turn, String summaryRef) {
        int globalActiveCount = summaryRepository.countAllActiveSummaries();
        int progress = thresholdProgress(globalActiveCount, memoryGovernanceThreshold);
        if (!shouldSchedule(globalActiveCount, memoryGovernanceThreshold)) {
            AutoAgentHumanLog.stage("长期记忆治理", turn.getRunId(), "长期记忆治理未触发："
                    + progress + "/" + memoryGovernanceThreshold
                    + "，全局ACTIVE摘要=" + globalActiveCount
                    + "，当前session=" + turn.getSessionId());
            return;
        }
        String taskId = followupScheduler.createAndDispatchIfNoOpenTaskType(MemoryTaskTypeEnumVO.MEMORY_GOVERNANCE.name(),
                turn.getTurnId(),
                turn.getRunId(),
                turn.getSessionId(),
                summaryRef);
        if (taskId == null) {
            AutoAgentHumanLog.stage("长期记忆治理", turn.getRunId(), "长期记忆治理达到触发条件但未创建新任务："
                    + memoryGovernanceThreshold + "/" + memoryGovernanceThreshold
                    + "，全局ACTIVE摘要=" + globalActiveCount
                    + "，原因=已有全局治理任务待执行或正在执行。");
            return;
        }
        AutoAgentHumanLog.stage("长期记忆治理", turn.getRunId(), "长期记忆治理已触发："
                + memoryGovernanceThreshold + "/" + memoryGovernanceThreshold
                + "，全局ACTIVE摘要=" + globalActiveCount
                + "，taskId=" + taskId);
    }

    private boolean shouldSchedule(int activeCount, int threshold) {
        return threshold > 0 && activeCount > 0 && activeCount % threshold == 0;
    }

    private int thresholdProgress(int activeCount, int threshold) {
        if (threshold <= 0) {
            return 0;
        }
        int progress = activeCount % threshold;
        return progress == 0 && activeCount > 0 ? threshold : progress;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }
}
