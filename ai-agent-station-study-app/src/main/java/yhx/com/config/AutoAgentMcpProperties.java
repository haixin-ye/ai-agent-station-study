package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
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
        private String url;
        private boolean enabled = true;
        private List<McpToolProperties> tools = new ArrayList<>();
    }

    @Data
    public static class McpToolProperties {
        private String toolName;
        private String description;
        private String inputSchemaRef;
        private Map<String, Object> inputSchema;
        private String requiredPermission = "NONE";
        private String riskLevel = "LOW";
        private boolean destructive;
        private boolean schemaLessAllowed;
    }
}
