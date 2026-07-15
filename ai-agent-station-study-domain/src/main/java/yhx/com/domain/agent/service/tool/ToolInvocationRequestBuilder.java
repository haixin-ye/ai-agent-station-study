package yhx.com.domain.agent.service.tool;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IToolRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionDecisionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolApprovalDecisionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.ToolActionCommandVO;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;
import yhx.com.domain.agent.model.valobj.tool.PermissionCheckCommandVO;
import yhx.com.domain.agent.model.valobj.tool.PermissionDecisionVO;
import yhx.com.domain.agent.model.valobj.tool.ToolApprovalDecisionCommandVO;
import yhx.com.domain.agent.model.valobj.tool.ToolApprovalDecisionResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolApprovalKeyCommandVO;
import yhx.com.domain.agent.model.valobj.tool.ToolArgumentsMaterializationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolIntentVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationBuildResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationRequestVO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ToolInvocationRequestBuilder {

    public static final String READY = "READY";
    public static final String WAITING_USER = "WAITING_USER";
    public static final String DENIED = "DENIED";
    public static final String FAILED = "FAILED";

    private final CapabilityRegistry capabilityRegistry;
    private final McpToolRegistry mcpToolRegistry;
    private final ToolArgumentMaterializer argumentMaterializer;
    private final PermissionEnforcer permissionEnforcer;
    private final ToolApprovalService approvalService;
    private final ToolApprovalKeyGenerator approvalKeyGenerator;
    private final IToolRepository toolRepository;
    private final IPayloadRepository payloadRepository;

    public ToolInvocationRequestBuilder(CapabilityRegistry capabilityRegistry,
                                        McpToolRegistry mcpToolRegistry,
                                        ToolArgumentMaterializer argumentMaterializer,
                                        PermissionEnforcer permissionEnforcer,
                                        ToolApprovalService approvalService,
                                        ToolApprovalKeyGenerator approvalKeyGenerator,
                                        IToolRepository toolRepository,
                                        IPayloadRepository payloadRepository) {
        this.capabilityRegistry = capabilityRegistry;
        this.mcpToolRegistry = mcpToolRegistry;
        this.argumentMaterializer = argumentMaterializer;
        this.permissionEnforcer = permissionEnforcer;
        this.approvalService = approvalService;
        this.approvalKeyGenerator = approvalKeyGenerator;
        this.toolRepository = toolRepository;
        this.payloadRepository = payloadRepository;
    }

    public ToolInvocationBuildResultVO build(ToolActionCommandVO command) {
        ToolIntentVO intent = toIntent(command);
        if (command == null || isBlank(command.getRunId())) {
            return failed(null, "TOOL_INVALID_INTENT", "CALL_TOOL requires runId.");
        }
        CapabilitySpecVO capability = resolveCapability(intent);
        if (capability != null && isBlank(intent.getCapabilityCode())) {
            intent.setCapabilityCode(capability.getCapabilityCode());
        }
        if (isBlank(intent.getCapabilityCode()) && capability == null) {
            return failed(null, "TOOL_INVALID_INTENT", "CALL_TOOL requires capabilityCode or a unique toolName.");
        }
        String toolCallId = "tool-call-" + UUID.randomUUID();
        String toolInvocationId = "tool-invocation-" + UUID.randomUUID();
        String intentRef = savePayload(PayloadTypeEnumVO.JSON, intent, "tool-intent");
        ToolCallEntity toolCall = createToolCall(command, intent, capability, toolCallId, toolInvocationId, intentRef);
        toolRepository.createToolCall(toolCall);
        if (capability == null) {
            toolRepository.updateToolCallStatus(toolCallId, ToolCallStatusEnumVO.PERMISSION_DENIED);
            return denied(toolCallId, "TOOL_CAPABILITY_DISABLED", "Capability is missing or disabled.");
        }
        String mcpServerCode = firstNonBlank(intent.getMcpServerCode(), capability.getMcpServerCode());
        String toolName = firstNonBlank(capability.getToolName(), intent.getToolName(), command.getToolName());
        McpToolSpecVO toolSpec = mcpToolRegistry.findTool(mcpServerCode, toolName).orElse(null);
        if (toolSpec == null) {
            toolRepository.updateToolCallStatus(toolCallId, ToolCallStatusEnumVO.PERMISSION_DENIED);
            return denied(toolCallId, "TOOL_NOT_FOUND", "MCP tool metadata is missing.");
        }
        ToolArgumentsMaterializationResultVO arguments = argumentMaterializer.materialize(intent, capability);
        if (arguments.getFailureCode() != null) {
            toolRepository.saveToolReceipt(toolCallId, arguments.getArgumentsRef(), null, ToolCallStatusEnumVO.FAILED, arguments.getFailureCode());
            return failed(toolCallId, arguments.getFailureCode(), arguments.getFailureMessage());
        }
        String argumentsHash = approvalKeyGenerator.argumentsHash(arguments.getArguments());
        String approvalKey = approvalKeyGenerator.approvalKey(ToolApprovalKeyCommandVO.builder()
                .runId(command.getRunId())
                .toolCallId(toolCallId)
                .capabilityCode(capability.getCapabilityCode())
                .mcpServerCode(mcpServerCode)
                .toolName(toolName)
                .argumentsHash(argumentsHash)
                .requiredPermission(capability.getRequiredPermission())
                .workspaceScope(capability.getWorkspaceScope())
                .destructive(Boolean.TRUE.equals(capability.getDestructive()) || Boolean.TRUE.equals(toolSpec.getDestructive()))
                .build());
        ToolApprovalEntity existingApproval = toolRepository.findApprovalByApprovalKey(approvalKey).orElse(null);
        PermissionDecisionVO permissionDecision = permissionEnforcer.decide(PermissionCheckCommandVO.builder()
                .runId(command.getRunId())
                .toolCallId(toolCallId)
                .capability(capability)
                .toolSpec(toolSpec)
                .materializedArguments(arguments.getArguments())
                .argumentsHash(argumentsHash)
                .workspaceScope(capability.getWorkspaceScope())
                .destructive(Boolean.TRUE.equals(capability.getDestructive()) || Boolean.TRUE.equals(toolSpec.getDestructive()))
                .existingApproval(existingApproval)
                .build());
        ToolApprovalDecisionResultVO approvalDecision = approvalService.ensureApproval(ToolApprovalDecisionCommandVO.builder()
                .runId(command.getRunId())
                .sessionId(command.getSessionId())
                .toolCallId(toolCallId)
                .approvalKey(approvalKey)
                .argumentsHash(argumentsHash)
                .capability(capability)
                .toolSpec(toolSpec)
                .permissionDecision(permissionDecision)
                .toolIntent(intent)
                .runtimeContext(command.getRuntimeContext())
                .build());
        if (approvalDecision.getStatus() == ToolApprovalDecisionStatusEnumVO.PENDING) {
            toolRepository.updateToolCallStatus(toolCallId, ToolCallStatusEnumVO.APPROVAL_PENDING);
            return ToolInvocationBuildResultVO.builder()
                    .status(WAITING_USER)
                    .toolCallId(toolCallId)
                    .pendingInputId(approvalDecision.getPendingInputId())
                    .askUserRequest(approvalDecision.getAskUserRequest())
                    .failureCode("TOOL_APPROVAL_REQUIRED")
                    .failureMessage(approvalDecision.getMessage())
                    .build();
        }
        if (permissionDecision.getStatus() == PermissionDecisionStatusEnumVO.DENY
                || approvalDecision.getStatus() == ToolApprovalDecisionStatusEnumVO.DENIED
                || approvalDecision.getStatus() == ToolApprovalDecisionStatusEnumVO.REJECTED
                || approvalDecision.getStatus() == ToolApprovalDecisionStatusEnumVO.CANCELLED) {
            toolRepository.updateToolCallStatus(toolCallId, ToolCallStatusEnumVO.PERMISSION_DENIED);
            return denied(toolCallId, firstNonBlank(approvalDecision.getFailureCode(), permissionDecision.getFailureCode(), "TOOL_PERMISSION_DENIED"),
                    firstNonBlank(approvalDecision.getMessage(), permissionDecision.getReason(), "Tool permission denied."));
        }
        ToolInvocationRequestVO request = ToolInvocationRequestVO.builder()
                .runId(command.getRunId())
                .sessionId(command.getSessionId())
                .loopIndex(command.getLoopIndex())
                .toolCallId(toolCallId)
                .toolInvocationId(toolInvocationId)
                .toolIntent(intent)
                .capabilitySpec(capability)
                .mcpTool(toolSpec)
                .arguments(arguments.getArguments())
                .argumentsRef(arguments.getArgumentsRef())
                .argumentsHash(argumentsHash)
                .approvalId(approvalDecision.getApproval() == null ? null : approvalDecision.getApproval().getApprovalId())
                .approvalRequired(approvalDecision.getApproval() != null)
                .mustCallRealTool(true)
                .timeoutMs(capability.getTimeoutMs())
                .build();
        return ToolInvocationBuildResultVO.builder()
                .status(READY)
                .toolCallId(toolCallId)
                .request(request)
                .build();
    }

    private CapabilitySpecVO resolveCapability(ToolIntentVO intent) {
        if (intent == null || capabilityRegistry == null) {
            return null;
        }
        if (!isBlank(intent.getCapabilityCode())) {
            CapabilitySpecVO direct = capabilityRegistry.findCapability(intent.getCapabilityCode()).orElse(null);
            if (direct != null) {
                return direct;
            }
            return capabilityRegistry.findCapabilityByAbstractGrant(intent.getCapabilityCode(), intent.getMcpServerCode(), intent.getToolName()).orElse(null);
        }
        return capabilityRegistry.findUniqueCapabilityByTool(intent.getMcpServerCode(), intent.getToolName()).orElse(null);
    }

    private ToolCallEntity createToolCall(ToolActionCommandVO command, ToolIntentVO intent, CapabilitySpecVO capability,
                                          String toolCallId, String toolInvocationId, String intentRef) {
        String mcpServerCode = capability == null ? intent.getMcpServerCode() : firstNonBlank(intent.getMcpServerCode(), capability.getMcpServerCode());
        String toolName = capability == null
                ? firstNonBlank(intent.getToolName(), command.getToolName())
                : firstNonBlank(capability.getToolName(), intent.getToolName(), command.getToolName());
        return ToolCallEntity.builder()
                .toolCallId(toolCallId)
                .toolInvocationId(toolInvocationId)
                .runId(command.getRunId())
                .toolName(toolName)
                .mcpServerName(mcpServerCode)
                .status(ToolCallStatusEnumVO.CREATED)
                .intentRef(intentRef)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @SuppressWarnings("unchecked")
    private ToolIntentVO toIntent(ToolActionCommandVO command) {
        if (command == null) {
            return ToolIntentVO.builder().build();
        }
        Map<String, Object> raw = command.getRawToolIntent() == null ? Map.of() : command.getRawToolIntent();
        Map<String, Object> arguments = command.getArguments();
        if (arguments == null && raw.get("arguments") instanceof Map<?, ?> rawArguments) {
            arguments = (Map<String, Object>) rawArguments;
        }
        return ToolIntentVO.builder()
                .goal(firstNonBlank(command.getGoal(), string(raw.get("goal"))))
                .capabilityCode(firstNonBlank(command.getCapabilityCode(), string(raw.get("capabilityCode"))))
                .mcpServerCode(string(raw.get("mcpServerCode")))
                .toolName(firstNonBlank(command.getToolName(), string(raw.get("toolName"))))
                .arguments(arguments == null ? new LinkedHashMap<>() : arguments)
                .expectedOutcome(raw.get("expectedOutcome") instanceof Map<?, ?> expected ? (Map<String, Object>) expected : null)
                .build();
    }

    private String savePayload(PayloadTypeEnumVO payloadType, Object value, String preview) {
        if (payloadRepository == null || value == null) {
            return null;
        }
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(payloadType)
                .content(JSON.toJSONString(value))
                .preview(preview)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private ToolInvocationBuildResultVO denied(String toolCallId, String failureCode, String failureMessage) {
        return ToolInvocationBuildResultVO.builder()
                .status(DENIED)
                .toolCallId(toolCallId)
                .failureCode(failureCode)
                .failureMessage(failureMessage)
                .build();
    }

    private ToolInvocationBuildResultVO failed(String toolCallId, String failureCode, String failureMessage) {
        return ToolInvocationBuildResultVO.builder()
                .status(FAILED)
                .toolCallId(toolCallId)
                .failureCode(failureCode)
                .failureMessage(failureMessage)
                .build();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
