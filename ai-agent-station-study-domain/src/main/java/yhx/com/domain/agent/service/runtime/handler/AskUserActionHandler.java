package yhx.com.domain.agent.service.runtime.handler;

import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.interaction.MainAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.AskUserRequestPolicy;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionHandler;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;

import java.util.Map;

public class AskUserActionHandler extends MainActionHandlerSupport implements MainActionHandler {

    private final AskUserRequestPolicy askUserRequestPolicy = new AskUserRequestPolicy();

    public AskUserActionHandler(RuntimeFailureFactory failureFactory,
                                DeveloperTraceRecorder traceRecorder) {
        super(failureFactory, traceRecorder);
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
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.WAITING_USER)
                    .nextPhase(RuntimePhaseEnumVO.WAITING_USER)
                    .askUserRequest(request)
                    .message("MainAgent requested user input.")
                    .build();
        } catch (IllegalArgumentException e) {
            return validationFailure(context, e.getMessage());
        }
    }

    private void validateAskUserRequest(AskUserRequestVO request) {
        String failure = askUserRequestPolicy.normalizeAndValidate(request);
        if (failure != null) {
            throw new IllegalArgumentException(failure);
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
