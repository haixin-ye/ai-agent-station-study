package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "auto-agent.capabilities")
public class AutoAgentCapabilityProperties {

    private List<CapabilityProperties> tools = new ArrayList<>();

    @Data
    public static class CapabilityProperties {
        private String capabilityId;
        private String capabilityType = "TOOL";
        private String displayName;
        private String mcpServerId;
        private String mcpToolName;
        private String requiredPermission = "NONE";
        private String permissionMode;
        private String approvalPolicy;
        private String riskLevel = "LOW";
        private boolean destructive;
        private String defaultContentMode = "SUMMARY_ONLY";
        private String workspaceScope;
        private Long timeoutMs;
        private boolean enabled = true;
        private Map<String, Object> argumentDefaults;
    }
}
