package yhx.com.domain.agent.model.valobj.enums;

import java.util.Arrays;
import java.util.Optional;

public enum MainAgentActionTypeEnumVO {
    FINAL("FINAL", "Return a guarded final answer candidate."),
    CREATE_ARTIFACT("CREATE_ARTIFACT", "Create a new artifact."),
    UPDATE_ARTIFACT("UPDATE_ARTIFACT", "Update an existing artifact."),
    RETRIEVE_RAG("RETRIEVE_RAG", "Request RAG retrieval."),
    CALL_TOOL("CALL_TOOL", "Request runtime-owned tool execution."),
    ASK_USER("ASK_USER", "Ask the user for clarification or approval."),
    PLAN("PLAN", "Persist internal plan state."),
    CONTINUE("CONTINUE", "Continue the loop."),
    REPAIR_FINAL("REPAIR_FINAL", "Return a repaired final answer candidate."),
    FAIL("FAIL", "Return a user-safe failure candidate.");

    private final String code;
    private final String info;

    MainAgentActionTypeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<MainAgentActionTypeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
