package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auto-agent.legacy")
public class AutoAgentLegacyProperties {

    private boolean enabled = false;
    private boolean compareApiEnabled = false;
}

