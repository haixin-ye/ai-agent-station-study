package yhx.com.domain.agent.model.valobj.enums.agent;

import java.util.Arrays;
import java.util.Optional;

public enum SubAgentCommitStatusEnumVO {
    SUCCESS("SUCCESS", "Child task completed successfully."),
    PARTIAL("PARTIAL", "Child task completed partially."),
    BLOCKED("BLOCKED", "Child task is blocked and needs parent decision or user input."),
    FAILED("FAILED", "Child task failed.");

    private final String code;
    private final String info;

    SubAgentCommitStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<SubAgentCommitStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
