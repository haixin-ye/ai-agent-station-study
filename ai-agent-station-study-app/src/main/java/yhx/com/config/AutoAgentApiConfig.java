package yhx.com.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IPendingInputRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.adapter.repository.IToolRepository;
import yhx.com.domain.agent.service.api.AgentDebugFacade;
import yhx.com.domain.agent.service.api.AgentMockScenarioService;
import yhx.com.domain.agent.service.api.AgentQueryFacade;
import yhx.com.domain.agent.service.api.AgentRuntimeFacade;
import yhx.com.domain.agent.service.api.DebugSseEventBridge;
import yhx.com.domain.agent.service.api.SseUserEventBridge;
import yhx.com.domain.agent.service.debug.DebugAccessPolicy;
import yhx.com.domain.agent.service.debug.DebugDataPipeline;
import yhx.com.domain.agent.service.debug.DebugPayloadPreviewPolicy;
import yhx.com.domain.agent.service.runtime.AutoAgentRuntimeService;

@Configuration
@EnableConfigurationProperties(AutoAgentDebugProperties.class)
public class AutoAgentApiConfig {

    @Bean
    public AgentRuntimeFacade agentRuntimeFacade(ObjectProvider<AutoAgentRuntimeService> runtimeServiceProvider) {
        return new AgentRuntimeFacade(runtimeServiceProvider.getIfAvailable());
    }

    @Bean
    public AgentQueryFacade agentQueryFacade(IRunRepository runRepository,
                                             IConversationRepository conversationRepository,
                                             IEventTraceRepository eventTraceRepository,
                                             IPendingInputRepository pendingInputRepository,
                                             IArtifactRepository artifactRepository,
                                             IPayloadRepository payloadRepository) {
        return new AgentQueryFacade(runRepository,
                conversationRepository,
                eventTraceRepository,
                pendingInputRepository,
                artifactRepository,
                payloadRepository);
    }

    @Bean
    public DebugAccessPolicy debugAccessPolicy(AutoAgentDebugProperties properties) {
        return new DebugAccessPolicy(properties.isDebugApiEnabled(),
                properties.isDebugSseEnabled(),
                properties.isDebugPayloadPreviewEnabled());
    }

    @Bean
    public DebugPayloadPreviewPolicy debugPayloadPreviewPolicy(AutoAgentDebugProperties properties) {
        return new DebugPayloadPreviewPolicy(properties.getDebugPayloadPreviewMaxChars(),
                properties.isDebugPayloadPreviewEnabled());
    }

    @Bean
    public DebugDataPipeline debugDataPipeline(IEventTraceRepository eventTraceRepository, IPayloadRepository payloadRepository) {
        return new DebugDataPipeline(eventTraceRepository, payloadRepository);
    }

    @Bean
    public AgentDebugFacade agentDebugFacade(IEventTraceRepository eventTraceRepository,
                                             IEvidenceRepository evidenceRepository,
                                             IToolRepository toolRepository,
                                             IPayloadRepository payloadRepository,
                                             DebugAccessPolicy debugAccessPolicy,
                                             DebugPayloadPreviewPolicy debugPayloadPreviewPolicy) {
        return new AgentDebugFacade(eventTraceRepository,
                evidenceRepository,
                toolRepository,
                payloadRepository,
                debugAccessPolicy,
                debugPayloadPreviewPolicy);
    }

    @Bean
    public SseUserEventBridge sseUserEventBridge(AgentQueryFacade agentQueryFacade) {
        return new SseUserEventBridge(agentQueryFacade);
    }

    @Bean
    public DebugSseEventBridge debugSseEventBridge(AgentDebugFacade agentDebugFacade) {
        return new DebugSseEventBridge(agentDebugFacade);
    }

    @Bean
    public AgentMockScenarioService agentMockScenarioService() {
        return new AgentMockScenarioService();
    }
}

