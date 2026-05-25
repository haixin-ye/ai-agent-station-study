package yhx.com.domain.agent.service.memory.gc.worker;

import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.ConversationRollupInputVO;
import yhx.com.domain.agent.model.valobj.memory.ConversationRollupItemVO;
import yhx.com.domain.agent.model.valobj.memory.ConversationRollupOutputVO;
import yhx.com.domain.agent.service.memory.MemoryManager;
import yhx.com.domain.agent.service.node.conversationrollup.ConversationRollupNodeService;

import java.time.LocalDateTime;
import java.util.List;

public class ConversationRollupGcWorker implements MemoryGcTaskWorker {

    private static final int MAX_FAILURE_MESSAGE_CHARS = 4000;

    private final ITurnSummaryRepository turnSummaryRepository;
    private final IMemoryTaskRepository taskRepository;
    private final IPayloadRepository payloadRepository;
    private final MemoryManager memoryManager;
    private final ConversationRollupNodeService nodeService;
    private final int summaryLimit;

    public ConversationRollupGcWorker(ITurnSummaryRepository turnSummaryRepository,
                                      IMemoryTaskRepository taskRepository,
                                      IPayloadRepository payloadRepository,
                                      MemoryManager memoryManager,
                                      ConversationRollupNodeService nodeService,
                                      int summaryLimit) {
        this.turnSummaryRepository = turnSummaryRepository;
        this.taskRepository = taskRepository;
        this.payloadRepository = payloadRepository;
        this.memoryManager = memoryManager;
        this.nodeService = nodeService;
        this.summaryLimit = summaryLimit <= 0 ? 12 : summaryLimit;
    }

    @Override
    public String taskType() {
        return MemoryTaskTypeEnumVO.CONVERSATION_ROLLUP.name();
    }

    @Override
    public void handle(String taskId) {
        try {
            taskRepository.markRunning(taskId);
            AgentMemoryTaskEntity task = taskRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("Memory task not found: " + taskId));
            List<AgentTurnSummaryEntity> summaries = turnSummaryRepository.listRecentActiveSummaries(task.getSessionId(), summaryLimit);
            if (summaries == null || summaries.isEmpty()) {
                throw new IllegalStateException("No active turn summaries for conversation rollup: " + task.getSessionId());
            }
            ConversationRollupOutputVO output = nodeService.summarize(ConversationRollupInputVO.builder()
                    .runId(task.getRunId())
                    .sessionId(task.getSessionId())
                    .userId(firstUserId(summaries))
                    .summaries(summaries.stream().map(this::toItem).toList())
                    .build(), null, null);
            if (output == null || output.getSummary() == null || output.getSummary().isBlank()) {
                throw new IllegalStateException("CONVERSATION_ROLLUP returned empty summary.");
            }
            String payloadRef = payloadRepository.savePayload(AgentPayloadEntity.builder()
                    .payloadType(PayloadTypeEnumVO.JSON)
                    .content(output.getSummary())
                    .preview(preview(output.getSummary()))
                    .createdAt(LocalDateTime.now())
                    .build());
            AgentConversationSummaryEntity summary = AgentConversationSummaryEntity.builder()
                    .sessionId(task.getSessionId())
                    .userId(firstUserId(summaries))
                    .summaryRef(payloadRef)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            memoryManager.saveConversationSummary(summary);
            taskRepository.markSucceeded(taskId, payloadRef);
        } catch (Exception e) {
            taskRepository.markFailed(taskId, "CONVERSATION_ROLLUP_FAILED", truncate(e.getMessage()));
        }
    }

    private ConversationRollupItemVO toItem(AgentTurnSummaryEntity summary) {
        return ConversationRollupItemVO.builder()
                .summaryId(summary.getSummaryId())
                .turnId(summary.getTurnId())
                .summary(loadPayloadContent(summary.getSummaryRef()))
                .intent(summary.getIntent())
                .build();
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
