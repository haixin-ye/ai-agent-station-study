package yhx.com.test.domain.agent.config;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import yhx.com.config.AutoAgentCapabilityProperties;
import yhx.com.config.AutoAgentMcpProperties;
import yhx.com.config.AutoAgentToolConfig;
import yhx.com.domain.agent.model.valobj.enums.tool.ApprovalPolicyEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.RequiredPermissionEnumVO;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;
import yhx.com.domain.agent.service.tool.CapabilityRegistry;
import yhx.com.domain.agent.service.tool.McpClientRegistry;
import yhx.com.domain.agent.service.tool.McpToolRegistry;
import yhx.com.domain.agent.service.tool.port.McpToolDiscoveryPort;
import yhx.com.domain.agent.model.valobj.tool.McpRuntimeCatalogVO;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    @Test
    public void application_dev_exposes_configured_file_system_capabilities() throws IOException {
        StandardEnvironment environment = applicationDevEnvironment();
        AutoAgentMcpProperties mcpProperties = Binder.get(environment)
                .bind("auto-agent.mcp", AutoAgentMcpProperties.class)
                .orElseThrow(() -> new AssertionError("auto-agent.mcp should bind from application-dev.yml"));
        AutoAgentCapabilityProperties capabilityProperties = Binder.get(environment)
                .bind("auto-agent.capabilities", AutoAgentCapabilityProperties.class)
                .orElseThrow(() -> new AssertionError("auto-agent.capabilities should bind from application-dev.yml"));
        AutoAgentToolConfig config = new AutoAgentToolConfig();
        McpRuntimeCatalogVO catalog = config.autoAgentMcpRuntimeCatalog(mcpProperties, serverId -> List.of(McpToolSpecVO.builder()
                .mcpServerCode(serverId)
                .toolName("JavaSDKMCPClient_list_allowed_directories")
                .description("Spring AI wrapper name that must not become a public capability.")
                .inputSchema(Map.of("type", "object"))
                .build()));

        McpToolRegistry toolRegistry = config.mcpToolRegistry(catalog);
        CapabilityRegistry capabilityRegistry = config.capabilityRegistry(capabilityProperties, mcpProperties, catalog);

        McpToolSpecVO writeTool = toolRegistry.requireTool("file-system", "write_file");
        Assert.assertEquals(RequiredPermissionEnumVO.WORKSPACE_WRITE, writeTool.getRequiredPermission());
        Assert.assertEquals("HIGH", writeTool.getRiskLevel());

        assertWriteCapability(capabilityRegistry.requireCapability("file_system_create_file"));
        assertWriteCapability(capabilityRegistry.requireCapability("file_system_write_file"));
        assertReadCapability(capabilityRegistry.requireCapability("file_system_read_multiple_files"), "read_multiple_files");
        assertReadCapability(capabilityRegistry.requireCapability("file_system_directory_tree"), "directory_tree");
        assertReadCapability(capabilityRegistry.requireCapability("file_system_get_file_info"), "get_file_info");
        assertReadCapability(capabilityRegistry.requireCapability("file_system_list_allowed_directories"), "list_allowed_directories");
        assertWriteCapability(capabilityRegistry.requireCapability("file_system_edit_file"), "edit_file");
        assertWriteCapability(capabilityRegistry.requireCapability("file_system_create_directory"), "create_directory");
        assertWriteCapability(capabilityRegistry.requireCapability("file_system_move_file"), "move_file");
        Assert.assertTrue(capabilityRegistry.findCapability("file_system_javasdkmcpclient_list_allowed_directories").isEmpty());
    }

    @Test
    public void discovered_mcp_tools_are_catalogued_but_not_registered_as_default_capabilities() {
        AutoAgentMcpProperties mcpProperties = new AutoAgentMcpProperties();
        AutoAgentMcpProperties.McpServerProperties server = new AutoAgentMcpProperties.McpServerProperties();
        server.setServerId("baidu-ai-search");
        server.setTransport("SSE");
        server.setEnabled(true);
        server.setAutoDiscoverTools(true);
        server.setAutoRegisterCapabilities(false);
        mcpProperties.getServers().add(server);
        McpToolDiscoveryPort discoveryPort = serverId -> List.of(McpToolSpecVO.builder()
                .mcpServerCode(serverId)
                .toolName("ai_search")
                .description("Search with Baidu AI.")
                .inputSchema(Map.of("type", "object"))
                .build());
        AutoAgentToolConfig config = new AutoAgentToolConfig();

        McpRuntimeCatalogVO catalog = config.autoAgentMcpRuntimeCatalog(mcpProperties, discoveryPort);
        McpToolRegistry toolRegistry = config.mcpToolRegistry(catalog);
        CapabilityRegistry capabilityRegistry = config.capabilityRegistry(new AutoAgentCapabilityProperties(), mcpProperties, catalog);

        Assert.assertEquals("ai_search", toolRegistry.requireTool("baidu-ai-search", "ai_search").getToolName());
        Assert.assertTrue(capabilityRegistry.findCapability("baidu_ai_search_ai_search").isEmpty());
    }

    private void assertWriteCapability(CapabilitySpecVO capability) {
        assertWriteCapability(capability, "write_file");
    }

    private void assertWriteCapability(CapabilitySpecVO capability, String toolName) {
        Assert.assertEquals("file-system", capability.getMcpServerCode());
        Assert.assertEquals(toolName, capability.getToolName());
        Assert.assertEquals(RequiredPermissionEnumVO.WORKSPACE_WRITE, capability.getRequiredPermission());
        Assert.assertEquals(PermissionModeEnumVO.ASK_USER, capability.getPermissionMode());
        Assert.assertEquals(ApprovalPolicyEnumVO.ASK_USER_BEFORE_EXECUTE, capability.getApprovalPolicy());
        Assert.assertEquals("HIGH", capability.getRiskLevel());
    }

    private void assertReadCapability(CapabilitySpecVO capability, String toolName) {
        Assert.assertEquals("file-system", capability.getMcpServerCode());
        Assert.assertEquals(toolName, capability.getToolName());
        Assert.assertEquals(RequiredPermissionEnumVO.READ_ONLY, capability.getRequiredPermission());
        Assert.assertEquals(PermissionModeEnumVO.ALLOW, capability.getPermissionMode());
        Assert.assertEquals(ApprovalPolicyEnumVO.NEVER, capability.getApprovalPolicy());
        Assert.assertEquals("LOW", capability.getRiskLevel());
    }

    private StandardEnvironment applicationDevEnvironment() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        List<org.springframework.core.env.PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load("application-dev", new ClassPathResource("application-dev.yml"));
        loaded.forEach(sources::addFirst);
        return environment;
    }
}
