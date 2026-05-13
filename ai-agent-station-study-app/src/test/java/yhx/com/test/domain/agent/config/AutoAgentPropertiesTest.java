package yhx.com.test.domain.agent.config;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.config.AutoAgentCapabilityProperties;
import yhx.com.config.AutoAgentContextProperties;
import yhx.com.config.AutoAgentDebugProperties;
import yhx.com.config.AutoAgentMcpProperties;
import yhx.com.config.AutoAgentNodeProperties;
import yhx.com.config.AutoAgentRagProperties;
import yhx.com.config.AutoAgentRuntimeProperties;

public class AutoAgentPropertiesTest {

    @Test
    public void test_runtimeProperties_haveFailClosedDefaults() {
        AutoAgentRuntimeProperties properties = new AutoAgentRuntimeProperties();

        Assert.assertEquals(8, properties.getMaxLoopCount());
        Assert.assertEquals(1, properties.getMaxContractRepairAttempts());
        Assert.assertEquals(1, properties.getMaxFinalRepairAttempts());
    }

    @Test
    public void test_contextProperties_haveBoundedDefaults() {
        AutoAgentContextProperties properties = new AutoAgentContextProperties();

        Assert.assertTrue(properties.getMaxStateViewChars() > 0);
        Assert.assertTrue(properties.getMaxSnippetChars() > 0);
        Assert.assertTrue(properties.getMaxSelectedArtifacts() > 0);
    }

    @Test
    public void test_debugProperties_areDisabledByDefault() {
        AutoAgentDebugProperties properties = new AutoAgentDebugProperties();

        Assert.assertFalse(properties.isDebugApiEnabled());
        Assert.assertFalse(properties.isDebugSseEnabled());
        Assert.assertFalse(properties.isDebugPayloadPreviewEnabled());
        Assert.assertTrue(properties.getDebugPayloadPreviewMaxChars() > 0);
    }

    @Test
    public void test_otherProperties_canBeCreatedForConfigurationBinding() {
        Assert.assertNotNull(new AutoAgentNodeProperties());
        Assert.assertNotNull(new AutoAgentRagProperties());
        Assert.assertNotNull(new AutoAgentMcpProperties());
        Assert.assertNotNull(new AutoAgentCapabilityProperties());
    }
}
