package yhx.com.domain.agent.model.valobj.enums.persistence;

import java.util.Arrays;
import java.util.Optional;

public enum AuditTypeEnumVO {
    RUN_CREATED("RUN_CREATED", "Run was created."),
    PERMISSION_CHECKED("PERMISSION_CHECKED", "Tool permission was checked."),
    USER_APPROVAL_RECORDED("USER_APPROVAL_RECORDED", "User approval decision was recorded."),
    FINAL_DELIVERED("FINAL_DELIVERED", "Final answer was delivered."),
    RUN_CANCELLED("RUN_CANCELLED", "Run was cancelled.");

    private final String code;
    private final String info;

    AuditTypeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<AuditTypeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
