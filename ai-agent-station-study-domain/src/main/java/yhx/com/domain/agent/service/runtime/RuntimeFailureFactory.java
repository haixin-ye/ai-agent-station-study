package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeSafeFailureVO;

public class RuntimeFailureFactory {

    private static final String DEFAULT_USER_MESSAGE = "抱歉，这次任务没有被安全完成。请稍后重试，或调整问题后再试。";

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
            return "这次任务需要的步骤过多，系统已安全停止。请缩小问题范围后再试。";
        }
        if (code == RuntimeFailureCodeEnumVO.INVALID_PENDING_ANSWER) {
            return "你的回答无法应用到当前问题，请重新回答。";
        }
        return DEFAULT_USER_MESSAGE;
    }
}
