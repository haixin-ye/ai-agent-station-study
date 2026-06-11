package yhx.com.domain.agent.model.valobj.enums.tool;

import java.util.Arrays;
import java.util.Optional;

public enum RequiredPermissionEnumVO {
    NONE("NONE", "No external permission required."),
    READ_ONLY("READ_ONLY", "Read-only external access."),
    WORKSPACE_WRITE("WORKSPACE_WRITE", "Write access inside workspace scope."),
    EXTERNAL_WRITE("EXTERNAL_WRITE", "External side effect or publication."),
    DESTRUCTIVE("DESTRUCTIVE", "Destructive operation.");

    private final String code;
    private final String info;

    RequiredPermissionEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<RequiredPermissionEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
