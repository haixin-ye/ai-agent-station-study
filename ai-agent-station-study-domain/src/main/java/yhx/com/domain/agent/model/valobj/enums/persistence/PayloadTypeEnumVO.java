package yhx.com.domain.agent.model.valobj.enums.persistence;

import java.util.Arrays;
import java.util.Optional;

public enum PayloadTypeEnumVO {
    TEXT("TEXT", "Plain text payload."),
    JSON("JSON", "Structured JSON payload."),
    STATE_SNAPSHOT("STATE_SNAPSHOT", "Runtime state snapshot payload."),
    TRANSCRIPT_BLOCK("TRANSCRIPT_BLOCK", "Typed transcript block payload."),
    ARTIFACT_CONTENT("ARTIFACT_CONTENT", "Artifact content payload."),
    RAG_CHUNK("RAG_CHUNK", "Raw bounded RAG chunk payload."),
    RAG_EVIDENCE("RAG_EVIDENCE", "RAG evidence payload."),
    TOOL_RECEIPT("TOOL_RECEIPT", "Raw or normalized tool receipt payload."),
    DEBUG_TRACE("DEBUG_TRACE", "Developer-only debug trace payload."),
    PROMPT_CONTENT("PROMPT_CONTENT", "Editable node prompt content payload.");

    private final String code;
    private final String info;

    PayloadTypeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<PayloadTypeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
