package yhx.com.test.config;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.config.AutoAgentContextProperties;

public class AutoAgentContextPropertiesTest {

    @Test
    public void vector_recall_timeout_default_allows_embedding_network_latency() {
        AutoAgentContextProperties properties = new AutoAgentContextProperties();

        Assert.assertTrue(properties.getVectorRecallTimeoutMillis() >= 5000);
    }
}
