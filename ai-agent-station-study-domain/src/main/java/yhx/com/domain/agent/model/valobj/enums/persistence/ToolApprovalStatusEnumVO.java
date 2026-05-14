package yhx.com.domain.agent.model.valobj.enums.persistence;

import java.util.Arrays;
import java.util.Optional;

public enum ToolApprovalStatusEnumVO {
    PENDING("PENDING", "Approval is waiting for user decision."),
    APPROVED("APPROVED", "User approved the tool call."),
    REJECTED("REJECTED", "User rejected the tool call."),
    CANCELLED("CANCELLED", "Approval was cancelled."),
    EXPIRED("EXPIRED", "Approval expired.");

    private final String code;
    private final String info;

    ToolApprovalStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<ToolApprovalStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
