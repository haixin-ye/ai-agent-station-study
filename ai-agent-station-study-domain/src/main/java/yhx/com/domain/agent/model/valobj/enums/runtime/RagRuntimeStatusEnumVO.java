package yhx.com.domain.agent.model.valobj.enums.runtime;

import java.util.Arrays;
import java.util.Optional;

public enum RagRuntimeStatusEnumVO {
    SUCCESS("SUCCESS", "RAG retrieval produced evidence."),
    NO_HIT("NO_HIT", "RAG retrieval found no matching evidence but can continue."),
    FAILED("FAILED", "RAG retrieval failed.");

    private final String code;
    private final String info;

    RagRuntimeStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<RagRuntimeStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
