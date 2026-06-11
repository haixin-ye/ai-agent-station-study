package yhx.com.domain.agent.model.valobj.enums.tool;

import java.util.Arrays;
import java.util.Optional;

public enum PermissionModeEnumVO {
    ALLOW("ALLOW", "Allow when all facts are valid."),
    ASK_USER("ASK_USER", "Ask user before execution."),
    DENY("DENY", "Always deny.");

    private final String code;
    private final String info;

    PermissionModeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public static Optional<PermissionModeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
