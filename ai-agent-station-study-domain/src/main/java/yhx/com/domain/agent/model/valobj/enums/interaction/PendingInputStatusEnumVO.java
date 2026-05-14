package yhx.com.domain.agent.model.valobj.enums.interaction;

import java.util.Arrays;
import java.util.Optional;

public enum PendingInputStatusEnumVO {
    PENDING("PENDING", "Pending input is waiting for user answer."),
    ANSWERED("ANSWERED", "Pending input has been answered."),
    CANCELLED("CANCELLED", "Pending input was cancelled."),
    EXPIRED("EXPIRED", "Pending input expired.");

    private final String code;
    private final String info;

    PendingInputStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<PendingInputStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
