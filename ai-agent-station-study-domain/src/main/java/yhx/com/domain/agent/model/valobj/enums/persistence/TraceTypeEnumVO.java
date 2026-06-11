package yhx.com.domain.agent.model.valobj.enums.persistence;

import java.util.Arrays;
import java.util.Optional;

public enum TraceTypeEnumVO {
    NODE_INPUT("NODE_INPUT", "LLM node input trace."),
    NODE_OUTPUT("NODE_OUTPUT", "LLM node output trace."),
    CONTRACT_VALIDATION("CONTRACT_VALIDATION", "Contract validation trace."),
    RUNTIME_DECISION("RUNTIME_DECISION", "Runtime decision trace."),
    TOOL_RECEIPT("TOOL_RECEIPT", "Tool receipt trace."),
    FINAL_GUARD("FINAL_GUARD", "Final response guard trace.");

    private final String code;
    private final String info;

    TraceTypeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<TraceTypeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
