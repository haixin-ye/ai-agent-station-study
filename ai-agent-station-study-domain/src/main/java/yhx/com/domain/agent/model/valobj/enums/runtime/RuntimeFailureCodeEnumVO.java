package yhx.com.domain.agent.model.valobj.enums.runtime;

import java.util.Arrays;
import java.util.Optional;

public enum RuntimeFailureCodeEnumVO {
    MISSING_COMMAND("MISSING_COMMAND", "Runtime command is missing."),
    MAX_LOOP_REACHED("MAX_LOOP_REACHED", "Runtime loop limit was reached."),
    ILLEGAL_PHASE_TRANSITION("ILLEGAL_PHASE_TRANSITION", "Runtime phase transition is illegal."),
    MISSING_ACTIVE_RUN("MISSING_ACTIVE_RUN", "Run record is missing."),
    MISSING_ACTIVE_PENDING_INPUT("MISSING_ACTIVE_PENDING_INPUT", "No active pending input exists."),
    INVALID_PENDING_ANSWER("INVALID_PENDING_ANSWER", "User answer cannot resolve the pending input."),
    CONTRACT_REPAIR_EXHAUSTED("CONTRACT_REPAIR_EXHAUSTED", "Contract repair attempts were exhausted."),
    CONTEXT_PREPARATION_FAILED("CONTEXT_PREPARATION_FAILED", "Context preparation failed without fallback."),
    ACTION_HANDLER_UNAVAILABLE("ACTION_HANDLER_UNAVAILABLE", "No action handler is available."),
    MAIN_ACTION_CONTRACT_FAILED("MAIN_ACTION_CONTRACT_FAILED", "Main agent action failed contract validation."),
    MAIN_ACTION_PARSE_FAILED("MAIN_ACTION_PARSE_FAILED", "Main agent action could not be parsed."),
    FINAL_INTERNAL_LEAK("FINAL_INTERNAL_LEAK", "Final answer leaked internal runtime details.");

    private final String code;
    private final String info;

    RuntimeFailureCodeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<RuntimeFailureCodeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
