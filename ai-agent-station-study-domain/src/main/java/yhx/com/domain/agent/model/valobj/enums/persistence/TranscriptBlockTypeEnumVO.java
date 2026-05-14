package yhx.com.domain.agent.model.valobj.enums.persistence;

import java.util.Arrays;
import java.util.Optional;

public enum TranscriptBlockTypeEnumVO {
    USER_MESSAGE("USER_MESSAGE", "User message block."),
    ASSISTANT_MESSAGE("ASSISTANT_MESSAGE", "Assistant message block."),
    RUNTIME_EVENT("RUNTIME_EVENT", "Runtime event block."),
    TOOL_RECEIPT("TOOL_RECEIPT", "Tool receipt block."),
    RAG_EVIDENCE("RAG_EVIDENCE", "RAG evidence block."),
    COMPACTION_SUMMARY("COMPACTION_SUMMARY", "Compaction summary block.");

    private final String code;
    private final String info;

    TranscriptBlockTypeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<TranscriptBlockTypeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
