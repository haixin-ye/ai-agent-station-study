package yhx.com.test.domain.agent.config;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import yhx.com.config.ThreadPoolConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class AutoAgentExecutorConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ThreadPoolConfig.class)
            .withPropertyValues(properties());

    private String[] properties() {
        return Stream.of(
                    pool("agent-execution", 2, 4, 6, "auto-agent-exec-", "CALLER_RUNS"),
                    pool("sse", 1, 2, 5, "auto-agent-sse-", "ABORT"),
                    pool("context-recall", 2, 2, 4, "auto-agent-context-", "CALLER_RUNS"),
                    pool("memory", 1, 1, 3, "auto-agent-memory-", "ABORT"),
                    pool("mcp", 1, 2, 2, "auto-agent-mcp-", "ABORT"))
                .flatMap(Stream::of)
                .toArray(String[]::new);
    }

    @Test
    public void creates_five_named_lifecycle_managed_executors() {
        contextRunner.run(context -> {
            Map<String, String> expectedPrefixes = Map.of(
                    "autoAgentExecutionExecutor", "auto-agent-exec-",
                    "autoAgentSseExecutor", "auto-agent-sse-",
                    "autoAgentContextRecallExecutor", "auto-agent-context-",
                    "autoAgentMemoryTaskExecutor", "auto-agent-memory-",
                    "autoAgentMcpExecutor", "auto-agent-mcp-");

            for (Map.Entry<String, String> entry : expectedPrefixes.entrySet()) {
                ThreadPoolTaskExecutor executor = context.getBean(entry.getKey(), ThreadPoolTaskExecutor.class);
                Assert.assertTrue(executeAndCaptureThreadName(executor).startsWith(entry.getValue()));
            }

            ThreadPoolTaskExecutor agent = context.getBean("autoAgentExecutionExecutor", ThreadPoolTaskExecutor.class);
            Assert.assertEquals(2, agent.getCorePoolSize());
            Assert.assertEquals(4, agent.getMaxPoolSize());
            Assert.assertEquals(6, agent.getThreadPoolExecutor().getQueue().remainingCapacity());
        });
    }

    private String[] pool(String name, int core, int max, int queue, String prefix, String rejection) {
        return List.of(
                property(name, "core-pool-size", core),
                property(name, "max-pool-size", max),
                property(name, "queue-capacity", queue),
                property(name, "keep-alive", "30s"),
                property(name, "thread-name-prefix", prefix),
                property(name, "rejection-policy", rejection),
                property(name, "allow-core-thread-timeout", false),
                property(name, "wait-for-tasks-to-complete-on-shutdown", true),
                property(name, "await-termination", "5s"))
                .toArray(String[]::new);
    }

    private String property(String pool, String key, Object value) {
        return "auto-agent.executors." + pool + "." + key + "=" + value;
    }

    private String executeAndCaptureThreadName(ThreadPoolTaskExecutor executor) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        executor.execute(() -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
        });
        Assert.assertTrue(latch.await(2, TimeUnit.SECONDS));
        return threadName.get();
    }
}
