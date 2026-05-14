package yhx.com.domain.agent.model.valobj.enums.interaction;

import java.util.Arrays;
import java.util.Optional;

public enum UserAnswerStatusEnumVO {
    RESOLVED("RESOLVED", "Answer was normalized and can be passed back to the source component."),
    FAILED("FAILED", "Answer was invalid for the pending input contract."),
    CANCELLED("CANCELLED", "User cancelled the pending input.");

    private final String code;
    private final String info;

    UserAnswerStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<UserAnswerStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
