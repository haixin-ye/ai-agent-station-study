package yhx.com.domain.agent.model.valobj.enums.agent;

import java.util.Arrays;
import java.util.Optional;

public enum AgentProfileTypeEnumVO {
    MAIN_AGENT("MAIN_AGENT", "User-facing orchestrator agent."),
    GENERIC_SUB_AGENT("GENERIC_SUB_AGENT", "Temporary delegated worker agent."),
    CODE_AGENT_BRIDGE("CODE_AGENT_BRIDGE", "Reserved bridge profile for future workspace code agent.");

    private final String code;
    private final String info;

    AgentProfileTypeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<AgentProfileTypeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
