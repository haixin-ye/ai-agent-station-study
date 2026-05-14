package yhx.com.domain.agent.model.valobj.enums.persistence;

import java.util.Arrays;
import java.util.Optional;

public enum MessageRoleEnumVO {
    USER("USER", "User message."),
    ASSISTANT("ASSISTANT", "Assistant message."),
    SYSTEM("SYSTEM", "System message."),
    TOOL("TOOL", "Tool message.");

    private final String code;
    private final String info;

    MessageRoleEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<MessageRoleEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
