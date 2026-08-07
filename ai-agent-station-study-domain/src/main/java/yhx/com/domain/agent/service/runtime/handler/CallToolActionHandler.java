package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.ActionEffectVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.ToolActionCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.ToolActionResultVO;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionHandler;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.port.ToolActionOrchestratorPort;

import java.util.Map;

public class CallToolActionHandler extends MainActionHandlerSupport implements MainActionHandler {

    private final ToolActionOrchestratorPort toolActionOrchestratorPort;

    public CallToolActionHandler(ToolActionOrchestratorPort toolActionOrchestratorPort,
                                 RuntimeFailureFactory failureFactory,
                                 DeveloperTraceRecorder traceRecorder) {
        super(failureFactory, traceRecorder);
        this.toolActionOrchestratorPort = toolActionOrchestratorPort;
    }

    @Override
    public MainAgentActionTypeEnumVO actionType() {
        return MainAgentActionTypeEnumVO.CALL_TOOL;
    }

    @Override
    public MainActionHandlerResult handle(RuntimeExecutionContext context, MainAgentActionVO action) {
        try {
            Map<String, Object> intent = requireToolIntent(action);
            String capabilityCode = stringValue(intent, "capabilityCode");
            String toolName = stringValue(intent, "toolName");
            String goal = stringValue(intent, "goal");
            if (isBlank(toolName) && isBlank(goal)) {
                throw new IllegalArgumentException("toolIntent.toolName or goal is required.");
            }
            if (toolActionOrchestratorPort == null) {
                return safeFailure(context, RuntimeFailureCodeEnumVO.ACTION_HANDLER_UNAVAILABLE,
                        "Tool execution is unavailable.", "ToolActionOrchestratorPort is not configured.");
            }
            ToolActionResultVO result = toolActionOrchestratorPort.handleToolAction(ToolActionCommandVO.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .userId(context.getUserId())
                    .loopIndex(context.getLoopIndex())
                    .capabilityCode(capabilityCode)
                    .toolName(toolName)
                    .goal(goal)
                    .rawToolIntent(intent)
                    .runtimeContext(context)
                    .build());
            if (result == null || result.getStatus() == null) {
                return safeFailure(context, RuntimeFailureCodeEnumVO.ACTION_HANDLER_UNAVAILABLE,
                        "Tool execution failed.", "ToolActionOrchestratorPort returned null result.");
            }
            if (result.getStatus() == ToolActionStatusEnumVO.WAITING_USER) {
                return MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.WAITING_USER)
                        .nextPhase(RuntimePhaseEnumVO.WAITING_USER)
                        .pendingInputId(result.getPendingInputId())
                        .askUserRequest(result.getAskUserRequest())
                        .pauseIntent(result.getPauseIntent())
                        .message(result.getMessage())
                        .build();
            }
            if (result.getStatus() == ToolActionStatusEnumVO.CONTINUE_LOOP) {
                return MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                        .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                        .createdEvidenceIds(result.getEvidenceIds())
                        .actionEffect(ActionEffectVO.builder()
                                .action(MainAgentActionTypeEnumVO.CALL_TOOL.code())
                                .status(result.getActionEffectStatus() == null ? null : result.getActionEffectStatus().name())
                                .message(resultMessage(result))
                                .loopIndex(context.getLoopIndex())
                                .resultRef(firstContentRef(result))
                                .createdEvidenceIds(result.getEvidenceIds())
                                .metadata(resultMetadata(result))
                                .build())
                        .message(resultMessage(result))
                        .build();
            }
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.FAILED)
                    .nextPhase(RuntimePhaseEnumVO.FAILED)
                    .safeFailure(result.getSafeFailure())
                    .message(result.getMessage())
                    .build();
        } catch (IllegalArgumentException e) {
            return validationFailure(context, e.getMessage());
        }
    }

    private String firstContentRef(ToolActionResultVO result) {
        if (result == null || result.getEvidence() == null) {
            return null;
        }
        return result.getEvidence().stream()
                .filter(java.util.Objects::nonNull)
                .map(yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO::getContentRef)
                .filter(ref -> ref != null && !ref.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String resultMessage(ToolActionResultVO result) {
        if (result != null && result.getEvidence() != null) {
            String summary = result.getEvidence().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO::getSummary)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);
            if (summary != null) {
                return summary;
            }
        }
        return result == null ? null : result.getMessage();
    }

    private Map<String, Object> resultMetadata(ToolActionResultVO result) {
        if (result == null || result.getEvidence() == null) {
            return Map.of();
        }
        return result.getEvidence().stream()
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(evidence -> {
                    Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                    if (evidence.getMetadata() != null) metadata.putAll(evidence.getMetadata());
                    metadata.put("contentFormat", evidence.getContentFormat());
                    metadata.put("totalChars", evidence.getTotalChars());
                    metadata.put("totalBytes", evidence.getTotalBytes());
                    metadata.values().removeIf(java.util.Objects::isNull);
                    return metadata;
                })
                .orElse(Map.of());
    }
}
