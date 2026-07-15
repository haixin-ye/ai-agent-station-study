package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.valobj.enums.runtime.FinalDeliveryStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryResultVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionHandler;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.port.FinalDeliveryPort;

public class FinalActionHandler extends MainActionHandlerSupport implements MainActionHandler {

    private final FinalDeliveryPort finalDeliveryPort;

    public FinalActionHandler(FinalDeliveryPort finalDeliveryPort,
                              RuntimeFailureFactory failureFactory,
                              DeveloperTraceRecorder traceRecorder) {
        super(failureFactory, traceRecorder);
        this.finalDeliveryPort = finalDeliveryPort;
    }

    @Override
    public MainAgentActionTypeEnumVO actionType() {
        return MainAgentActionTypeEnumVO.FINAL;
    }

    @Override
    public MainActionHandlerResult handle(RuntimeExecutionContext context, MainAgentActionVO action) {
        try {
            FinalAnswerCandidateVO candidate = requireFinalAnswerCandidate(action);
            return routeDelivery(context, MainAgentActionTypeEnumVO.FINAL, candidate);
        } catch (IllegalArgumentException e) {
            return validationFailure(context, e.getMessage());
        }
    }

    protected MainActionHandlerResult routeDelivery(RuntimeExecutionContext context,
                                                    MainAgentActionTypeEnumVO sourceAction,
                                                    FinalAnswerCandidateVO candidate) {
        if (finalDeliveryPort == null) {
            return safeFailure(context, RuntimeFailureCodeEnumVO.ACTION_HANDLER_UNAVAILABLE,
                    "Final delivery is unavailable.", "FinalDeliveryPort is not configured.");
        }
        FinalDeliveryResultVO result = finalDeliveryPort.deliver(FinalDeliveryCommandVO.builder()
                .runId(context.getRunId())
                .sessionId(context.getSessionId())
                .userId(context.getUserId())
                .agentId(context.getAgentId())
                .userMessageId(context.getUserMessageId())
                .userInput(context.getUserInput())
                .loopIndex(context.getLoopIndex())
                .sourceAction(sourceAction)
                .finalAnswerCandidate(candidate)
                .userClarifications(userClarifications(context))
                .verifiedToolCallRefs(verifiedToolCallRefs(context))
                .finalRepairCount(context.countersOrInitial().finalRepairCountValue())
                .build());
        if (result == null || result.getStatus() == null) {
            return safeFailure(context, RuntimeFailureCodeEnumVO.ACTION_HANDLER_UNAVAILABLE,
                    "Final delivery failed.", "FinalDeliveryPort returned null result.");
        }
        if (result.getStatus() == FinalDeliveryStatusEnumVO.DELIVERED) {
            FinalAnswerCandidateVO delivered = FinalAnswerCandidateVO.builder()
                    .content(result.getDeliveredContent() == null ? candidate.getContent() : result.getDeliveredContent())
                    .contentRef(result.getFinalAnswerRef())
                    .format(candidate.getFormat())
                    .build();
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.COMPLETED)
                    .nextPhase(RuntimePhaseEnumVO.COMPLETED)
                    .finalAnswerCandidate(delivered)
                    .finalMessageId(result.getFinalMessageId())
                    .finalAnswerRef(result.getFinalAnswerRef())
                    .message(result.getMessage())
                    .build();
        }
        if (result.getStatus() == FinalDeliveryStatusEnumVO.NEEDS_REPAIR) {
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                    .nextPhase(RuntimePhaseEnumVO.REPAIRING_FINAL)
                    .finalAnswerCandidate(candidate)
                    .message(result.getMessage())
                    .build();
        }
        return MainActionHandlerResult.builder()
                .status(MainActionHandlerStatusEnumVO.FAILED)
                .nextPhase(RuntimePhaseEnumVO.FAILED)
                .safeFailure(result.getSafeFailure())
                .message(result.getMessage())
                .build();
    }
}
