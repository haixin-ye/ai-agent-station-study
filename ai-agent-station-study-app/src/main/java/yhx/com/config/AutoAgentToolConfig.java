package yhx.com.config;

import io.modelcontextprotocol.client.McpSyncClient;
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
import yhx.com.domain.agent.model.valobj.enums.armory.AiAgentEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ApprovalPolicyEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.McpTransportTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.RequiredPermissionEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolArgumentContentModeEnumVO;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties({AutoAgentCapabilityProperties.class, AutoAgentMcpProperties.class})
public class AutoAgentToolConfig {

    @Bean
    public CapabilityRegistry capabilityRegistry(AutoAgentCapabilityProperties properties) {
        List<CapabilitySpecVO> capabilities = properties.getTools().stream()
                .map(this::toCapabilitySpec)
                .toList();
        return new CapabilityRegistry(capabilities);
    }

    @Bean
    public McpClientRegistry mcpClientRegistry(AutoAgentMcpProperties properties, ApplicationContext applicationContext) {
        Map<String, McpSyncClient> clients = applicationContext.getBeansOfType(McpSyncClient.class);
        Map<String, Object> handles = new LinkedHashMap<>();
        for (AutoAgentMcpProperties.McpServerProperties server : properties.getServers()) {
            if (!server.isEnabled() || server.getServerId() == null) {
                continue;
            }
            McpSyncClient client = clients.get(server.getServerId());
            if (client == null) {
                client = clients.get(AiAgentEnumVO.AI_CLIENT_TOOL_MCP.getBeanName(server.getServerId()));
            }
            if (client != null) {
                handles.put(server.getServerId(), client);
            }
        }
        return new McpClientRegistry(handles);
    }

    @Bean
    public McpToolRegistry mcpToolRegistry(AutoAgentMcpProperties properties) {
        List<McpToolSpecVO> tools = properties.getServers().stream()
                .filter(AutoAgentMcpProperties.McpServerProperties::isEnabled)
                .flatMap(server -> server.getTools().stream().map(tool -> toMcpToolSpec(server, tool)))
                .toList();
        return new McpToolRegistry(tools);
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
}
