package yhx.com.domain.agent.model.valobj.enums.agent;

import java.util.Arrays;
import java.util.Optional;

public enum AgentDelegationWaitModeEnumVO {
    WAIT_ALL("WAIT_ALL", "Parent waits until all delegated children are terminal.");

    private final String code;
    private final String info;

    AgentDelegationWaitModeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<AgentDelegationWaitModeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
