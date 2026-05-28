package yhx.com.test.domain.agent.config;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;
import yhx.com.config.AutoAgentMcpProperties;
import yhx.com.config.AutoAgentToolConfig;
import yhx.com.domain.agent.service.tool.McpClientRegistry;

public class AutoAgentToolConfigTest {

    @Test
    public void mcpClientRegistry_doesNotStartDisabledConfiguredServers() {
        AutoAgentMcpProperties properties = new AutoAgentMcpProperties();
        AutoAgentMcpProperties.McpServerProperties server = new AutoAgentMcpProperties.McpServerProperties();
        server.setServerId("file-system");
        server.setTransport("STDIO");
        server.setCommand("npx");
        server.setEnabled(false);
        properties.getServers().add(server);

        McpClientRegistry registry = new AutoAgentToolConfig()
                .mcpClientRegistry(properties, new StaticApplicationContext());

        Assert.assertFalse(registry.hasClient("file-system"));
    }

    @Test
    public void mcpClientRegistry_isEmptyWhenMcpIsGloballyDisabled() {
        AutoAgentMcpProperties properties = new AutoAgentMcpProperties();
        properties.setEnabled(false);

        McpClientRegistry registry = new AutoAgentToolConfig()
                .mcpClientRegistry(properties, new StaticApplicationContext());

        Assert.assertFalse(registry.hasClient("any"));
    }
}
