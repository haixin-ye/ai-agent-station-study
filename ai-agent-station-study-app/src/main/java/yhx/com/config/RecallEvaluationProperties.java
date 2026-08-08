package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auto-agent.recall-evaluation")
public class RecallEvaluationProperties {
    private int corePoolSize = 2;
    private int maxPoolSize = 4;
    private int queueCapacity = 16;
    private int maxBatchItems = 500;
    private long maxUploadBytes = 10 * 1024 * 1024L;
}
