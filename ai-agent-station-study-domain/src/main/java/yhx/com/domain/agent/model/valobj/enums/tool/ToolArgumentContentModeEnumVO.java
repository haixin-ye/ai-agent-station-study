package yhx.com.domain.agent.model.valobj.enums.tool;

import java.util.Arrays;
import java.util.Optional;

public enum ToolArgumentContentModeEnumVO {
    METADATA_ONLY("METADATA_ONLY", "Use metadata only."),
    SUMMARY_ONLY("SUMMARY_ONLY", "Use summary only."),
    FULL_TEXT_REQUIRED("FULL_TEXT_REQUIRED", "Full text must be loaded."),
    INLINE_VALUE("INLINE_VALUE", "Use inline value.");

    private final String code;
    private final String info;

    ToolArgumentContentModeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public static Optional<ToolArgumentContentModeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
