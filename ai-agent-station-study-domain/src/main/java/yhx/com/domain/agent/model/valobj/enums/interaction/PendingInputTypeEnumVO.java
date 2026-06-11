package yhx.com.domain.agent.model.valobj.enums.interaction;

import java.util.Arrays;
import java.util.Optional;

public enum PendingInputTypeEnumVO {
    CONTEXT_CLARIFICATION("CONTEXT_CLARIFICATION", "ContextPlanner asks user to choose or clarify context."),
    MAIN_AGENT_QUESTION("MAIN_AGENT_QUESTION", "MainAgent asks user for clarification."),
    CHILD_AGENT_QUESTION("CHILD_AGENT_QUESTION", "Child agent asks user for clarification."),
    TOOL_APPROVAL("TOOL_APPROVAL", "Runtime asks user to approve or reject a tool call."),
    RAG_CLARIFICATION("RAG_CLARIFICATION", "RAG flow asks user for query clarification."),
    FINAL_REPAIR_CLARIFICATION("FINAL_REPAIR_CLARIFICATION", "Final repair flow asks user for output clarification.");

    private final String code;
    private final String info;

    PendingInputTypeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<PendingInputTypeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
