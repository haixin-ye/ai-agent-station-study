package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.port.FinalDeliveryPort;

public class RepairFinalActionHandler extends FinalActionHandler {

    public RepairFinalActionHandler(FinalDeliveryPort finalDeliveryPort,
                                    RuntimeFailureFactory failureFactory,
                                    DeveloperTraceRecorder traceRecorder) {
        super(finalDeliveryPort, failureFactory, traceRecorder);
    }

    @Override
    public MainAgentActionTypeEnumVO actionType() {
        return MainAgentActionTypeEnumVO.REPAIR_FINAL;
    }

    @Override
    public MainActionHandlerResult handle(RuntimeExecutionContext context, MainAgentActionVO action) {
        if (context.getCurrentPhase() != RuntimePhaseEnumVO.REPAIRING_FINAL
                && context.getCurrentPhase() != RuntimePhaseEnumVO.HANDLING_ACTION) {
            return validationFailure(context, "REPAIR_FINAL is only valid during final repair.");
        }
        try {
            FinalAnswerCandidateVO candidate = requireFinalAnswerCandidate(action);
            return routeDelivery(context, MainAgentActionTypeEnumVO.REPAIR_FINAL, candidate);
        } catch (IllegalArgumentException e) {
            return validationFailure(context, e.getMessage());
        }
    }
}
