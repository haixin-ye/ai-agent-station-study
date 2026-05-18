package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auto-agent.rag")
public class AutoAgentRagProperties {

    private boolean enabled = true;
    private String embeddingApiId;
    private String vectorName = "vector_store";
    private String embeddingModelName = "text-embedding-3-large";
    private int dimensions = 1536;
    private int maxQueriesPerRun = 3;
    private int maxHitsPerQuery = 5;
    private int maxEvidenceSnippetChars = 1200;
}
