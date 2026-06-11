package yhx.com.domain.agent.service.runtime.handler;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.model.valobj.agent.AgentProfileVO;
import yhx.com.domain.agent.model.valobj.contract.ContractValidationResult;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentProfileTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.agent.AgentActionPermissionPolicy;
import yhx.com.domain.agent.service.agent.AgentProfileRegistry;
import yhx.com.domain.agent.service.contract.ContractValidator;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.domain.agent.service.runtime.MainActionHandler;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;

public class DefaultMainActionDispatcher implements MainActionDispatcher {

    private final MainActionHandlerRegistry handlerRegistry;
    private final ContractValidator contractValidator;
    private final RuntimeFailureFactory failureFactory;
    private final DeveloperTraceRecorder traceRecorder;
    private final AgentActionPermissionPolicy permissionPolicy;
    private final AgentProfileVO mainAgentProfile;

    public DefaultMainActionDispatcher(MainActionHandlerRegistry handlerRegistry,
                                       ContractValidator contractValidator,
                                       RuntimeFailureFactory failureFactory,
                                       DeveloperTraceRecorder traceRecorder) {
        this.handlerRegistry = handlerRegistry;
        this.contractValidator = contractValidator == null ? ContractValidator.defaultValidator() : contractValidator;
        this.failureFactory = failureFactory == null ? new RuntimeFailureFactory() : failureFactory;
        this.traceRecorder = traceRecorder;
        this.permissionPolicy = new AgentActionPermissionPolicy();
        this.mainAgentProfile = AgentProfileRegistry.defaultRegistry().requireProfile(AgentProfileTypeEnumVO.MAIN_AGENT);
    }

    public DefaultMainActionDispatcher(MainActionHandlerRegistry handlerRegistry,
                                       ContractValidator contractValidator,
                                       RuntimeFailureFactory failureFactory,
                                       DeveloperTraceRecorder traceRecorder,
                                       AgentActionPermissionPolicy permissionPolicy,
                                       AgentProfileVO mainAgentProfile) {
        this.handlerRegistry = handlerRegistry;
        this.contractValidator = contractValidator == null ? ContractValidator.defaultValidator() : contractValidator;
        this.failureFactory = failureFactory == null ? new RuntimeFailureFactory() : failureFactory;
        this.traceRecorder = traceRecorder;
        this.permissionPolicy = permissionPolicy == null ? new AgentActionPermissionPolicy() : permissionPolicy;
        this.mainAgentProfile = mainAgentProfile == null
                ? AgentProfileRegistry.defaultRegistry().requireProfile(AgentProfileTypeEnumVO.MAIN_AGENT)
                : mainAgentProfile;
    }

    @Override
    public MainActionHandlerResult dispatch(RuntimeExecutionContext context, MainAgentActionVO action) {
        if (action == null || action.getAction() == null || action.getAction().isBlank()) {
            return failure(context, "MainAgentAction action is missing.");
        }
        MainAgentActionTypeEnumVO actionType = MainAgentActionTypeEnumVO.ofCode(action.getAction()).orElse(null);
        if (actionType == null) {
            return failure(context, "Unknown MainAgentAction: " + action.getAction());
        }

        ContractValidationResult validationResult = contractValidator.validateMainAgentAction(JSON.toJSONString(action));
        if (!validationResult.isPassed()) {
            if (traceRecorder != null) {
                traceRecorder.contractFailure(context.getRunId(), context.getLoopIndex(),
                        RuntimeFailureCodeEnumVO.MAIN_ACTION_CONTRACT_FAILED, null);
            }
            return failure(context, validationResult.getViolations().toString());
        }

        String permissionFailure = permissionPolicy.validate(mainAgentProfile,
                        permissionPolicy.defaultEffectiveCapabilities(mainAgentProfile),
                        action.getAction())
                .orElse(null);
        if (permissionFailure != null) {
            return failure(context, permissionFailure);
        }

        MainActionHandler handler = handlerRegistry.getHandler(actionType);
        if (handler == null) {
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.FAILED)
                    .nextPhase(RuntimePhaseEnumVO.FAILED)
                    .safeFailure(failureFactory.actionHandlerUnavailable(action.getAction()))
                    .message("Missing handler for action " + action.getAction())
                    .build();
        }
        if (traceRecorder != null) {
            traceRecorder.actionParsed(context.getRunId(), context.getLoopIndex(), actionType, null);
        }
        return handler.handle(context, action);
    }

    private MainActionHandlerResult failure(RuntimeExecutionContext context, String developerMessage) {
        return MainActionHandlerResult.builder()
                .status(MainActionHandlerStatusEnumVO.FAILED)
                .nextPhase(RuntimePhaseEnumVO.FAILED)
                .safeFailure(failureFactory.create(RuntimeFailureCodeEnumVO.MAIN_ACTION_CONTRACT_FAILED,
                        RuntimePhaseEnumVO.HANDLING_ACTION, developerMessage, true))
                .message(developerMessage)
                .build();
    }
}
