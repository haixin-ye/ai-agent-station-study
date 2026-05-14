package yhx.com.domain.agent.service.invocation;

import java.util.Arrays;
import java.util.Optional;

public enum NodeInvocationFailureTypeEnumVO {
    CLIENT_ERROR("CLIENT_ERROR", "Node client failed."),
    EMPTY_OUTPUT("EMPTY_OUTPUT", "Model returned empty output."),
    INVALID_JSON("INVALID_JSON", "Model returned invalid JSON."),
    CONTRACT_VIOLATION("CONTRACT_VIOLATION", "Output violated Java-owned contract."),
    REPAIR_BUDGET_EXHAUSTED("REPAIR_BUDGET_EXHAUSTED", "Repair budget was exhausted.");

    private final String code;
    private final String info;

    NodeInvocationFailureTypeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<NodeInvocationFailureTypeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
