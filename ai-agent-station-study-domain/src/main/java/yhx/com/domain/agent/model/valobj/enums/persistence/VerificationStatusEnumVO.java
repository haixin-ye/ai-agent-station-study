package yhx.com.domain.agent.model.valobj.enums.persistence;

import java.util.Arrays;
import java.util.Optional;

public enum VerificationStatusEnumVO {
    PASSED("PASSED", "Verification passed."),
    FAILED("FAILED", "Verification failed."),
    SKIPPED("SKIPPED", "Verification was skipped.");

    private final String code;
    private final String info;

    VerificationStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<VerificationStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
