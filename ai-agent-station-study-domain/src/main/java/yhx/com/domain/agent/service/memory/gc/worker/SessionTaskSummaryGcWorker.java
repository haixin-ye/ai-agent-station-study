package yhx.com.domain.agent.service.memory.gc.worker;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ISessionTaskSummaryRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentSessionTaskSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.SessionTaskSummaryInputVO;
import yhx.com.domain.agent.model.valobj.memory.SessionTaskSummaryItemVO;
import yhx.com.domain.agent.model.valobj.memory.SessionTaskSummaryOutputVO;
import yhx.com.domain.agent.service.node.sessiontasksummary.SessionTaskSummaryNodeService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class SessionTaskSummaryGcWorker implements MemoryGcTaskWorker {

    private static final int MAX_FAILURE_MESSAGE_CHARS = 4000;
    private static final int DEFAULT_MIN_SUMMARY_WINDOW = 30;

    private final ITurnSummaryRepository turnSummaryRepository;
    private final IMemoryTaskRepository taskRepository;
    private final IPayloadRepository payloadRepository;
    private final ISessionTaskSummaryRepository sessionTaskSummaryRepository;
    private final SessionTaskSummaryNodeService nodeService;
    private final int minSummaryWindow;

    public SessionTaskSummaryGcWorker(ITurnSummaryRepository turnSummaryRepository,
                                      IMemoryTaskRepository taskRepository,
                                      IPayloadRepository payloadRepository,
                                      ISessionTaskSummaryRepository sessionTaskSummaryRepository,
                                      SessionTaskSummaryNodeService nodeService,
                                      int minSummaryWindow) {
        this.turnSummaryRepository = turnSummaryRepository;
        this.taskRepository = taskRepository;
        this.payloadRepository = payloadRepository;
        this.sessionTaskSummaryRepository = sessionTaskSummaryRepository;
        this.nodeService = nodeService;
        this.minSummaryWindow = minSummaryWindow <= 0 ? DEFAULT_MIN_SUMMARY_WINDOW : minSummaryWindow;
    }

    @Override
    public String taskType() {
        return MemoryTaskTypeEnumVO.SESSION_TASK_SUMMARY.name();
    }

    @Override
    public void handle(String taskId) {
        try {
            taskRepository.markRunning(taskId);
            AgentMemoryTaskEntity task = taskRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("Memory task not found: " + taskId));
            int activeCount = turnSummaryRepository.countActiveSummaries(task.getSessionId());
            int summaryLimit = summaryLimit(activeCount);
            List<AgentTurnSummaryEntity> summaries = turnSummaryRepository.listRecentActiveSummaries(task.getSessionId(), summaryLimit);
            if (summaries == null || summaries.isEmpty()) {
                throw new IllegalStateException("No active turn summaries for session task summary: " + task.getSessionId());
            }
            AgentSessionTaskSummaryEntity previous = sessionTaskSummaryRepository.findActiveBySessionId(task.getSessionId()).orElse(null);
            SessionTaskSummaryOutputVO output = nodeService.summarize(SessionTaskSummaryInputVO.builder()
                    .runId(task.getRunId())
                    .sessionId(task.getSessionId())
                    .userId(firstUserId(summaries))
                    .previousTaskSummary(loadPayloadContent(previous == null ? null : previous.getSummaryRef()))
                    .summaries(summaries.stream().map(this::toItem).toList())
                    .build(), null, null);
            if (output == null) {
                throw new IllegalStateException("SESSION_TASK_SUMMARY returned empty output.");
            }
            if (!Boolean.TRUE.equals(output.getShouldUpdate())) {
                taskRepository.markSucceeded(taskId, previous == null ? null : previous.getSummaryRef());
                return;
            }
            if (isBlank(output.getCurrentTask()) && isEmpty(output.getMainTasks())) {
                throw new IllegalStateException("SESSION_TASK_SUMMARY returned no task state.");
            }
            String payloadRef = payloadRepository.savePayload(AgentPayloadEntity.builder()
                    .payloadType(PayloadTypeEnumVO.JSON)
                    .content(JSON.toJSONString(output))
                    .preview(preview(firstNonBlank(output.getCurrentTask(), output.getMainTasks() == null ? null : String.join("; ", output.getMainTasks()))))
                    .createdAt(LocalDateTime.now())
                    .build());
            AgentTurnSummaryEntity latest = summaries.get(summaries.size() - 1);
            sessionTaskSummaryRepository.markActiveSuperseded(task.getSessionId());
            sessionTaskSummaryRepository.saveSummary(AgentSessionTaskSummaryEntity.builder()
                    .summaryId("session-task-summary-" + UUID.randomUUID())
                    .sessionId(task.getSessionId())
                    .userId(firstUserId(summaries))
                    .summaryRef(payloadRef)
                    .versionNo(sessionTaskSummaryRepository.nextVersionNo(task.getSessionId()))
                    .sourceTurnCount(summaries.size())
                    .sourceLatestTurnId(latest.getTurnId())
                    .status("ACTIVE")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build());
            taskRepository.markSucceeded(taskId, payloadRef);
        } catch (Exception e) {
            taskRepository.markFailed(taskId, "SESSION_TASK_SUMMARY_FAILED", truncate(e.getMessage()));
        }
    }

    private SessionTaskSummaryItemVO toItem(AgentTurnSummaryEntity summary) {
        return SessionTaskSummaryItemVO.builder()
                .summaryId(summary.getSummaryId())
                .turnId(summary.getTurnId())
                .summary(loadPayloadContent(summary.getSummaryRef()))
                .intent(summary.getIntent())
                .build();
    }

    private int summaryLimit(int activeCount) {
        if (activeCount <= 0) {
            return minSummaryWindow;
        }
        int seventyPercent = (int) Math.ceil(activeCount * 0.7d);
        return Math.min(Math.max(minSummaryWindow, seventyPercent), activeCount);
    }

    private String loadPayloadContent(String payloadRef) {
        if (payloadRepository == null || payloadRef == null || payloadRef.isBlank()) {
            return null;
        }
        return payloadRepository.findPayload(payloadRef)
                .map(payload -> firstNonBlank(payload.getContent(), payload.getPreview()))
                .orElse(null);
    }

    private String firstUserId(List<AgentTurnSummaryEntity> summaries) {
        return summaries.stream()
                .map(AgentTurnSummaryEntity::getUserId)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
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
