package yhx.com.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(AutoAgentExecutorProperties.class)
public class ThreadPoolConfig {

    private final AutoAgentExecutorProperties properties;

    public ThreadPoolConfig(AutoAgentExecutorProperties properties) {
        this.properties = Objects.requireNonNull(properties, "AutoAgentExecutorProperties is required.");
        this.properties.validate();
    }

    @Bean("autoAgentExecutionExecutor")
    public ThreadPoolTaskExecutor autoAgentExecutionExecutor() {
        return executor(properties.getAgentExecution());
    }

    @Bean("autoAgentSseExecutor")
    public ThreadPoolTaskExecutor autoAgentSseExecutor() {
        return executor(properties.getSse());
    }

    @Bean("autoAgentContextRecallExecutor")
    public ThreadPoolTaskExecutor autoAgentContextRecallExecutor() {
        return executor(properties.getContextRecall());
    }

    @Bean("autoAgentMemoryTaskExecutor")
    public ThreadPoolTaskExecutor autoAgentMemoryTaskExecutor() {
        return executor(properties.getMemory());
    }

    @Bean("autoAgentMcpExecutor")
    public ThreadPoolTaskExecutor autoAgentMcpExecutor() {
        return executor(properties.getMcp());
    }

    private ThreadPoolTaskExecutor executor(AutoAgentExecutorProperties.PoolProperties pool) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(pool.getCorePoolSize());
        executor.setMaxPoolSize(pool.getMaxPoolSize());
        executor.setQueueCapacity(pool.getQueueCapacity());
        executor.setKeepAliveSeconds(Math.toIntExact(pool.getKeepAlive().toSeconds()));
        executor.setThreadNamePrefix(pool.getThreadNamePrefix());
        executor.setAllowCoreThreadTimeOut(pool.isAllowCoreThreadTimeout());
        executor.setWaitForTasksToCompleteOnShutdown(pool.isWaitForTasksToCompleteOnShutdown());
        executor.setAwaitTerminationSeconds(Math.toIntExact(pool.getAwaitTermination().toSeconds()));
        executor.setRejectedExecutionHandler(rejectionHandler(pool.getRejectionPolicy()));
        return executor;
    }

    private RejectedExecutionHandler rejectionHandler(AutoAgentExecutorProperties.RejectionPolicy policy) {
        return switch (Objects.requireNonNull(policy, "Executor rejection policy is required.")) {
            case ABORT -> new ThreadPoolExecutor.AbortPolicy();
            case CALLER_RUNS -> new ThreadPoolExecutor.CallerRunsPolicy();
            case DISCARD -> new ThreadPoolExecutor.DiscardPolicy();
            case DISCARD_OLDEST -> new ThreadPoolExecutor.DiscardOldestPolicy();
        };
    }
}
