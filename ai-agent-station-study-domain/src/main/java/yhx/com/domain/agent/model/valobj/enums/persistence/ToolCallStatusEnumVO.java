package yhx.com.domain.agent.model.valobj.enums.persistence;

import java.util.Arrays;
import java.util.Optional;

public enum ToolCallStatusEnumVO {
    CREATED("CREATED", "Tool call was created."),
    APPROVAL_PENDING("APPROVAL_PENDING", "Tool call is waiting for user approval."),
    RUNNING("RUNNING", "Tool call is running."),
    SUCCEEDED("SUCCEEDED", "Tool call succeeded."),
    FAILED("FAILED", "Tool call failed."),
    CANCELLED("CANCELLED", "Tool call was cancelled."),
    PERMISSION_DENIED("PERMISSION_DENIED", "Tool call was denied by permission policy.");

    private final String code;
    private final String info;

    ToolCallStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<ToolCallStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
