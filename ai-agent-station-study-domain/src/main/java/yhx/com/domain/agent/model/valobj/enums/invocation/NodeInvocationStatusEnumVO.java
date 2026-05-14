package yhx.com.domain.agent.model.valobj.enums.invocation;

import java.util.Arrays;
import java.util.Optional;

public enum NodeInvocationStatusEnumVO {
    SUCCESS("SUCCESS", "Invocation succeeded without repair."),
    PARSE_FAILED("PARSE_FAILED", "Raw output could not be parsed."),
    CONTRACT_FAILED("CONTRACT_FAILED", "Parsed output violated the contract."),
    REPAIR_SUCCEEDED("REPAIR_SUCCEEDED", "Invocation succeeded after bounded repair."),
    REPAIR_FAILED("REPAIR_FAILED", "Repair attempts were exhausted or invalid."),
    CLIENT_FAILED("CLIENT_FAILED", "Node client call failed.");

    private final String code;
    private final String info;

    NodeInvocationStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<NodeInvocationStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
