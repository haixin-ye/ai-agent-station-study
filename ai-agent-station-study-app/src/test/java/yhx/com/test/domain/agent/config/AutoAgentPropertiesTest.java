package yhx.com.test.domain.agent.config;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.config.AutoAgentCapabilityProperties;
import yhx.com.config.AutoAgentContextProperties;
import yhx.com.config.AutoAgentDebugProperties;
import yhx.com.config.AutoAgentMcpProperties;
import yhx.com.config.AutoAgentRagProperties;
import yhx.com.config.AutoAgentRuntimeProperties;

public class AutoAgentPropertiesTest {

    @Test
    public void test_runtimeProperties_haveFailClosedDefaults() {
        AutoAgentRuntimeProperties properties = new AutoAgentRuntimeProperties();

        Assert.assertEquals(8, properties.getMaxLoopCount());
        Assert.assertEquals(1, properties.getMaxContractRepairAttempts());
        Assert.assertEquals(1, properties.getMaxFinalRepairAttempts());
        Assert.assertTrue(properties.isFinalResponseGuardEnabled());
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
        Assert.assertNotNull(new AutoAgentRagProperties());
        Assert.assertNotNull(new AutoAgentMcpProperties());
        Assert.assertNotNull(new AutoAgentCapabilityProperties());
    }

    @Test
    public void test_mcpServerProperties_supportStdioAndSseClientBindingFields() {
        AutoAgentMcpProperties.McpServerProperties server = new AutoAgentMcpProperties.McpServerProperties();

        server.setCommand("npx");
        server.getArgs().add("-y");
        server.getEnv().put("API_KEY", "test");
        server.setUrl("http://localhost:3000/mcp");
        server.setSseEndpoint("sse?api_key=test");

        Assert.assertEquals("npx", server.getCommand());
        Assert.assertEquals("-y", server.getArgs().get(0));
        Assert.assertEquals("test", server.getEnv().get("API_KEY"));
        Assert.assertEquals("http://localhost:3000/mcp", server.getUrl());
        Assert.assertEquals("sse?api_key=test", server.getSseEndpoint());
        Assert.assertEquals(100, server.getRequestTimeoutSeconds());
        Assert.assertTrue(server.isAutoInitialize());
    }
}
