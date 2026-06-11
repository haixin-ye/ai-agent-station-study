package yhx.com.domain.agent.model.valobj.enums.runtime;

import java.util.Arrays;
import java.util.Optional;

public enum RunStatusEnumVO {
    CREATED("CREATED", "Run record is created but execution has not started."),
    RUNNING("RUNNING", "Runtime is actively executing the run."),
    WAITING_USER("WAITING_USER", "Runtime is paused and waiting for user input or approval."),
    WAITING_CHILDREN("WAITING_CHILDREN", "Runtime is paused and waiting for delegated child agents."),
    COMPLETED("COMPLETED", "Run completed with a guarded final response."),
    FAILED("FAILED", "Run ended with a user-safe failure response."),
    CANCELLED("CANCELLED", "Run was cancelled by user or system.");

    private final String code;
    private final String info;

    RunStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<RunStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}

