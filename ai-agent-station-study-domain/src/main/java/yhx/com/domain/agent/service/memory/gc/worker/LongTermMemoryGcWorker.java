package yhx.com.domain.agent.service.memory.gc.worker;

import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.ExtractedMemoryVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryExtractionInputVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryExtractionOutputVO;
import yhx.com.domain.agent.service.memory.MemoryManager;
import yhx.com.domain.agent.service.node.memoryextraction.MemoryExtractionNodeService;

import java.math.BigDecimal;
import java.util.List;

public class LongTermMemoryGcWorker implements MemoryGcTaskWorker {

    private static final int MAX_FAILURE_MESSAGE_CHARS = 4000;

    private final ITurnRepository turnRepository;
    private final IMemoryTaskRepository taskRepository;
    private final IPayloadRepository payloadRepository;
    private final MemoryManager memoryManager;
    private final MemoryExtractionNodeService nodeService;

    public LongTermMemoryGcWorker(ITurnRepository turnRepository,
                                  IMemoryTaskRepository taskRepository,
                                  IPayloadRepository payloadRepository,
                                  MemoryManager memoryManager,
                                  MemoryExtractionNodeService nodeService) {
        this.turnRepository = turnRepository;
        this.taskRepository = taskRepository;
        this.payloadRepository = payloadRepository;
        this.memoryManager = memoryManager;
        this.nodeService = nodeService;
    }

    @Override
    public String taskType() {
        return MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name();
    }

    @Override
    public void handle(String taskId) {
        try {
            taskRepository.markRunning(taskId);
            AgentMemoryTaskEntity task = taskRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("Memory task not found: " + taskId));
            AgentTurnEntity turn = turnRepository.findByTurnId(task.getTurnId())
                    .orElseThrow(() -> new IllegalArgumentException("Turn not found: " + task.getTurnId()));
            MemoryExtractionOutputVO output = nodeService.extract(MemoryExtractionInputVO.builder()
                    .runId(turn.getRunId())
                    .sessionId(turn.getSessionId())
                    .turnId(turn.getTurnId())
                    .userInput(loadPayloadContent(turn.getUserPayloadRef()))
                    .finalAnswer(loadPayloadContent(turn.getAssistantPayloadRef()))
                    .turnSummary(loadPayloadContent(task.getInputRef()))
                    .build(), turn.getAgentId(), null);
            saveMemories(turn, output == null ? List.of() : output.getMemories());
            taskRepository.markSucceeded(taskId, null);
        } catch (Exception e) {
            taskRepository.markFailed(taskId, "LONG_TERM_MEMORY_EXTRACTION_FAILED", truncate(e.getMessage()));
        }
    }

    private void saveMemories(AgentTurnEntity turn, List<ExtractedMemoryVO> memories) {
        if (memories == null || memoryManager == null) {
            return;
        }
        for (ExtractedMemoryVO item : memories) {
            if (item == null || isBlank(item.getSummary())) {
                continue;
            }
            String memoryType = normalizeMemoryType(item.getMemoryType());
            memoryManager.saveLongTermMemory(AgentMemoryEntity.builder()
                    .userId(turn.getUserId())
                    .sessionId(turn.getSessionId())
                    .memoryType(memoryType)
                    .summary(item.getSummary())
                    .score(item.getScore() == null ? new BigDecimal("0.50") : item.getScore())
                    .build());
        }
    }

    private String normalizeMemoryType(String memoryType) {
        return "USER_PREFERENCE".equalsIgnoreCase(memoryType) ? "USER_PREFERENCE" : "LONG_TERM_MEMORY";
    }

    private String loadPayloadContent(String payloadRef) {
        if (payloadRepository == null || payloadRef == null || payloadRef.isBlank()) {
            return null;
        }
        return payloadRepository.findPayload(payloadRef)
                .map(payload -> firstNonBlank(payload.getContent(), payload.getPreview()))
                .orElse(null);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_FAILURE_MESSAGE_CHARS ? message : message.substring(0, MAX_FAILURE_MESSAGE_CHARS);
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first;
        }
        return isBlank(second) ? null : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
