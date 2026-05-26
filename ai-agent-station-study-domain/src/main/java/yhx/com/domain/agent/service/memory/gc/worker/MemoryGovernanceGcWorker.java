package yhx.com.domain.agent.service.memory.gc.worker;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IVectorIndexRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceActionVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceInputVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceItemVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceOutputVO;
import yhx.com.domain.agent.service.node.memorygovernance.MemoryGovernanceNodeService;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MemoryGovernanceGcWorker implements MemoryGcTaskWorker {

    private static final int MAX_FAILURE_MESSAGE_CHARS = 4000;

    private final IMemoryRepository memoryRepository;
    private final IMemoryTaskRepository taskRepository;
    private final IVectorMemoryRepository vectorMemoryRepository;
    private final IVectorIndexRepository vectorIndexRepository;
    private final MemoryGovernanceNodeService nodeService;
    private final int memoryLimit;

    public MemoryGovernanceGcWorker(IMemoryRepository memoryRepository,
                                    IMemoryTaskRepository taskRepository,
                                    IVectorMemoryRepository vectorMemoryRepository,
                                    IVectorIndexRepository vectorIndexRepository,
                                    MemoryGovernanceNodeService nodeService,
                                    int memoryLimit) {
        this.memoryRepository = memoryRepository;
        this.taskRepository = taskRepository;
        this.vectorMemoryRepository = vectorMemoryRepository;
        this.vectorIndexRepository = vectorIndexRepository;
        this.nodeService = nodeService;
        this.memoryLimit = memoryLimit <= 0 ? 50 : memoryLimit;
    }

    @Override
    public String taskType() {
        return MemoryTaskTypeEnumVO.MEMORY_GOVERNANCE.name();
    }

    @Override
    public void handle(String taskId) {
        try {
            taskRepository.markRunning(taskId);
            AgentMemoryTaskEntity task = taskRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("Memory task not found: " + taskId));
            List<AgentMemoryEntity> memories = memoryRepository.listActiveMemoriesBySession(task.getSessionId(), memoryLimit);
            if (memories == null || memories.isEmpty()) {
                taskRepository.markSucceeded(taskId, null);
                return;
            }
            Map<String, AgentMemoryEntity> byId = new LinkedHashMap<>();
            memories.stream()
                    .filter(memory -> memory != null && memory.getMemoryId() != null && !memory.getMemoryId().isBlank())
                    .forEach(memory -> byId.put(memory.getMemoryId(), memory));
            MemoryGovernanceOutputVO output = nodeService.govern(MemoryGovernanceInputVO.builder()
                    .runId(task.getRunId())
                    .sessionId(task.getSessionId())
                    .memories(memories.stream().map(this::toItem).toList())
                    .build(), null, null);
            applyActions(task, byId, output == null ? List.of() : output.getActions());
            taskRepository.markSucceeded(taskId, null);
        } catch (Exception e) {
            taskRepository.markFailed(taskId, "MEMORY_GOVERNANCE_FAILED", truncate(e.getMessage()));
        }
    }

    private void applyActions(AgentMemoryTaskEntity task, Map<String, AgentMemoryEntity> byId, List<MemoryGovernanceActionVO> actions) {
        if (actions == null) {
            return;
        }
        for (MemoryGovernanceActionVO action : actions) {
            if (action == null || isBlank(action.getMemoryId()) || !byId.containsKey(action.getMemoryId())) {
                continue;
            }
            String normalized = normalizeAction(action.getAction());
            if ("DISABLE".equals(normalized)) {
                updateLifecycle(task, byId.get(action.getMemoryId()), "DISABLED", null, action);
            } else if ("SUPERSEDE".equals(normalized)
                    && !isBlank(action.getTargetMemoryId())
                    && byId.containsKey(action.getTargetMemoryId())) {
                updateLifecycle(task, byId.get(action.getMemoryId()), "SUPERSEDED", action.getTargetMemoryId(), action);
            }
        }
    }

    private void updateLifecycle(AgentMemoryTaskEntity task,
                                 AgentMemoryEntity memory,
                                 String status,
                                 String supersededBy,
                                 MemoryGovernanceActionVO action) {
        memoryRepository.updateMemoryLifecycle(memory.getMemoryId(), status, supersededBy);
        disableVector(memory);
        memoryRepository.recordMemoryEvent(AgentMemoryEventEntity.builder()
                .runId(task.getRunId())
                .sessionId(task.getSessionId())
                .memoryId(memory.getMemoryId())
                .eventType("GOVERNANCE_" + status)
                .payloadRef(JSON.toJSONString(action))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void disableVector(AgentMemoryEntity memory) {
        VectorCollectionTypeEnumVO collectionType = collectionType(memory.getMemoryType());
        VectorSourceTypeEnumVO sourceType = sourceType(memory.getMemoryType());
        if (vectorMemoryRepository != null) {
            vectorMemoryRepository.disable(collectionType, memory.getMemoryId());
        }
        if (vectorIndexRepository != null) {
            vectorIndexRepository.markDisabled(collectionType.name(), sourceType.name(), memory.getMemoryId());
        }
    }

    private MemoryGovernanceItemVO toItem(AgentMemoryEntity memory) {
        return MemoryGovernanceItemVO.builder()
                .memoryId(memory.getMemoryId())
                .memoryType(memory.getMemoryType())
                .summary(memory.getSummary())
                .score(memory.getScore())
                .status(memory.getStatus())
                .sourceRunId(memory.getSourceRunId())
                .sourceTurnId(memory.getSourceTurnId())
                .build();
    }

    private String normalizeAction(String action) {
        if (action == null) {
            return "KEEP";
        }
        return action.trim().toUpperCase();
    }

    private VectorCollectionTypeEnumVO collectionType(String memoryType) {
        return "USER_PREFERENCE".equalsIgnoreCase(memoryType)
                ? VectorCollectionTypeEnumVO.USER_PREFERENCE
                : VectorCollectionTypeEnumVO.LONG_TERM_MEMORY;
    }

    private VectorSourceTypeEnumVO sourceType(String memoryType) {
        return "USER_PREFERENCE".equalsIgnoreCase(memoryType)
                ? VectorSourceTypeEnumVO.USER_PREFERENCE
                : VectorSourceTypeEnumVO.LONG_TERM_MEMORY;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_FAILURE_MESSAGE_CHARS ? message : message.substring(0, MAX_FAILURE_MESSAGE_CHARS);
    }
}
