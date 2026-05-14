package yhx.com.domain.agent.model.valobj.enums.persistence;

import java.util.Arrays;
import java.util.Optional;

public enum RunEventTypeEnumVO {
    RUN_STARTED("RUN_STARTED", "Run has started."),
    STATUS_CHANGED("STATUS_CHANGED", "Run status or phase changed."),
    ASSISTANT_MESSAGE("ASSISTANT_MESSAGE", "Assistant-visible message was produced."),
    ASK_USER("ASK_USER", "Runtime is asking the user for input."),
    FINAL_READY("FINAL_READY", "Guarded final response is ready."),
    RUN_FAILED("RUN_FAILED", "Run failed with a safe response.");

    private final String code;
    private final String info;

    RunEventTypeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<RunEventTypeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
