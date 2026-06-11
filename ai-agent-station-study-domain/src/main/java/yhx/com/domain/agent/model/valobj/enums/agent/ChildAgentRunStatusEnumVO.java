package yhx.com.domain.agent.model.valobj.enums.agent;

import java.util.Arrays;
import java.util.Optional;

public enum ChildAgentRunStatusEnumVO {
    PENDING("PENDING", "Child run has been registered but not started."),
    RUNNING("RUNNING", "Child run is executing."),
    WAITING_USER("WAITING_USER", "Child run is paused until the user answers its pending input."),
    COMMITTED("COMMITTED", "Child run committed a result to the parent."),
    FAILED("FAILED", "Child run failed terminally."),
    BLOCKED("BLOCKED", "Child run is blocked terminally for the current wait set.");

    private final String code;
    private final String info;

    ChildAgentRunStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public boolean terminal() {
        return this == COMMITTED || this == FAILED || this == BLOCKED;
    }

    public static Optional<ChildAgentRunStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
