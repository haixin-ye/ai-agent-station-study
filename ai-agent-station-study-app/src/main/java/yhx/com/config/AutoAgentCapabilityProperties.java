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
    private PromptExposureProperties promptExposure = new PromptExposureProperties();

    @Data
    public static class PromptExposureProperties {
        private int maxTools = 32;
        private int maxDescriptionChars = 300;
        private int maxSchemaDepth = 5;
        private int maxSchemaPropertiesPerTool = 40;
        private int maxSchemaCharsPerTool = 2400;
        private int maxTotalSchemaChars = 12000;
        private int maxRequiredArgumentsPerTool = 64;
        private int maxCapabilityCharsPerTool = 3200;
        private int maxTotalCapabilityChars = 10000;
    }

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
        private String resultContentMode = "SUMMARY_ONLY";
        private String workspaceScope;
        private Long timeoutMs;
        private boolean enabled = true;
        private Map<String, Object> argumentDefaults;
    }
}
