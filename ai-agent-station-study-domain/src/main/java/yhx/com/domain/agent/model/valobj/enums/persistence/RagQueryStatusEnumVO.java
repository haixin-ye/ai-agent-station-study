package yhx.com.domain.agent.model.valobj.enums.persistence;

import java.util.Arrays;
import java.util.Optional;

public enum RagQueryStatusEnumVO {
    REQUESTED("REQUESTED", "RAG query was accepted by Runtime."),
    SUCCESS("SUCCESS", "RAG query returned usable hits."),
    NO_HIT("NO_HIT", "RAG query completed without usable hits."),
    FAILED("FAILED", "RAG query failed during retrieval.");

    private final String code;
    private final String info;

    RagQueryStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<RagQueryStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
