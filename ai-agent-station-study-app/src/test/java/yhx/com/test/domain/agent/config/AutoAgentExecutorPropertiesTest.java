package yhx.com.test.domain.agent.config;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import yhx.com.config.AutoAgentExecutorProperties;

import java.io.IOException;
import java.time.Duration;

public class AutoAgentExecutorPropertiesTest {

    @Test
    public void application_dev_binds_all_named_executor_specs() throws IOException {
        AutoAgentExecutorProperties properties = bind("application-dev.yml");

        properties.validate();
        Assert.assertEquals(4, properties.getAgentExecution().getCorePoolSize());
        Assert.assertEquals(8, properties.getAgentExecution().getMaxPoolSize());
        Assert.assertEquals(Duration.ofSeconds(60), properties.getAgentExecution().getKeepAlive());
        Assert.assertEquals("auto-agent-exec-", properties.getAgentExecution().getThreadNamePrefix());
        Assert.assertEquals(AutoAgentExecutorProperties.RejectionPolicy.CALLER_RUNS,
                properties.getAgentExecution().getRejectionPolicy());
        Assert.assertNotNull(properties.getSse());
        Assert.assertEquals(0, properties.getSse().getQueueCapacity());
        Assert.assertNotNull(properties.getContextRecall());
        Assert.assertNotNull(properties.getMemory());
        Assert.assertNotNull(properties.getMcp());
    }

    @Test
    public void production_and_test_profiles_bind_complete_executor_specs() throws IOException {
        AutoAgentExecutorProperties production = bind("application-prod.yml");
        AutoAgentExecutorProperties test = bind("application-test.yml");

        production.validate();
        test.validate();
        Assert.assertEquals(4, production.getMemory().getCorePoolSize());
        Assert.assertEquals(2, test.getAgentExecution().getCorePoolSize());
        Assert.assertEquals(1, test.getMemory().getMaxPoolSize());
    }

    @Test
    public void validation_rejects_invalid_pool_bounds_with_property_path() {
        AutoAgentExecutorProperties properties = validProperties();
        properties.getMcp().setMaxPoolSize(1);
        properties.getMcp().setCorePoolSize(2);

        IllegalStateException error = Assert.assertThrows(IllegalStateException.class, properties::validate);

        Assert.assertTrue(error.getMessage().contains("auto-agent.executors.mcp.max-pool-size"));
    }

    private AutoAgentExecutorProperties validProperties() {
        AutoAgentExecutorProperties properties = new AutoAgentExecutorProperties();
        properties.setAgentExecution(pool("exec-"));
        properties.setSse(pool("sse-"));
        properties.setContextRecall(pool("context-"));
        properties.setMemory(pool("memory-"));
        properties.setMcp(pool("mcp-"));
        return properties;
    }

    private AutoAgentExecutorProperties bind(String resource) throws IOException {
        MockEnvironment environment = new MockEnvironment();
        new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource))
                .forEach(source -> environment.getPropertySources().addLast(source));
        return Binder.get(environment)
                .bind("auto-agent.executors", AutoAgentExecutorProperties.class)
                .orElseThrow(() -> new AssertionError("auto-agent.executors should bind from " + resource));
    }

    private AutoAgentExecutorProperties.PoolProperties pool(String prefix) {
        AutoAgentExecutorProperties.PoolProperties pool = new AutoAgentExecutorProperties.PoolProperties();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(4);
        pool.setKeepAlive(Duration.ofSeconds(30));
        pool.setThreadNamePrefix(prefix);
        pool.setRejectionPolicy(AutoAgentExecutorProperties.RejectionPolicy.ABORT);
        pool.setAwaitTermination(Duration.ofSeconds(5));
        return pool;
    }
}
