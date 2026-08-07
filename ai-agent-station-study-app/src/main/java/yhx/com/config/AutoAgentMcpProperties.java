package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "auto-agent.mcp")
public class AutoAgentMcpProperties {

    private boolean enabled = true;
    private List<McpServerProperties> servers = new ArrayList<>();

    @Data
    public static class McpServerProperties {
        private String serverId;
        private String name;
        private String transport;
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new LinkedHashMap<>();
        private String url;
        private String sseEndpoint;
        private Map<String, String> headers = new LinkedHashMap<>();
        private String bearerToken;
        private String apiKey;
        private String apiKeyHeader = "X-API-Key";
        private long requestTimeoutSeconds = 100;
        private boolean autoInitialize = true;
        private boolean autoDiscoverTools = true;
        private boolean autoRegisterCapabilities = false;
        private String capabilityPrefix;
        private String defaultRequiredPermission = "NONE";
        private String defaultPermissionMode = "ASK_USER";
        private String defaultApprovalPolicy = "ASK_USER_BEFORE_EXECUTE";
        private String defaultRiskLevel = "MEDIUM";
        private boolean defaultDestructive;
        private String defaultContentMode = "SUMMARY_ONLY";
        private String defaultResultContentMode = "SUMMARY_ONLY";
        private String workspaceScope;
        private Long timeoutMs;
        private boolean enabled = true;
        private List<McpToolProperties> tools = new ArrayList<>();
    }

    @Data
    public static class McpToolProperties {
        private String toolName;
        private String description;
        private String inputSchemaRef;
        private Map<String, Object> inputSchema;
        private String requiredPermission;
        private String riskLevel;
        private Boolean destructive;
        private Boolean schemaLessAllowed;
    }
}
