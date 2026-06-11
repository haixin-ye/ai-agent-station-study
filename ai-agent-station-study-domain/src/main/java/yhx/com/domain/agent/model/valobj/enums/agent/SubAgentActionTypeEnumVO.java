package yhx.com.domain.agent.model.valobj.enums.agent;

import java.util.Arrays;
import java.util.Optional;

public enum SubAgentActionTypeEnumVO {
    CALL_TOOL("CALL_TOOL", "Request runtime-owned tool execution."),
    RETRIEVE_RAG("RETRIEVE_RAG", "Request RAG retrieval."),
    ASK_USER("ASK_USER", "Ask the user through shared pending input."),
    CONTINUE("CONTINUE", "Continue the subagent loop."),
    COMMIT("COMMIT", "Return structured work result to parent agent."),
    FAIL("FAIL", "Return structured failure to parent agent.");

    private final String code;
    private final String info;

    SubAgentActionTypeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<SubAgentActionTypeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
