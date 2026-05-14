package yhx.com.domain.agent.model.valobj.enums.runtime;

import java.util.Arrays;
import java.util.Optional;

public enum ArtifactUpdateModeEnumVO {
    REPLACE_FULL("REPLACE_FULL", "Replace artifact content."),
    PATCH_TEXT("PATCH_TEXT", "Patch artifact text."),
    APPEND("APPEND", "Append content to artifact."),
    CREATE_VERSION("CREATE_VERSION", "Create a new artifact version.");

    private final String code;
    private final String info;

    ArtifactUpdateModeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<ArtifactUpdateModeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
