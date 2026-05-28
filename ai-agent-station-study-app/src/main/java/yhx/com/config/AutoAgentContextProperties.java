package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auto-agent.context")
public class AutoAgentContextProperties {

    private int maxStateViewChars = 24000;
    private int maxSnippetChars = 1200;
    private int maxSelectedMessages = 12;
    private int maxSelectedArtifacts = 4;
    private int maxSelectedMemories = 8;
    private int maxSelectedEvidence = 8;
    private int maxContextCompression = 2;
    private long vectorRecallTimeoutMillis = 8000;
}
