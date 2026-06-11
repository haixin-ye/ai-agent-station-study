package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.valobj.context.FailureVO;
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

public class FailActionHandler extends MainActionHandlerSupport implements MainActionHandler {

    private final FinalDeliveryPort finalDeliveryPort;

    public FailActionHandler(FinalDeliveryPort finalDeliveryPort,
                             RuntimeFailureFactory failureFactory,
                             DeveloperTraceRecorder traceRecorder) {
        super(failureFactory, traceRecorder);
        this.finalDeliveryPort = finalDeliveryPort;
    }

    @Override
    public MainAgentActionTypeEnumVO actionType() {
        return MainAgentActionTypeEnumVO.FAIL;
    }

    @Override
    public MainActionHandlerResult handle(RuntimeExecutionContext context, MainAgentActionVO action) {
        try {
            FailureVO failure = requireFailure(action);
            if (traceRecorder != null) {
                traceRecorder.error(context.getRunId(), context.getLoopIndex(), RuntimeFailureCodeEnumVO.MAIN_ACTION_CONTRACT_FAILED,
                        failure.getFailureCode(), null);
            }
            if (finalDeliveryPort == null) {
                return safeFailure(context, RuntimeFailureCodeEnumVO.ACTION_HANDLER_UNAVAILABLE,
                        "Failure delivery is unavailable.", "FinalDeliveryPort is not configured.");
            }
            FinalAnswerCandidateVO candidate = FinalAnswerCandidateVO.builder().content(failure.getMessage()).build();
            FinalDeliveryResultVO delivery = finalDeliveryPort.deliver(FinalDeliveryCommandVO.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .userId(context.getUserId())
                    .agentId(context.getAgentId())
                    .userMessageId(context.getUserMessageId())
                    .userInput(context.getUserInput())
                    .loopIndex(context.getLoopIndex())
                    .sourceAction(MainAgentActionTypeEnumVO.FAIL)
                    .finalAnswerCandidate(candidate)
                    .failure(failure)
                    .userClarifications(userClarifications(context))
                    .finalRepairCount(context.countersOrInitial().finalRepairCountValue())
                    .build());
            if (delivery != null && delivery.getStatus() == FinalDeliveryStatusEnumVO.DELIVERED) {
                return MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.FAILED)
                        .nextPhase(RuntimePhaseEnumVO.FAILED)
                        .finalAnswerCandidate(FinalAnswerCandidateVO.builder()
                                .content(delivery.getDeliveredContent() == null ? candidate.getContent() : delivery.getDeliveredContent())
                                .contentRef(delivery.getFinalAnswerRef())
                                .build())
                        .finalMessageId(delivery.getFinalMessageId())
                        .finalAnswerRef(delivery.getFinalAnswerRef())
                        .message(failure.getMessage())
                        .build();
            }
            return safeFailure(context, RuntimeFailureCodeEnumVO.FINAL_INTERNAL_LEAK,
                    "The task could not be completed safely.", "FAIL action delivery did not pass final delivery.");
        } catch (IllegalArgumentException e) {
            return validationFailure(context, e.getMessage());
        }
    }
}
