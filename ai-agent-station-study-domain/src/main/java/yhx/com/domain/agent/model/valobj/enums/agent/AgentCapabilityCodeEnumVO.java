package yhx.com.domain.agent.model.valobj.enums.agent;

import java.util.Arrays;
import java.util.Optional;

public enum AgentCapabilityCodeEnumVO {
    RAG("RAG", "May request RAG retrieval."),
    MCP_TOOL("MCP_TOOL", "May call allowed MCP tools."),
    FILE_READ("FILE_READ", "May read files inside effective workspace scope."),
    FILE_WRITE("FILE_WRITE", "May write files inside effective workspace scope after policy checks."),
    ASK_USER("ASK_USER", "May create pending input."),
    DELEGATE_AGENTS("DELEGATE_AGENTS", "May create generic subagents."),
    DELEGATE_CODE_AGENT("DELEGATE_CODE_AGENT", "Reserved for future CodeAgent bridge."),
    COMMIT("COMMIT", "May return work result to parent."),
    FINAL("FINAL", "May produce final user-facing answer.");

    private final String code;
    private final String info;

    AgentCapabilityCodeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<AgentCapabilityCodeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
