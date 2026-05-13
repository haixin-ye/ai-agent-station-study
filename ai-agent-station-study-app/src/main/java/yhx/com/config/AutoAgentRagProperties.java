package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auto-agent.rag")
public class AutoAgentRagProperties {

    private boolean enabled = true;
    private int maxQueriesPerRun = 3;
    private int maxHitsPerQuery = 5;
    private int maxEvidenceSnippetChars = 1200;
}
