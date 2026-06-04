package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auto-agent.context")
public class AutoAgentContextProperties {

    private int maxStateViewChars = 30000;
    private int maxSnippetChars = 1200;
    private int maxSelectedMessages = 20;
    private int maxSelectedArtifacts = 10;
    private int maxSelectedMemories = 15;
    private int maxSelectedEvidence = 15;
    private int maxContextCompression = 2;
    private long vectorRecallTimeoutMillis = 10000;
}
