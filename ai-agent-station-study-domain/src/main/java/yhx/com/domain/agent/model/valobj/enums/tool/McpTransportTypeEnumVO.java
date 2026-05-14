package yhx.com.domain.agent.model.valobj.enums.tool;

import java.util.Arrays;
import java.util.Optional;

public enum McpTransportTypeEnumVO {
    SSE("SSE", "Server-sent events transport."),
    STDIO("STDIO", "Stdio transport."),
    UNKNOWN("UNKNOWN", "Unknown transport.");

    private final String code;
    private final String info;

    McpTransportTypeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public static Optional<McpTransportTypeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
