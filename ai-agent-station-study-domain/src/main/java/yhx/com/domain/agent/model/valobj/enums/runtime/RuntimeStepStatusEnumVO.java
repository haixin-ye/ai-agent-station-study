package yhx.com.domain.agent.model.valobj.enums.runtime;

import java.util.Arrays;
import java.util.Optional;

public enum RuntimeStepStatusEnumVO {
    CONTINUE("CONTINUE", "Runtime should continue the same run loop."),
    WAITING_USER("WAITING_USER", "Runtime is paused and waiting for user input."),
    COMPLETED("COMPLETED", "Runtime completed the run."),
    FAILED("FAILED", "Runtime failed safely."),
    CANCELLED("CANCELLED", "Runtime was cancelled.");

    private final String code;
    private final String info;

    RuntimeStepStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<RuntimeStepStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
