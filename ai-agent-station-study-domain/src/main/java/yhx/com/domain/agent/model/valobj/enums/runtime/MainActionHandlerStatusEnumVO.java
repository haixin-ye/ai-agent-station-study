package yhx.com.domain.agent.model.valobj.enums.runtime;

import java.util.Arrays;
import java.util.Optional;

public enum MainActionHandlerStatusEnumVO {
    CONTINUE_LOOP("CONTINUE_LOOP", "Action completed and Runtime should continue the loop."),
    WAITING_USER("WAITING_USER", "Action requires user input and Runtime should pause."),
    COMPLETED("COMPLETED", "Action completed the run."),
    FAILED("FAILED", "Action failed safely."),
    CANCELLED("CANCELLED", "Action cancelled the run.");

    private final String code;
    private final String info;

    MainActionHandlerStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<MainActionHandlerStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
