package yhx.com.domain.agent.model.valobj.enums.runtime;

import java.util.Arrays;
import java.util.Optional;

public enum ToolActionStatusEnumVO {
    WAITING_USER("WAITING_USER", "Tool flow is waiting for user approval."),
    CONTINUE_LOOP("CONTINUE_LOOP", "Tool flow completed or was denied and Runtime can continue."),
    FAILED("FAILED", "Tool flow failed safely.");

    private final String code;
    private final String info;

    ToolActionStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<ToolActionStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
