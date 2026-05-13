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
        private String modelName;
        private Double temperature;
        private Integer maxOutputTokens;
        private Integer timeoutSeconds;
    }
}
