package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "auto-agent.nodes")
public class AutoAgentNodeProperties {

    private Map<String, NodeModelProperties> models = new HashMap<>();

    @Data
    public static class NodeModelProperties {
        private String modelCode;
        private String modelName;
        private String promptVersion = "v1";
        private Double temperature;
        private Integer maxOutputTokens;
        private Integer maxRepairAttempts;
        private Integer timeoutSeconds;
    }
}
