package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionExecutionContextVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionHandlerResultVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentCapabilityCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.SubAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.ToolActionCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.ToolActionResultVO;
import yhx.com.domain.agent.service.runtime.port.ToolActionOrchestratorPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SubAgentCallToolActionHandler implements SubAgentActionHandler {

    private final ParentChildRunRegistry registry;
    private final ToolActionOrchestratorPort toolActionOrchestratorPort;

    public SubAgentCallToolActionHandler(ParentChildRunRegistry registry,
                                         ToolActionOrchestratorPort toolActionOrchestratorPort) {
        this.registry = registry == null ? new ParentChildRunRegistry() : registry;
        this.toolActionOrchestratorPort = toolActionOrchestratorPort;
    }

    @Override
    public String actionType() {
        return SubAgentActionTypeEnumVO.CALL_TOOL.code();
    }

    @Override
    public SubAgentActionHandlerResultVO handle(SubAgentActionExecutionContextVO context, SubAgentActionVO action) {
        ParentChildRunRelationVO relation = context == null ? null : context.getRelation();
        if (toolActionOrchestratorPort == null) {
            return failed(relation, "Tool execution is unavailable for generic subagent.");
        }
        Map<String, Object> intent = action == null || action.getActionInput() == null ? Map.of() : action.getActionInput();
        String capabilityCode = stringValue(intent, "capabilityCode");
        String toolName = stringValue(intent, "toolName");
        String goal = stringValue(intent, "goal");
        if (isBlank(toolName) && isBlank(goal)) {
            return failed(relation, "Generic subagent CALL_TOOL requires actionInput.toolName or actionInput.goal.");
        }
        String permissionFailure = validateToolCapability(context, capabilityCode);
        if (permissionFailure != null) {
            return failed(relation, permissionFailure);
        }
        ToolActionResultVO result = toolActionOrchestratorPort.handleToolAction(ToolActionCommandVO.builder()
                .runId(relation == null ? null : relation.getChildRunId())
                .sessionId(context == null || context.getCommand() == null ? null : context.getCommand().getSessionId())
                .userId(context == null || context.getCommand() == null ? null : context.getCommand().getUserId())
                .loopIndex(context == null ? null : context.getLoopIndex())
                .capabilityCode(capabilityCode)
                .toolName(toolName)
                .goal(goal)
                .arguments(arguments(intent))
                .rawToolIntent(intent)
                .build());
        if (result == null || result.getStatus() == null) {
            return failed(relation, "Tool execution failed for generic subagent.");
        }
        if (result.getStatus() == ToolActionStatusEnumVO.WAITING_USER) {
            return waitingUser(relation, result);
        }
        if (result.getStatus() == ToolActionStatusEnumVO.CONTINUE_LOOP) {
            return SubAgentActionHandlerResultVO.builder()
                    .action(actionType())
                    .terminal(false)
                    .message(result.getMessage())
                    .resultSnapshot(resultSnapshot(result))
                    .build();
        }
        return failed(relation, result.getMessage() == null ? "Tool execution failed for generic subagent." : result.getMessage());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> arguments(Map<String, Object> intent) {
        Object value = intent.get("arguments");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private String validateToolCapability(SubAgentActionExecutionContextVO context, String capabilityCode) {
        if (context == null || context.getCommand() == null || context.getCommand().getEffectiveCapabilityCodes() == null) {
            return "Generic subagent CALL_TOOL requires effective tool capabilities.";
        }
        java.util.Set<String> effectiveCapabilities = context.getCommand().getEffectiveCapabilityCodes();
        if (!isBlank(capabilityCode) && !effectiveCapabilities.contains(capabilityCode)) {
            return "Generic subagent CALL_TOOL capabilityCode is not granted: " + capabilityCode + ".";
        }
        if (isBlank(capabilityCode) && !effectiveCapabilities.contains(AgentCapabilityCodeEnumVO.MCP_TOOL.code())) {
            return "Generic subagent CALL_TOOL requires capabilityCode or MCP_TOOL capability.";
        }
        return null;
    }

    private SubAgentActionHandlerResultVO waitingUser(ParentChildRunRelationVO relation, ToolActionResultVO result) {
        if (relation != null) {
            registry.markWaitingUser(relation.getChildRunId(), result.getPendingInputId());
        }
        return SubAgentActionHandlerResultVO.builder()
                .action(actionType())
                .terminal(true)
                .status(ChildAgentRunStatusEnumVO.WAITING_USER)
                .pendingInputId(result.getPendingInputId())
                .askUserRequest(result.getAskUserRequest())
                .message(result.getMessage())
                .resultSnapshot(resultSnapshot(result))
                .build();
    }

    private SubAgentActionHandlerResultVO failed(ParentChildRunRelationVO relation, String failureMessage) {
        if (relation != null) {
            registry.markFailed(relation.getChildRunId(), failureMessage);
        }
        return SubAgentActionHandlerResultVO.builder()
                .action(actionType())
                .terminal(true)
                .status(ChildAgentRunStatusEnumVO.FAILED)
                .failureMessage(failureMessage)
                .message(failureMessage)
                .resultSnapshot(Map.of(
                        "action", actionType(),
                        "status", ChildAgentRunStatusEnumVO.FAILED.code(),
                        "failureMessage", failureMessage))
                .build();
    }

    private Map<String, Object> resultSnapshot(ToolActionResultVO result) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", result.getStatus() == null ? null : result.getStatus().code());
        snapshot.put("actionEffectStatus", result.getActionEffectStatus() == null ? null : result.getActionEffectStatus().name());
        snapshot.put("message", result.getMessage());
        snapshot.put("pendingInputId", result.getPendingInputId());
        snapshot.put("evidenceIds", result.getEvidenceIds() == null ? List.of() : result.getEvidenceIds());
        snapshot.put("evidence", result.getEvidence() == null ? List.of() : result.getEvidence().stream()
                .map(this::visibleEvidenceSnapshot)
                .toList());
        return snapshot;
    }

    private Map<String, Object> visibleEvidenceSnapshot(MaterializedEvidenceVO evidence) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (evidence == null) {
            return snapshot;
        }
        snapshot.put("evidenceId", evidence.getEvidenceId());
        snapshot.put("summary", bounded(evidence.getSummary(), 600));
        snapshot.put("boundedSnippet", bounded(evidence.getBoundedSnippet(), 1200));
        snapshot.put("sourceRef", evidence.getSourceRef());
        snapshot.put("contentRef", evidence.getContentRef());
        snapshot.put("totalChars", evidence.getTotalChars());
        snapshot.put("truncated", evidence.getTruncated());
        return snapshot;
    }

    private String bounded(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String stringValue(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
