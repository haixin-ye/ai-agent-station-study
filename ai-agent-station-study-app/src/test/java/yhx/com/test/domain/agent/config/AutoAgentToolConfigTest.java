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
import yhx.com.domain.agent.model.valobj.context.CapabilityCandidateVO;
import yhx.com.domain.agent.model.valobj.context.ToolCapabilityExposurePolicyVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ApprovalPolicyEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.McpToolAvailabilityEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.RequiredPermissionEnumVO;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;
import yhx.com.domain.agent.service.context.ToolCapabilityCandidateProjector;
import yhx.com.domain.agent.service.tool.CapabilityRegistry;
import yhx.com.domain.agent.service.tool.McpClientRegistry;
import yhx.com.domain.agent.service.tool.McpToolRegistry;
import yhx.com.domain.agent.service.tool.port.McpToolDiscoveryPort;
import yhx.com.domain.agent.model.valobj.tool.McpRuntimeCatalogVO;

import java.io.IOException;
import java.util.LinkedHashMap;
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
        Assert.assertEquals(32, capabilityProperties.getPromptExposure().getMaxTools());
        Assert.assertEquals(2400, capabilityProperties.getPromptExposure().getMaxSchemaCharsPerTool());
        Assert.assertEquals(12000, capabilityProperties.getPromptExposure().getMaxTotalSchemaChars());
        AutoAgentToolConfig config = new AutoAgentToolConfig();
        McpRuntimeCatalogVO catalog = config.autoAgentMcpRuntimeCatalog(mcpProperties, serverId -> List.of(McpToolSpecVO.builder()
                .mcpServerCode(serverId)
                .toolName("JavaSDKMCPClient_list_allowed_directories")
                .description("Spring AI wrapper name that must not become a public capability.")
                .inputSchema(Map.of("type", "object"))
                .build()), availableClients(mcpProperties));

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

        McpRuntimeCatalogVO catalog = config.autoAgentMcpRuntimeCatalog(
                mcpProperties, discoveryPort, availableClients(mcpProperties));
        McpToolRegistry toolRegistry = config.mcpToolRegistry(catalog);
        CapabilityRegistry capabilityRegistry = config.capabilityRegistry(new AutoAgentCapabilityProperties(), mcpProperties, catalog);

        Assert.assertEquals("ai_search", toolRegistry.requireTool("baidu-ai-search", "ai_search").getToolName());
        Assert.assertTrue(capabilityRegistry.findCapability("baidu_ai_search_ai_search").isEmpty());
    }

    @Test
    public void yaml_only_tool_is_registered_and_exposed_with_schema() {
        AutoAgentMcpProperties properties = new AutoAgentMcpProperties();
        AutoAgentMcpProperties.McpServerProperties server = new AutoAgentMcpProperties.McpServerProperties();
        server.setServerId("invoice-server");
        server.setTransport("SSE");
        server.setAutoDiscoverTools(false);
        server.setAutoRegisterCapabilities(true);
        server.setCapabilityPrefix("invoice");
        AutoAgentMcpProperties.McpToolProperties tool = new AutoAgentMcpProperties.McpToolProperties();
        tool.setToolName("generate_invoice");
        tool.setDescription("Generate an invoice for a customer.");
        tool.setInputSchema(invoiceSchema());
        server.getTools().add(tool);
        properties.getServers().add(server);
        AutoAgentToolConfig config = new AutoAgentToolConfig();

        McpRuntimeCatalogVO catalog = config.autoAgentMcpRuntimeCatalog(properties,
                serverId -> {
                    throw new AssertionError("Discovery must not run for YAML-only servers.");
                }, availableClients(properties));
        McpToolRegistry toolRegistry = config.mcpToolRegistry(catalog);
        CapabilityRegistry capabilityRegistry = config.capabilityRegistry(
                new AutoAgentCapabilityProperties(), properties, catalog);
        CapabilitySpecVO capability = capabilityRegistry.requireCapability("invoice_generate_invoice");
        List<CapabilityCandidateVO> candidates = new ToolCapabilityCandidateProjector().projectAll(
                capabilityRegistry.listEnabledCapabilities(), toolRegistry, ToolCapabilityExposurePolicyVO.builder().build());

        Assert.assertEquals("generate_invoice",
                toolRegistry.requireTool("invoice-server", "generate_invoice").getToolName());
        Assert.assertEquals("invoice-server", capability.getMcpServerCode());
        Assert.assertEquals("generate_invoice", capability.getToolName());
        Assert.assertEquals(1, candidates.size());
        Assert.assertTrue(candidates.get(0).getRequiredArguments()
                .containsAll(List.of("customer", "customer.taxId", "currency")));
        Assert.assertNotNull(candidates.get(0).getInputSchema());
        Assert.assertNotNull(candidates.get(0).getSchemaHash());
    }

    @Test
    public void yaml_non_empty_metadata_overrides_discovery_and_missing_fields_fall_back() {
        AutoAgentMcpProperties properties = new AutoAgentMcpProperties();
        AutoAgentMcpProperties.McpServerProperties server = new AutoAgentMcpProperties.McpServerProperties();
        server.setServerId("invoice-server");
        server.setTransport("SSE");
        server.setAutoDiscoverTools(true);
        AutoAgentMcpProperties.McpToolProperties configured = new AutoAgentMcpProperties.McpToolProperties();
        configured.setToolName("generate_invoice");
        configured.setDescription("YAML invoice description.");
        configured.setInputSchema(invoiceSchema());
        server.getTools().add(configured);
        properties.getServers().add(server);

        McpToolSpecVO discovered = McpToolSpecVO.builder()
                .mcpServerCode("invoice-server")
                .toolName("generate_invoice")
                .description("Discovered description.")
                .inputSchemaRef("mcp://schemas/invoice")
                .inputSchema(Map.of("type", "object", "properties", Map.of("legacy", Map.of("type", "string"))))
                .requiredPermission(RequiredPermissionEnumVO.EXTERNAL_WRITE)
                .riskLevel("HIGH")
                .destructive(true)
                .schemaLessAllowed(true)
                .build();

        McpToolSpecVO merged = new AutoAgentToolConfig()
                .autoAgentMcpRuntimeCatalog(properties, serverId -> List.of(discovered), availableClients(properties))
                .getTools().get(0);

        Assert.assertEquals("YAML invoice description.", merged.getDescription());
        Assert.assertEquals(invoiceSchema(), merged.getInputSchema());
        Assert.assertEquals("mcp://schemas/invoice", merged.getInputSchemaRef());
        Assert.assertEquals(RequiredPermissionEnumVO.EXTERNAL_WRITE, merged.getRequiredPermission());
        Assert.assertEquals("HIGH", merged.getRiskLevel());
        Assert.assertEquals(Boolean.TRUE, merged.getDestructive());
        Assert.assertEquals(Boolean.TRUE, merged.getSchemaLessAllowed());
    }

    @Test
    public void unavailable_client_keeps_yaml_metadata_but_does_not_auto_register_capability() {
        AutoAgentMcpProperties properties = yamlOnlyInvoiceProperties();
        AutoAgentToolConfig config = new AutoAgentToolConfig();

        McpRuntimeCatalogVO catalog = config.autoAgentMcpRuntimeCatalog(properties,
                serverId -> List.of(), new McpClientRegistry(Map.of()));
        McpToolSpecVO tool = catalog.getTools().get(0);
        CapabilityRegistry capabilityRegistry = config.capabilityRegistry(
                new AutoAgentCapabilityProperties(), properties, catalog);

        Assert.assertEquals(McpToolAvailabilityEnumVO.UNAVAILABLE, tool.getAvailability());
        Assert.assertTrue(capabilityRegistry.findCapability("invoice_generate_invoice").isEmpty());
    }

    @Test
    public void missing_client_registry_marks_yaml_metadata_unavailable() {
        AutoAgentMcpProperties properties = yamlOnlyInvoiceProperties();

        McpRuntimeCatalogVO catalog = new AutoAgentToolConfig().autoAgentMcpRuntimeCatalog(
                properties, serverId -> List.of(), null);

        Assert.assertEquals(McpToolAvailabilityEnumVO.UNAVAILABLE, catalog.getTools().get(0).getAvailability());
    }

    @Test
    public void discovery_failure_marks_yaml_fallback_degraded() {
        AutoAgentMcpProperties properties = yamlOnlyInvoiceProperties();
        properties.getServers().get(0).setAutoDiscoverTools(true);
        McpClientRegistry clients = new McpClientRegistry(Map.of("invoice-server", new Object()));

        McpRuntimeCatalogVO catalog = new AutoAgentToolConfig().autoAgentMcpRuntimeCatalog(properties,
                serverId -> {
                    throw new IllegalStateException("endpoint unavailable");
                }, clients);

        Assert.assertEquals(McpToolAvailabilityEnumVO.DEGRADED, catalog.getTools().get(0).getAvailability());
    }

    @Test
    public void explicit_non_destructive_tool_override_survives_auto_capability_registration() {
        AutoAgentMcpProperties properties = yamlOnlyInvoiceProperties();
        AutoAgentMcpProperties.McpServerProperties server = properties.getServers().get(0);
        server.setDefaultDestructive(true);
        server.getTools().get(0).setDestructive(false);
        McpClientRegistry clients = new McpClientRegistry(Map.of("invoice-server", new Object()));
        AutoAgentToolConfig config = new AutoAgentToolConfig();

        McpRuntimeCatalogVO catalog = config.autoAgentMcpRuntimeCatalog(properties, serverId -> List.of(), clients);
        CapabilitySpecVO capability = config.capabilityRegistry(
                new AutoAgentCapabilityProperties(), properties, catalog)
                .requireCapability("invoice_generate_invoice");

        Assert.assertEquals(Boolean.FALSE, catalog.getTools().get(0).getDestructive());
        Assert.assertEquals(Boolean.FALSE, capability.getDestructive());
    }

    private Map<String, Object> invoiceSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("customer", "currency"),
                "properties", Map.of(
                        "customer", Map.of(
                                "type", "object",
                                "required", List.of("taxId"),
                                "properties", Map.of("taxId", Map.of("type", "string"))),
                        "currency", Map.of("type", "string", "enum", List.of("CNY", "USD"))));
    }

    private AutoAgentMcpProperties yamlOnlyInvoiceProperties() {
        AutoAgentMcpProperties properties = new AutoAgentMcpProperties();
        AutoAgentMcpProperties.McpServerProperties server = new AutoAgentMcpProperties.McpServerProperties();
        server.setServerId("invoice-server");
        server.setTransport("SSE");
        server.setAutoDiscoverTools(false);
        server.setAutoRegisterCapabilities(true);
        server.setCapabilityPrefix("invoice");
        AutoAgentMcpProperties.McpToolProperties tool = new AutoAgentMcpProperties.McpToolProperties();
        tool.setToolName("generate_invoice");
        tool.setDescription("Generate an invoice for a customer.");
        tool.setInputSchema(invoiceSchema());
        server.getTools().add(tool);
        properties.getServers().add(server);
        return properties;
    }

    private McpClientRegistry availableClients(AutoAgentMcpProperties properties) {
        Map<String, Object> handles = new LinkedHashMap<>();
        properties.getServers().stream()
                .filter(AutoAgentMcpProperties.McpServerProperties::isEnabled)
                .forEach(server -> handles.put(server.getServerId(), new Object()));
        return new McpClientRegistry(handles);
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
