package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
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
            if (isBlank(capabilityCode)) {
                throw new IllegalArgumentException("toolIntent.capabilityCode is required.");
            }
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
                        .message(result.getMessage())
                        .build();
            }
            if (result.getStatus() == ToolActionStatusEnumVO.CONTINUE_LOOP) {
                return MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                        .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                        .createdEvidenceIds(result.getEvidenceIds())
                        .message(result.getMessage())
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
}
