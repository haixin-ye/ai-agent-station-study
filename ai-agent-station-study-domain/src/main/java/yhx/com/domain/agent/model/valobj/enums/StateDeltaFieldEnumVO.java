package yhx.com.domain.agent.model.valobj.enums;

import java.util.Arrays;
import java.util.Optional;

public enum StateDeltaFieldEnumVO {
    FINAL_ANSWER_CANDIDATE("finalAnswerCandidate", "Candidate final answer."),
    ARTIFACT_DRAFT("artifactDraft", "Artifact draft to create."),
    ARTIFACT_PATCH("artifactPatch", "Artifact patch to apply."),
    RAG_REQUEST("ragRequest", "RAG retrieval request."),
    TOOL_INTENT("toolIntent", "Tool execution intent."),
    ASK_USER_REQUEST("askUserRequest", "User interaction request."),
    PLAN_DRAFT("planDraft", "Internal plan draft."),
    NEXT_ACTION_HINT("nextActionHint", "Hint for next loop."),
    FAILURE("failure", "User-safe failure data.");

    private final String code;
    private final String info;

    StateDeltaFieldEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<StateDeltaFieldEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
