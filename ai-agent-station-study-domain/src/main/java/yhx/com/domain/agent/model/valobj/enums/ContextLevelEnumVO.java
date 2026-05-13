package yhx.com.domain.agent.model.valobj.enums;

import java.util.Arrays;
import java.util.Optional;

public enum ContextLevelEnumVO {
    METADATA_ONLY("METADATA_ONLY", "Only metadata is loaded."),
    SUMMARY_ONLY("SUMMARY_ONLY", "Only summary is loaded."),
    SUMMARY_PLUS_SNIPPET("SUMMARY_PLUS_SNIPPET", "Summary plus bounded snippet is loaded."),
    FULL_TEXT("FULL_TEXT", "Full content is loaded when budget allows."),
    CHUNKED_CONTEXT("CHUNKED_CONTEXT", "Chunked content is loaded.");

    private final String code;
    private final String info;

    ContextLevelEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<ContextLevelEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
