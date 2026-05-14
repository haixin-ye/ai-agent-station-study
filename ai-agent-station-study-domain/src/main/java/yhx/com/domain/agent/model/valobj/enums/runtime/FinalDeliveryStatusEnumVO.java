package yhx.com.domain.agent.model.valobj.enums.runtime;

import java.util.Arrays;
import java.util.Optional;

public enum FinalDeliveryStatusEnumVO {
    DELIVERED("DELIVERED", "Final response passed delivery checks."),
    NEEDS_REPAIR("NEEDS_REPAIR", "Final response needs repair before delivery."),
    FAILED("FAILED", "Final response delivery failed.");

    private final String code;
    private final String info;

    FinalDeliveryStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<FinalDeliveryStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
