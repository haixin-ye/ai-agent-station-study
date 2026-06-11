package yhx.com.domain.agent.model.valobj.enums.interaction;

import java.util.Arrays;
import java.util.Optional;

public enum UserAnswerTypeEnumVO {
    OPTION("OPTION", "User clicked one stored option."),
    FREE_TEXT("FREE_TEXT", "User submitted free text."),
    CANCEL("CANCEL", "User cancelled."),
    INVALID("INVALID", "User answer is invalid.");

    private final String code;
    private final String info;

    UserAnswerTypeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<UserAnswerTypeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
