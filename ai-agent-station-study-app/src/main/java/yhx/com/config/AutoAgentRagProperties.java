package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auto-agent.rag")
public class AutoAgentRagProperties {

    private boolean enabled = true;
    private String vectorName = "vector_store";
    private int maxQueriesPerRun = 3;
    private int maxHitsPerQuery = 5;
    private int maxEvidenceSnippetChars = 1200;
    private Asset asset = new Asset();

    @Data
    public static class Asset {
        private int recallTopK = 8;
        private double recallMinScore = 0.2;
        private long recallTimeoutMillis = 1200;
        private int chunkMaxChars = 512;
        private int chunkOverlapChars = 100;
    }
}
