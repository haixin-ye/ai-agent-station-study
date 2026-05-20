package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.enums.interaction.PendingInputTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateCommand;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputCreateResult;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.interaction.MainAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionHandler;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;

import java.util.Map;

public class AskUserActionHandler extends MainActionHandlerSupport implements MainActionHandler {

    private final UserInteractionManager userInteractionManager;

    public AskUserActionHandler(UserInteractionManager userInteractionManager,
                                RuntimeFailureFactory failureFactory,
                                DeveloperTraceRecorder traceRecorder) {
        super(failureFactory, traceRecorder);
        this.userInteractionManager = userInteractionManager;
    }

    @Override
    public MainAgentActionTypeEnumVO actionType() {
        return MainAgentActionTypeEnumVO.ASK_USER;
    }

    @Override
    public MainActionHandlerResult handle(RuntimeExecutionContext context, MainAgentActionVO action) {
        try {
            AskUserRequestVO request = requireAskUserRequest(action);
            validateAskUserRequest(request);
            if (alreadyAnswered(context, request)) {
                return MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                        .nextPhase(RuntimePhaseEnumVO.CALLING_MAIN_NODE)
                        .message("User already answered this clarification. Continue with userClarifications.")
                        .build();
            }
            PendingInputCreateResult pending = userInteractionManager.createPendingInput(PendingInputCreateCommand.builder()
                    .runId(context.getRunId())
                    .sessionId(context.getSessionId())
                    .sourceComponent(MainAgentPendingInputHandler.HANDLER_CODE)
                    .pendingType(PendingInputTypeEnumVO.MAIN_AGENT_QUESTION.code())
                    .askUserRequest(request)
                    .continuation(ContinuationCheckpointVO.builder()
                            .handler(MainAgentPendingInputHandler.HANDLER_CODE)
                            .resumePhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                            .sourceComponent(MainAgentPendingInputHandler.HANDLER_CODE)
                            .relatedRunId(context.getRunId())
                            .relatedLoopIndex(context.getLoopIndex())
                            .payload(Map.of())
                            .build())
                    .build());
            if (!Boolean.TRUE.equals(pending.getCreated())) {
                return validationFailure(context, pending.getFailureMessage());
            }
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.WAITING_USER)
                    .nextPhase(RuntimePhaseEnumVO.WAITING_USER)
                    .askUserRequest(request)
                    .pendingInputId(pending.getPendingInputId())
                    .message("MainAgent requested user input.")
                    .build();
        } catch (IllegalArgumentException e) {
            return validationFailure(context, e.getMessage());
        }
    }

    private void validateAskUserRequest(AskUserRequestVO request) {
        if (request == null || isBlank(request.getQuestion()) || isBlank(request.getInputMode())) {
            throw new IllegalArgumentException("askUserRequest.question and inputMode are required.");
        }
        if ("SINGLE_CHOICE".equals(request.getInputMode())
                || "SINGLE_CHOICE_OR_FREE_TEXT".equals(request.getInputMode())) {
            if (request.getOptions() == null || request.getOptions().isEmpty()) {
                throw new IllegalArgumentException("askUserRequest.options are required for choice input modes.");
            }
        }
    }

    private boolean alreadyAnswered(RuntimeExecutionContext context, AskUserRequestVO request) {
        if (context == null || context.getRuntimeFacts() == null || request == null || isBlank(request.getQuestion())) {
            return false;
        }
        Object value = context.getRuntimeFacts().get("userClarifications");
        if (!(value instanceof Iterable<?> iterable)) {
            return false;
        }
        String question = normalize(request.getQuestion());
        for (Object item : iterable) {
            if (item instanceof UserClarificationVO clarification
                    && question.equals(normalize(clarification.getQuestion()))
                    && (clarification.getValue() != null || !isBlank(clarification.getFreeText()))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }
}
