package yhx.com.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "auto-agent.executors")
public class AutoAgentExecutorProperties {

    private PoolProperties agentExecution;
    private PoolProperties sse;
    private PoolProperties contextRecall;
    private PoolProperties memory;
    private PoolProperties mcp;

    public void validate() {
        validatePool("agent-execution", agentExecution);
        validatePool("sse", sse);
        validatePool("context-recall", contextRecall);
        validatePool("memory", memory);
        validatePool("mcp", mcp);
    }

    private void validatePool(String name, PoolProperties pool) {
        String path = "auto-agent.executors." + name;
        require(pool != null, path + " is required");
        require(pool.getCorePoolSize() >= 1, path + ".core-pool-size must be >= 1");
        require(pool.getMaxPoolSize() >= pool.getCorePoolSize(),
                path + ".max-pool-size must be >= core-pool-size");
        require(pool.getQueueCapacity() >= 0, path + ".queue-capacity must be >= 0");
        require(nonNegative(pool.getKeepAlive()), path + ".keep-alive must be non-negative");
        require(pool.getThreadNamePrefix() != null && !pool.getThreadNamePrefix().isBlank(),
                path + ".thread-name-prefix is required");
        require(pool.getRejectionPolicy() != null, path + ".rejection-policy is required");
        require(nonNegative(pool.getAwaitTermination()), path + ".await-termination must be non-negative");
    }

    private boolean nonNegative(Duration duration) {
        return duration != null && !duration.isNegative();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @Data
    public static class PoolProperties {
        private int corePoolSize;
        private int maxPoolSize;
        private int queueCapacity;
        private Duration keepAlive;
        private String threadNamePrefix;
        private RejectionPolicy rejectionPolicy;
        private boolean allowCoreThreadTimeout;
        private boolean waitForTasksToCompleteOnShutdown;
        private Duration awaitTermination;
    }

    public enum RejectionPolicy {
        ABORT,
        CALLER_RUNS,
        DISCARD,
        DISCARD_OLDEST
    }
}
