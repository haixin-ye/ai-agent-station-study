package yhx.com.domain.agent.service.memory.gc.worker;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IVectorIndexRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceActionVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceInputVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceItemVO;
import yhx.com.domain.agent.model.valobj.memory.MemoryGovernanceOutputVO;
import yhx.com.domain.agent.service.node.memorygovernance.MemoryGovernanceNodeService;
import yhx.com.domain.agent.service.observability.AutoAgentHumanLog;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MemoryGovernanceGcWorker implements MemoryGcTaskWorker {

    private static final int MAX_FAILURE_MESSAGE_CHARS = 4000;

    private final IMemoryRepository memoryRepository;
    private final IMemoryTaskRepository taskRepository;
    private final IPayloadRepository payloadRepository;
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
        this(memoryRepository, taskRepository, null, vectorMemoryRepository, vectorIndexRepository, nodeService, memoryLimit);
    }

    public MemoryGovernanceGcWorker(IMemoryRepository memoryRepository,
                                    IMemoryTaskRepository taskRepository,
                                    IPayloadRepository payloadRepository,
                                    IVectorMemoryRepository vectorMemoryRepository,
                                    IVectorIndexRepository vectorIndexRepository,
                                    MemoryGovernanceNodeService nodeService,
                                    int memoryLimit) {
        this.memoryRepository = memoryRepository;
        this.taskRepository = taskRepository;
        this.payloadRepository = payloadRepository;
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
            AutoAgentHumanLog.stage("长期记忆治理", task.getRunId(), "开始执行长期记忆治理：taskId=" + taskId
                    + "，触发session=" + task.getSessionId());
            List<AgentMemoryEntity> memories = memoryRepository.listActiveMemoriesForGovernance(memoryLimit);
            if (memories == null || memories.isEmpty()) {
                AutoAgentHumanLog.stage("长期记忆治理", task.getRunId(), "长期记忆治理跳过：没有可治理的ACTIVE长期记忆。");
                taskRepository.markSucceeded(taskId, null);
                return;
            }
            AutoAgentHumanLog.stage("长期记忆治理", task.getRunId(), "长期记忆治理读取到候选记忆："
                    + memories.size() + "条，limit=" + memoryLimit);
            Map<String, AgentMemoryEntity> byId = new LinkedHashMap<>();
            memories.stream()
                    .filter(memory -> memory != null && memory.getMemoryId() != null && !memory.getMemoryId().isBlank())
                    .forEach(memory -> byId.put(memory.getMemoryId(), memory));
            MemoryGovernanceOutputVO output = nodeService.govern(MemoryGovernanceInputVO.builder()
                    .runId(task.getRunId())
                    .sessionId(task.getSessionId())
                    .memories(memories.stream().map(this::toItem).toList())
                    .build(), null, null);
            AutoAgentHumanLog.stage("长期记忆治理", task.getRunId(), "长期记忆治理LLM返回动作："
                    + size(output == null ? null : output.getActions()) + "个。");
            applyActions(task, byId, output == null ? List.of() : output.getActions());
            taskRepository.markSucceeded(taskId, null);
            AutoAgentHumanLog.stage("长期记忆治理", task.getRunId(), "长期记忆治理完成：taskId=" + taskId);
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
                AutoAgentHumanLog.stage("长期记忆治理", task.getRunId(), "禁用长期记忆：memoryId="
                        + action.getMemoryId() + "，原因=" + preview(action.getReason()));
                updateLifecycle(task, byId.get(action.getMemoryId()), "DISABLED", null, action);
            } else if ("SUPERSEDE".equals(normalized)
                    && !isBlank(action.getTargetMemoryId())
                    && byId.containsKey(action.getTargetMemoryId())) {
                AutoAgentHumanLog.stage("长期记忆治理", task.getRunId(), "替换长期记忆：memoryId="
                        + action.getMemoryId() + "，supersededBy=" + action.getTargetMemoryId()
                        + "，原因=" + preview(action.getReason()));
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
                .payloadRef(saveActionPayload(action))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String saveActionPayload(MemoryGovernanceActionVO action) {
        if (payloadRepository == null || action == null) {
            return null;
        }
        String content = JSON.toJSONString(action);
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(content)
                .preview(preview(content))
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
                .content(loadPayloadContent(memory.getContentRef()))
                .score(memory.getScore())
                .status(memory.getStatus())
                .sourceRunId(memory.getSourceRunId())
                .sourceTurnId(memory.getSourceTurnId())
                .lastSeenAt(memory.getLastSeenAt())
                .createdAt(memory.getCreatedAt())
                .updatedAt(memory.getUpdatedAt())
                .build();
    }

    private String loadPayloadContent(String payloadRef) {
        if (payloadRepository == null || isBlank(payloadRef)) {
            return null;
        }
        return payloadRepository.findPayload(payloadRef)
                .map(payload -> firstNonBlank(payload.getContent(), payload.getPreview()))
                .orElse(null);
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

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_FAILURE_MESSAGE_CHARS ? message : message.substring(0, MAX_FAILURE_MESSAGE_CHARS);
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= 200 ? content : content.substring(0, 200);
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
