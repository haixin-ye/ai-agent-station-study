package yhx.com.domain.agent.model.valobj.enums.tool;

import java.util.Arrays;
import java.util.Optional;

public enum ToolResultContentModeEnumVO {
    METADATA_ONLY,
    SUMMARY_ONLY,
    FULL_TEXT_REQUIRED;

    public String code() {
        return name();
    }

    public static Optional<ToolResultContentModeEnumVO> ofCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(code))
                .findFirst();
    }
}
