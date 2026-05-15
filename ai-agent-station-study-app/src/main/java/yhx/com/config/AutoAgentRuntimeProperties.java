package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auto-agent.runtime")
public class AutoAgentRuntimeProperties {

    private boolean enabled = true;
    private int maxLoopCount = 8;
    private int maxContractRepairAttempts = 1;
    private int maxFinalRepairAttempts = 1;
    private int maxConsecutiveContinueActions = 1;
    private int maxRecoveryAttemptsPerFailureCode = 2;
}
