package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auto-agent.debug")
public class AutoAgentDebugProperties {

    private boolean debugApiEnabled = false;
    private boolean debugSseEnabled = false;
    private boolean debugPayloadPreviewEnabled = false;
    private int debugPayloadPreviewMaxChars = 2000;
}
