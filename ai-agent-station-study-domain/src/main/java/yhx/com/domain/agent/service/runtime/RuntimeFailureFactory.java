package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeSafeFailureVO;

public class RuntimeFailureFactory {

    private static final String DEFAULT_USER_MESSAGE = "The task could not be completed safely. Please try again or adjust your request.";

    public RuntimeSafeFailureVO create(RuntimeFailureCodeEnumVO code, RuntimePhaseEnumVO phase, String developerMessage, boolean retryable) {
        return RuntimeSafeFailureVO.builder()
                .failureCode(code)
                .phase(phase)
                .userMessage(userMessage(code))
                .developerMessage(developerMessage)
                .retryable(retryable)
                .build();
    }

    public RuntimeSafeFailureVO maxLoopReached(RuntimePhaseEnumVO phase) {
        return create(RuntimeFailureCodeEnumVO.MAX_LOOP_REACHED, phase, "Runtime reached max loop limit.", false);
    }

    public RuntimeSafeFailureVO illegalTransition(RuntimePhaseEnumVO from, RuntimePhaseEnumVO to) {
        return create(RuntimeFailureCodeEnumVO.ILLEGAL_PHASE_TRANSITION, from,
                "Illegal phase transition from " + from + " to " + to + ".", false);
    }

    public RuntimeSafeFailureVO missingPendingInput(String runId) {
        return create(RuntimeFailureCodeEnumVO.MISSING_ACTIVE_PENDING_INPUT, RuntimePhaseEnumVO.RESOLVING_USER_ANSWER,
                "No active pending input for run " + runId + ".", true);
    }

    public RuntimeSafeFailureVO invalidPendingAnswer(String message) {
        return create(RuntimeFailureCodeEnumVO.INVALID_PENDING_ANSWER, RuntimePhaseEnumVO.RESOLVING_USER_ANSWER, message, true);
    }

    public RuntimeSafeFailureVO actionHandlerUnavailable(String action) {
        return create(RuntimeFailureCodeEnumVO.ACTION_HANDLER_UNAVAILABLE, RuntimePhaseEnumVO.HANDLING_ACTION,
                "Action handler unavailable for " + action + ".", false);
    }

    private String userMessage(RuntimeFailureCodeEnumVO code) {
        if (code == RuntimeFailureCodeEnumVO.MAX_LOOP_REACHED) {
            return "The task required too many steps to complete safely. Please narrow the request and try again.";
        }
        if (code == RuntimeFailureCodeEnumVO.INVALID_PENDING_ANSWER) {
            return "The submitted answer could not be applied to the current question. Please answer again.";
        }
        return DEFAULT_USER_MESSAGE;
    }
}
