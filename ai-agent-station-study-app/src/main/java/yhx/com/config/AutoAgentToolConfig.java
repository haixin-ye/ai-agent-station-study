package yhx.com.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRunTranscriptRepository;
import yhx.com.domain.agent.adapter.repository.IToolRepository;
import yhx.com.domain.agent.model.valobj.enums.tool.ApprovalPolicyEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.McpTransportTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.RequiredPermissionEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolArgumentContentModeEnumVO;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
import yhx.com.domain.agent.model.valobj.tool.McpRuntimeCatalogVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;
import yhx.com.domain.agent.service.runtime.port.ToolActionOrchestratorPort;
import yhx.com.domain.agent.service.tool.CapabilityRegistry;
import yhx.com.domain.agent.service.tool.McpClientRegistry;
import yhx.com.domain.agent.service.tool.McpToolRegistry;
import yhx.com.domain.agent.service.tool.PermissionEnforcer;
import yhx.com.domain.agent.service.tool.ToolActionOrchestrator;
import yhx.com.domain.agent.service.tool.ToolApprovalKeyGenerator;
import yhx.com.domain.agent.service.tool.ToolApprovalService;
import yhx.com.domain.agent.service.tool.ToolArgumentMaterializer;
import yhx.com.domain.agent.service.tool.ToolEvidenceConverter;
import yhx.com.domain.agent.service.tool.ToolFailureMapper;
import yhx.com.domain.agent.service.tool.ToolInvocationRequestBuilder;
import yhx.com.domain.agent.service.tool.ToolReceiptCapture;
import yhx.com.domain.agent.service.tool.ToolRuntime;
import yhx.com.domain.agent.service.tool.ToolTranscriptRecorder;
import yhx.com.domain.agent.service.tool.ToolVerifier;
import yhx.com.domain.agent.service.tool.port.McpToolDiscoveryPort;
import yhx.com.domain.agent.service.tool.port.McpToolInvokerPort;
import yhx.com.infrastructure.adapter.port.SpringAiMcpToolDiscoveryAdapter;
import yhx.com.infrastructure.adapter.port.SpringAiMcpToolInvokerAdapter;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Configuration
@EnableConfigurationProperties({AutoAgentCapabilityProperties.class, AutoAgentMcpProperties.class})
@Slf4j
public class AutoAgentToolConfig {

    @Bean
    public CapabilityRegistry capabilityRegistry(AutoAgentCapabilityProperties properties,
                                                 AutoAgentMcpProperties mcpProperties,
                                                 McpRuntimeCatalogVO mcpRuntimeCatalog) {
        List<CapabilitySpecVO> capabilities = new ArrayList<>(properties.getTools().stream()
                .map(this::toCapabilitySpec)
                .toList());
        capabilities.addAll(autoCapabilities(mcpProperties, mcpRuntimeCatalog, capabilities));
        capabilities = capabilities.stream()
                .map(capability -> applyMcpServerTimeoutDefault(capability, mcpProperties))
                .toList();
        return new CapabilityRegistry(capabilities);
    }

    @Bean(destroyMethod = "close")
    public McpClientRegistry mcpClientRegistry(AutoAgentMcpProperties properties, ApplicationContext applicationContext) {
        Map<String, McpSyncClient> clients = applicationContext.getBeansOfType(McpSyncClient.class);
        Map<String, Object> handles = new LinkedHashMap<>();
        if (!properties.isEnabled()) {
            return new McpClientRegistry(handles);
        }
        for (AutoAgentMcpProperties.McpServerProperties server : properties.getServers()) {
            if (!server.isEnabled() || server.getServerId() == null) {
                continue;
            }
            McpSyncClient client = clients.get(server.getServerId());
            if (client == null) {
                try {
                    client = createConfiguredClient(server);
                } catch (RuntimeException e) {
                    log.warn("MCP client skipped, serverId={}, transport={}, reason={}",
                            server.getServerId(), server.getTransport(), e.getMessage());
                    continue;
                }
            }
            if (client != null) {
                handles.put(server.getServerId(), client);
            }
        }
        return new McpClientRegistry(handles);
    }

    @Bean
    public McpRuntimeCatalogVO autoAgentMcpRuntimeCatalog(AutoAgentMcpProperties properties,
                                                          McpToolDiscoveryPort discoveryPort) {
        if (!properties.isEnabled()) {
            return McpRuntimeCatalogVO.builder().tools(List.of()).build();
        }
        Map<String, McpToolSpecVO> merged = new LinkedHashMap<>();
        for (AutoAgentMcpProperties.McpServerProperties server : properties.getServers()) {
            if (!server.isEnabled() || isBlank(server.getServerId())) {
                continue;
            }
            if (server.isAutoDiscoverTools() && discoveryPort != null) {
                try {
                    for (McpToolSpecVO discovered : discoveryPort.discover(server.getServerId())) {
                        McpToolSpecVO tool = withServerDefaults(server, discovered);
                        if (tool == null || isBlank(tool.getToolName())) {
                            continue;
                        }
                        merged.put(toolKey(tool.getMcpServerCode(), tool.getToolName()), tool);
                    }
                } catch (RuntimeException e) {
                    log.warn("MCP tool discovery failed, serverId={}, reason={}", server.getServerId(), e.getMessage());
                }
            }
            for (AutoAgentMcpProperties.McpToolProperties configuredTool : server.getTools()) {
                McpToolSpecVO configured = toMcpToolSpec(server, configuredTool);
                String key = toolKey(configured.getMcpServerCode(), configured.getToolName());
                merged.put(key, mergeToolSpec(merged.get(key), configured));
            }
        }
        return McpRuntimeCatalogVO.builder().tools(new ArrayList<>(merged.values())).build();
    }

    @Bean
    public McpToolRegistry mcpToolRegistry(McpRuntimeCatalogVO mcpRuntimeCatalog) {
        return new McpToolRegistry(mcpRuntimeCatalog == null ? List.of() : mcpRuntimeCatalog.getTools());
    }

    @Bean
    public McpToolInvokerPort mcpToolInvokerPort(McpClientRegistry mcpClientRegistry) {
        return new SpringAiMcpToolInvokerAdapter(mcpClientRegistry);
    }

    @Bean
    public McpToolDiscoveryPort mcpToolDiscoveryPort(McpClientRegistry mcpClientRegistry) {
        return new SpringAiMcpToolDiscoveryAdapter(mcpClientRegistry);
    }

    @Bean
    @ConditionalOnBean({IArtifactRepository.class, IEvidenceRepository.class, IPayloadRepository.class})
    public ToolArgumentMaterializer toolArgumentMaterializer(IArtifactRepository artifactRepository,
                                                             IEvidenceRepository evidenceRepository,
                                                             IPayloadRepository payloadRepository) {
        return new ToolArgumentMaterializer(artifactRepository, evidenceRepository, payloadRepository);
    }

    @Bean
    public PermissionEnforcer permissionEnforcer() {
        return new PermissionEnforcer();
    }

    @Bean
    public ToolApprovalKeyGenerator toolApprovalKeyGenerator() {
        return new ToolApprovalKeyGenerator();
    }

    @Bean
    @ConditionalOnBean({IToolRepository.class, IPayloadRepository.class, UserInteractionManager.class})
    public ToolApprovalService toolApprovalService(IToolRepository toolRepository,
                                                   IPayloadRepository payloadRepository,
                                                   UserInteractionManager userInteractionManager) {
        return new ToolApprovalService(toolRepository, payloadRepository, userInteractionManager);
    }

    @Bean
    @ConditionalOnBean({CapabilityRegistry.class, McpToolRegistry.class, ToolArgumentMaterializer.class,
            PermissionEnforcer.class, ToolApprovalService.class, ToolApprovalKeyGenerator.class,
            IToolRepository.class, IPayloadRepository.class})
    public ToolInvocationRequestBuilder toolInvocationRequestBuilder(CapabilityRegistry capabilityRegistry,
                                                                     McpToolRegistry mcpToolRegistry,
                                                                     ToolArgumentMaterializer argumentMaterializer,
                                                                     PermissionEnforcer permissionEnforcer,
                                                                     ToolApprovalService approvalService,
                                                                     ToolApprovalKeyGenerator approvalKeyGenerator,
                                                                     IToolRepository toolRepository,
                                                                     IPayloadRepository payloadRepository) {
        return new ToolInvocationRequestBuilder(capabilityRegistry, mcpToolRegistry, argumentMaterializer,
                permissionEnforcer, approvalService, approvalKeyGenerator, toolRepository, payloadRepository);
    }

    @Bean
    @ConditionalOnBean(IPayloadRepository.class)
    public ToolReceiptCapture toolReceiptCapture(IPayloadRepository payloadRepository) {
        return new ToolReceiptCapture(payloadRepository);
    }

    @Bean
    public ToolFailureMapper toolFailureMapper() {
        return new ToolFailureMapper();
    }

    @Bean
    @ConditionalOnBean({McpToolInvokerPort.class, ToolReceiptCapture.class, ToolFailureMapper.class, IToolRepository.class})
    public ToolRuntime toolRuntime(McpToolInvokerPort mcpToolInvokerPort,
                                   ToolReceiptCapture receiptCapture,
                                   ToolFailureMapper failureMapper,
                                   IToolRepository toolRepository) {
        return new ToolRuntime(mcpToolInvokerPort, receiptCapture, failureMapper, toolRepository);
    }

    @Bean
    @ConditionalOnBean({IToolRepository.class, IPayloadRepository.class})
    public ToolVerifier toolVerifier(IToolRepository toolRepository, IPayloadRepository payloadRepository) {
        return new ToolVerifier(toolRepository, payloadRepository);
    }

    @Bean
    @ConditionalOnBean(IEvidenceRepository.class)
    public ToolEvidenceConverter toolEvidenceConverter(IEvidenceRepository evidenceRepository) {
        return new ToolEvidenceConverter(evidenceRepository);
    }

    @Bean
    @ConditionalOnBean({IRunTranscriptRepository.class, IPayloadRepository.class})
    public ToolTranscriptRecorder toolTranscriptRecorder(IRunTranscriptRepository transcriptRepository,
                                                         IPayloadRepository payloadRepository) {
        return new ToolTranscriptRecorder(transcriptRepository, payloadRepository);
    }

    @Bean
    @ConditionalOnBean({ToolInvocationRequestBuilder.class, ToolRuntime.class, ToolVerifier.class,
            ToolEvidenceConverter.class, ToolTranscriptRecorder.class})
    public ToolActionOrchestratorPort toolActionOrchestratorPort(ToolInvocationRequestBuilder requestBuilder,
                                                                 ToolRuntime toolRuntime,
                                                                 ToolVerifier toolVerifier,
                                                                 ToolEvidenceConverter evidenceConverter,
                                                                 ToolTranscriptRecorder transcriptRecorder) {
        return new ToolActionOrchestrator(requestBuilder, toolRuntime, toolVerifier, evidenceConverter, transcriptRecorder);
    }

    private CapabilitySpecVO toCapabilitySpec(AutoAgentCapabilityProperties.CapabilityProperties properties) {
        return CapabilitySpecVO.builder()
                .capabilityCode(properties.getCapabilityId())
                .capabilityType(properties.getCapabilityType())
                .mcpServerCode(properties.getMcpServerId())
                .toolName(properties.getMcpToolName())
                .requiredPermission(enumValue(properties.getRequiredPermission(), RequiredPermissionEnumVO.NONE))
                .permissionMode(enumValue(properties.getPermissionMode(), PermissionModeEnumVO.ALLOW))
                .approvalPolicy(enumValue(properties.getApprovalPolicy(), ApprovalPolicyEnumVO.NEVER))
                .riskLevel(properties.getRiskLevel())
                .destructive(properties.isDestructive())
                .defaultContentMode(enumValue(properties.getDefaultContentMode(), ToolArgumentContentModeEnumVO.SUMMARY_ONLY))
                .workspaceScope(properties.getWorkspaceScope())
                .timeoutMs(properties.getTimeoutMs())
                .enabled(properties.isEnabled())
                .argumentDefaults(properties.getArgumentDefaults())
                .build();
    }

    private McpToolSpecVO toMcpToolSpec(AutoAgentMcpProperties.McpServerProperties server,
                                        AutoAgentMcpProperties.McpToolProperties tool) {
        return McpToolSpecVO.builder()
                .mcpServerCode(server.getServerId())
                .toolName(tool.getToolName())
                .description(tool.getDescription())
                .transportType(enumValue(server.getTransport(), McpTransportTypeEnumVO.UNKNOWN))
                .inputSchemaRef(tool.getInputSchemaRef())
                .inputSchema(tool.getInputSchema())
                .requiredPermission(enumValue(tool.getRequiredPermission(), RequiredPermissionEnumVO.NONE))
                .riskLevel(tool.getRiskLevel())
                .destructive(tool.isDestructive())
                .schemaLessAllowed(tool.isSchemaLessAllowed())
                .build();
    }

    private List<CapabilitySpecVO> autoCapabilities(AutoAgentMcpProperties mcpProperties,
                                                    McpRuntimeCatalogVO mcpRuntimeCatalog,
                                                    List<CapabilitySpecVO> explicitCapabilities) {
        if (mcpProperties == null || !mcpProperties.isEnabled() || mcpRuntimeCatalog == null || mcpRuntimeCatalog.getTools() == null) {
            return List.of();
        }
        Set<String> explicitToolKeys = new HashSet<>();
        Set<String> explicitCapabilityCodes = new HashSet<>();
        if (explicitCapabilities != null) {
            for (CapabilitySpecVO capability : explicitCapabilities) {
                if (capability == null) {
                    continue;
                }
                explicitCapabilityCodes.add(capability.getCapabilityCode());
                explicitToolKeys.add(toolKey(capability.getMcpServerCode(), capability.getToolName()));
            }
        }
        List<CapabilitySpecVO> generated = new ArrayList<>();
        for (McpToolSpecVO tool : mcpRuntimeCatalog.getTools()) {
            AutoAgentMcpProperties.McpServerProperties server = findServer(mcpProperties, tool.getMcpServerCode());
            if (server == null || !server.isAutoRegisterCapabilities()) {
                continue;
            }
            String toolKey = toolKey(tool.getMcpServerCode(), tool.getToolName());
            if (explicitToolKeys.contains(toolKey)) {
                continue;
            }
            String capabilityCode = capabilityCode(server, tool.getToolName());
            if (explicitCapabilityCodes.contains(capabilityCode)) {
                continue;
            }
            generated.add(CapabilitySpecVO.builder()
                    .capabilityCode(capabilityCode)
                    .capabilityType("TOOL")
                    .mcpServerCode(tool.getMcpServerCode())
                    .toolName(tool.getToolName())
                    .requiredPermission(firstNonNull(tool.getRequiredPermission(),
                            enumValue(server.getDefaultRequiredPermission(), RequiredPermissionEnumVO.NONE)))
                    .permissionMode(enumValue(server.getDefaultPermissionMode(), PermissionModeEnumVO.ASK_USER))
                    .approvalPolicy(enumValue(server.getDefaultApprovalPolicy(), ApprovalPolicyEnumVO.ASK_USER_BEFORE_EXECUTE))
                    .riskLevel(firstNonBlank(tool.getRiskLevel(), server.getDefaultRiskLevel(), "MEDIUM"))
                    .destructive(Boolean.TRUE.equals(tool.getDestructive()) || server.isDefaultDestructive())
                    .defaultContentMode(enumValue(server.getDefaultContentMode(), ToolArgumentContentModeEnumVO.SUMMARY_ONLY))
                    .workspaceScope(server.getWorkspaceScope())
                    .timeoutMs(resolveToolTimeoutMs(server))
                    .enabled(true)
                    .build());
            explicitCapabilityCodes.add(capabilityCode);
            explicitToolKeys.add(toolKey);
        }
        return generated;
    }

    private McpToolSpecVO withServerDefaults(AutoAgentMcpProperties.McpServerProperties server, McpToolSpecVO tool) {
        if (tool == null) {
            return null;
        }
        return McpToolSpecVO.builder()
                .mcpServerCode(firstNonBlank(tool.getMcpServerCode(), server.getServerId()))
                .toolName(tool.getToolName())
                .description(tool.getDescription())
                .transportType(firstNonNull(tool.getTransportType(), enumValue(server.getTransport(), McpTransportTypeEnumVO.UNKNOWN)))
                .inputSchemaRef(tool.getInputSchemaRef())
                .inputSchema(tool.getInputSchema())
                .requiredPermission(firstNonNull(tool.getRequiredPermission(), enumValue(server.getDefaultRequiredPermission(), RequiredPermissionEnumVO.NONE)))
                .riskLevel(firstNonBlank(tool.getRiskLevel(), server.getDefaultRiskLevel(), "MEDIUM"))
                .destructive(Boolean.TRUE.equals(tool.getDestructive()) || server.isDefaultDestructive())
                .schemaLessAllowed(Boolean.TRUE.equals(tool.getSchemaLessAllowed()))
                .build();
    }

    private McpToolSpecVO mergeToolSpec(McpToolSpecVO base, McpToolSpecVO override) {
        if (base == null) {
            return override;
        }
        return McpToolSpecVO.builder()
                .mcpServerCode(firstNonBlank(override.getMcpServerCode(), base.getMcpServerCode()))
                .toolName(firstNonBlank(override.getToolName(), base.getToolName()))
                .description(firstNonBlank(override.getDescription(), base.getDescription()))
                .transportType(firstNonNull(override.getTransportType(), base.getTransportType()))
                .inputSchemaRef(firstNonBlank(override.getInputSchemaRef(), base.getInputSchemaRef()))
                .inputSchema(override.getInputSchema() == null || override.getInputSchema().isEmpty() ? base.getInputSchema() : override.getInputSchema())
                .requiredPermission(firstNonNull(override.getRequiredPermission(), base.getRequiredPermission()))
                .riskLevel(firstNonBlank(override.getRiskLevel(), base.getRiskLevel()))
                .destructive(firstNonNull(override.getDestructive(), base.getDestructive()))
                .schemaLessAllowed(firstNonNull(override.getSchemaLessAllowed(), base.getSchemaLessAllowed()))
                .build();
    }

    private AutoAgentMcpProperties.McpServerProperties findServer(AutoAgentMcpProperties properties, String serverId) {
        if (properties == null || properties.getServers() == null) {
            return null;
        }
        return properties.getServers().stream()
                .filter(server -> serverId != null && serverId.equals(server.getServerId()))
                .findFirst()
                .orElse(null);
    }

    private String capabilityCode(AutoAgentMcpProperties.McpServerProperties server, String toolName) {
        String prefix = firstNonBlank(server.getCapabilityPrefix(), server.getServerId());
        return sanitize(prefix) + "_" + sanitize(toolName);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private RequiredPermissionEnumVO enumValue(String code, RequiredPermissionEnumVO defaultValue) {
        return RequiredPermissionEnumVO.ofCode(normalize(code)).orElse(defaultValue);
    }

    private PermissionModeEnumVO enumValue(String code, PermissionModeEnumVO defaultValue) {
        return PermissionModeEnumVO.ofCode(normalize(code)).orElse(defaultValue);
    }

    private ApprovalPolicyEnumVO enumValue(String code, ApprovalPolicyEnumVO defaultValue) {
        return ApprovalPolicyEnumVO.ofCode(normalize(code)).orElse(defaultValue);
    }

    private ToolArgumentContentModeEnumVO enumValue(String code, ToolArgumentContentModeEnumVO defaultValue) {
        return ToolArgumentContentModeEnumVO.ofCode(normalize(code)).orElse(defaultValue);
    }

    private McpTransportTypeEnumVO enumValue(String code, McpTransportTypeEnumVO defaultValue) {
        return McpTransportTypeEnumVO.ofCode(normalize(code)).orElse(defaultValue);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private McpSyncClient createConfiguredClient(AutoAgentMcpProperties.McpServerProperties server) {
        McpTransportTypeEnumVO transportType = enumValue(server.getTransport(), McpTransportTypeEnumVO.UNKNOWN);
        return switch (transportType) {
            case STDIO -> createStdioClient(server);
            case SSE -> createSseClient(server);
            default -> throw new IllegalArgumentException("Unsupported MCP transport for server "
                    + server.getServerId() + ": " + server.getTransport());
        };
    }

    private McpSyncClient createStdioClient(AutoAgentMcpProperties.McpServerProperties server) {
        if (isBlank(server.getCommand())) {
            throw new IllegalArgumentException("MCP stdio server command is required: " + server.getServerId());
        }
        ServerParameters.Builder builder = ServerParameters.builder(server.getCommand());
        if (server.getArgs() != null && !server.getArgs().isEmpty()) {
            builder.args(server.getArgs().toArray(new String[0]));
        }
        if (server.getEnv() != null && !server.getEnv().isEmpty()) {
            builder.env(server.getEnv());
        }
        McpSyncClient client = McpClient.sync(new StdioClientTransport(builder.build()))
                .requestTimeout(resolveRequestTimeout(server))
                .build();
        initializeIfNeeded(server, client);
        return client;
    }

    private McpSyncClient createSseClient(AutoAgentMcpProperties.McpServerProperties server) {
        if (isBlank(server.getUrl())) {
            throw new IllegalArgumentException("MCP SSE server url is required: " + server.getServerId());
        }
        HttpClientSseClientTransport transport;
        if (hasHttpHeaders(server)) {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
            if (server.getHeaders() != null) {
                server.getHeaders().forEach((name, value) -> {
                    if (!isBlank(name) && !isBlank(value)) {
                        requestBuilder.header(name, value);
                    }
                });
            }
            if (!isBlank(server.getBearerToken())) {
                requestBuilder.header("Authorization", "Bearer " + server.getBearerToken());
            }
            if (!isBlank(server.getApiKey())) {
                requestBuilder.header(firstNonBlank(server.getApiKeyHeader(), "X-API-Key"), server.getApiKey());
            }
            transport = new HttpClientSseClientTransport(HttpClient.newBuilder(),
                    requestBuilder,
                    server.getUrl(),
                    firstNonBlank(server.getSseEndpoint(), "/sse"),
                    new ObjectMapper());
        } else {
            HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(server.getUrl());
            if (!isBlank(server.getSseEndpoint())) {
                builder.sseEndpoint(server.getSseEndpoint());
            }
            transport = builder.build();
        }
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(resolveRequestTimeout(server))
                .build();
        initializeIfNeeded(server, client);
        return client;
    }

    private Duration resolveRequestTimeout(AutoAgentMcpProperties.McpServerProperties server) {
        long seconds = server.getRequestTimeoutSeconds() <= 0 ? 100 : server.getRequestTimeoutSeconds();
        return Duration.ofSeconds(seconds);
    }

    private Long resolveToolTimeoutMs(AutoAgentMcpProperties.McpServerProperties server) {
        if (server == null) {
            return null;
        }
        if (server.getTimeoutMs() != null && server.getTimeoutMs() > 0) {
            return server.getTimeoutMs();
        }
        long seconds = server.getRequestTimeoutSeconds() <= 0 ? 100 : server.getRequestTimeoutSeconds();
        return Duration.ofSeconds(seconds).toMillis();
    }

    private CapabilitySpecVO applyMcpServerTimeoutDefault(CapabilitySpecVO capability,
                                                          AutoAgentMcpProperties mcpProperties) {
        if (capability == null || capability.getTimeoutMs() != null) {
            return capability;
        }
        AutoAgentMcpProperties.McpServerProperties server = findServer(mcpProperties, capability.getMcpServerCode());
        Long timeoutMs = resolveToolTimeoutMs(server);
        if (timeoutMs == null) {
            return capability;
        }
        return CapabilitySpecVO.builder()
                .capabilityCode(capability.getCapabilityCode())
                .capabilityType(capability.getCapabilityType())
                .mcpServerCode(capability.getMcpServerCode())
                .toolName(capability.getToolName())
                .requiredPermission(capability.getRequiredPermission())
                .permissionMode(capability.getPermissionMode())
                .approvalPolicy(capability.getApprovalPolicy())
                .riskLevel(capability.getRiskLevel())
                .destructive(capability.getDestructive())
                .defaultContentMode(capability.getDefaultContentMode())
                .enabled(capability.getEnabled())
                .workspaceScope(capability.getWorkspaceScope())
                .timeoutMs(timeoutMs)
                .argumentDefaults(capability.getArgumentDefaults())
                .build();
    }

    private void initializeIfNeeded(AutoAgentMcpProperties.McpServerProperties server, McpSyncClient client) {
        if (server.isAutoInitialize()) {
            client.initialize();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hasHttpHeaders(AutoAgentMcpProperties.McpServerProperties server) {
        return (server.getHeaders() != null && server.getHeaders().values().stream().anyMatch(value -> !isBlank(value)))
                || !isBlank(server.getBearerToken())
                || !isBlank(server.getApiKey());
    }

    private String toolKey(String mcpServerCode, String toolName) {
        return String.valueOf(mcpServerCode) + "::" + String.valueOf(toolName);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private <T> T firstNonNull(T first, T second) {
        return first == null ? second : first;
    }
}
