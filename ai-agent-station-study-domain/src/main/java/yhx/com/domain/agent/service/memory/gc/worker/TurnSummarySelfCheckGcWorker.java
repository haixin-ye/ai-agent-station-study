package yhx.com.domain.agent.service.memory.gc.worker;

import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.service.memory.gc.MemoryGcFollowupScheduler;
import yhx.com.domain.agent.service.observability.AutoAgentHumanLog;

import java.util.List;

public class TurnSummarySelfCheckGcWorker implements MemoryGcTaskWorker {

    private static final int DEFAULT_SCAN_LIMIT = 50;
    private static final int MAX_FAILURE_MESSAGE_CHARS = 5000;

    private final ITurnRepository turnRepository;
    private final ITurnSummaryRepository summaryRepository;
    private final IMemoryTaskRepository taskRepository;
    private final MemoryGcFollowupScheduler followupScheduler;
    private final int scanLimit;

    public TurnSummarySelfCheckGcWorker(ITurnRepository turnRepository,
                                        ITurnSummaryRepository summaryRepository,
                                        IMemoryTaskRepository taskRepository,
                                        MemoryGcFollowupScheduler followupScheduler) {
        this(turnRepository, summaryRepository, taskRepository, followupScheduler, DEFAULT_SCAN_LIMIT);
    }

    public TurnSummarySelfCheckGcWorker(ITurnRepository turnRepository,
                                        ITurnSummaryRepository summaryRepository,
                                        IMemoryTaskRepository taskRepository,
                                        MemoryGcFollowupScheduler followupScheduler,
                                        int scanLimit) {
        this.turnRepository = turnRepository;
        this.summaryRepository = summaryRepository;
        this.taskRepository = taskRepository;
        this.followupScheduler = followupScheduler;
        this.scanLimit = scanLimit <= 0 ? DEFAULT_SCAN_LIMIT : scanLimit;
    }

    @Override
    public String taskType() {
        return MemoryTaskTypeEnumVO.TURN_SUMMARY_SELF_CHECK.name();
    }

    @Override
    public void handle(String taskId) {
        try {
            taskRepository.markRunning(taskId);
            AgentMemoryTaskEntity task = taskRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("Memory task not found: " + taskId));
            int dispatched = selfCheck(task);
            taskRepository.markSucceeded(taskId, "dispatched=" + dispatched);
        } catch (Exception e) {
            taskRepository.markFailed(taskId, "TURN_SUMMARY_SELF_CHECK_FAILED", truncate(e.getMessage()));
        }
    }

    private int selfCheck(AgentMemoryTaskEntity task) {
        List<AgentTurnEntity> turns = turnRepository.listRecentCompletedTurns(scanLimit);
        if (turns == null || turns.isEmpty()) {
            AutoAgentHumanLog.stage("摘要存储自检", task.getRunId(), "自检完成：近" + scanLimit + "轮没有已完成 turn。");
            return 0;
        }
        int missing = 0;
        int alreadyQueued = 0;
        int dispatched = 0;
        for (AgentTurnEntity turn : turns) {
            if (turn == null || isBlank(turn.getTurnId())) {
                continue;
            }
            if (summaryRepository.findSummaryByTurnId(turn.getTurnId()).isPresent()) {
                continue;
            }
            missing++;
            boolean alreadyHasOpenTask = taskRepository.hasOpenTaskForTurn(MemoryTaskTypeEnumVO.TURN_SUMMARY.name(), turn.getTurnId());
            if (alreadyHasOpenTask) {
                alreadyQueued++;
                continue;
            }
            String newTaskId = followupScheduler.createAndDispatch(MemoryTaskTypeEnumVO.TURN_SUMMARY.name(),
                    turn.getTurnId(),
                    turn.getRunId(),
                    turn.getSessionId(),
                    null);
            if (newTaskId != null) {
                dispatched++;
            }
        }
        AutoAgentHumanLog.stage("摘要存储自检", task.getRunId(), "自检完成：检查近" + scanLimit
                + "轮，缺失摘要=" + missing
                + "，已有补偿任务=" + alreadyQueued
                + "，已创建补偿任务=" + dispatched + "。");
        return dispatched;
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
}
