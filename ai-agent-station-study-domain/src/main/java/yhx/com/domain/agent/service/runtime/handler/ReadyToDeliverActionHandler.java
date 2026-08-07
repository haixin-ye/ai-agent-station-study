package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.ActionEffectVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionHandler;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.TaskDeliveryReadinessPolicy;

public class ReadyToDeliverActionHandler extends MainActionHandlerSupport implements MainActionHandler {

    private final TaskDeliveryReadinessPolicy readinessPolicy;

    public ReadyToDeliverActionHandler(TaskDeliveryReadinessPolicy readinessPolicy,
                                       RuntimeFailureFactory failureFactory,
                                       DeveloperTraceRecorder traceRecorder) {
        super(failureFactory, traceRecorder);
        this.readinessPolicy = readinessPolicy == null ? new TaskDeliveryReadinessPolicy() : readinessPolicy;
    }

    @Override
    public MainAgentActionTypeEnumVO actionType() {
        return MainAgentActionTypeEnumVO.READY_TO_DELIVER;
    }

    @Override
    public MainActionHandlerResult handle(RuntimeExecutionContext context, MainAgentActionVO action) {
        if (context == null || context.getRunContextState() == null) {
            return validationFailure(context, "READY_TO_DELIVER requires RunContextState.");
        }
        if (requireStateDelta(action).get("deliveryRequest") == null) {
            return validationFailure(context, "READY_TO_DELIVER requires stateDelta.deliveryRequest.");
        }
        if (!readinessPolicy.isReady(context.getRunContextState().getTaskLedger())) {
            String message = "Delivery readiness was rejected because TaskLedger still contains incomplete deliverables.";
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                    .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                    .actionEffect(ActionEffectVO.builder()
                            .action(MainAgentActionTypeEnumVO.READY_TO_DELIVER.code())
                            .status("DELIVERY_NOT_READY")
                            .message(message)
                            .loopIndex(context.getLoopIndex())
                            .build())
                    .message(message)
                    .build();
        }
        context.getRunContextState().setMainAgentStage(MainAgentStageEnumVO.DELIVERING);
        return MainActionHandlerResult.builder()
                .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                .nextPhase(RuntimePhaseEnumVO.CALLING_MAIN_NODE)
                .message("Task completion validated; entering delivery stage.")
                .build();
    }
}
