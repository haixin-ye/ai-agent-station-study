package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "auto-agent.capabilities")
public class AutoAgentCapabilityProperties {

    private List<CapabilityProperties> tools = new ArrayList<>();

    @Data
    public static class CapabilityProperties {
        private String capabilityId;
        private String displayName;
        private String mcpServerId;
        private String mcpToolName;
        private String permissionMode;
        private String approvalPolicy;
        private boolean enabled = true;
    }
}
