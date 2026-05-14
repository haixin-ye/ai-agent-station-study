package yhx.com.domain.agent.model.valobj.enums.tool;

import java.util.Arrays;
import java.util.Optional;

public enum ApprovalPolicyEnumVO {
    NEVER("NEVER", "No approval required."),
    ASK_USER_BEFORE_EXECUTE("ASK_USER_BEFORE_EXECUTE", "Ask before execution."),
    ASK_USER_ON_RISK("ASK_USER_ON_RISK", "Ask only for risky calls.");

    private final String code;
    private final String info;

    ApprovalPolicyEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public static Optional<ApprovalPolicyEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
