package yhx.com.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.service.modelruntime.NodeRuntimeProfileResolver;
import yhx.com.domain.agent.service.finalresponse.FinalDeliveryService;
import yhx.com.domain.agent.service.finalresponse.FinalRepairService;
import yhx.com.domain.agent.service.finalresponse.FinalResponseBuilder;
import yhx.com.domain.agent.service.finalresponse.FinalResponseGuard;
import yhx.com.domain.agent.service.finalresponse.FinalResponseGuardInputBuilder;
import yhx.com.domain.agent.service.finalresponse.FinalResponsePersistenceService;
import yhx.com.domain.agent.service.finalresponse.FixedSafeFallbackFactory;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;
import yhx.com.domain.agent.service.rag.runtime.RagVerificationRouter;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.port.FinalDeliveryPort;

@Configuration
@EnableConfigurationProperties(AutoAgentRuntimeProperties.class)
public class AutoAgentFinalResponseConfig {

    @Bean
    public FinalResponseGuardInputBuilder finalResponseGuardInputBuilder() {
        return new FinalResponseGuardInputBuilder();
    }

    @Bean
    public FinalResponseGuard finalResponseGuard() {
        return new FinalResponseGuard();
    }

    @Bean
    public FinalResponseBuilder finalResponseBuilder() {
        return new FinalResponseBuilder();
    }

    @Bean
    public FixedSafeFallbackFactory fixedSafeFallbackFactory() {
        return new FixedSafeFallbackFactory();
    }

    @Bean
    public FinalResponsePersistenceService finalResponsePersistenceService(IPayloadRepository payloadRepository,
                                                                           IConversationRepository conversationRepository,
                                                                           IRunRepository runRepository,
                                                                           IEventTraceRepository eventTraceRepository) {
        return new FinalResponsePersistenceService(payloadRepository, conversationRepository, runRepository, eventTraceRepository);
    }

    @Bean
    public FinalRepairService finalRepairService(ObjectProvider<NodeInvocationPipeline> nodeInvocationPipelineProvider,
                                                 NodeRuntimeProfileResolver nodeRuntimeProfileResolver) {
        return new FinalRepairService(nodeInvocationPipelineProvider.getIfAvailable(),
                nodeRuntimeProfileResolver.resolveRequired("FINAL_REPAIR"));
    }

    @Bean
    public FinalDeliveryPort finalDeliveryPort(IRunRepository runRepository,
                                               ObjectProvider<RagVerificationRouter> ragVerificationRouterProvider,
                                               FinalResponseGuardInputBuilder guardInputBuilder,
                                               FinalResponseGuard finalResponseGuard,
                                               FinalResponseBuilder finalResponseBuilder,
                                               FinalRepairService finalRepairService,
                                               FixedSafeFallbackFactory fallbackFactory,
                                               FinalResponsePersistenceService persistenceService,
                                               ObjectProvider<RunEventPublisher> eventPublisherProvider) {
        return new FinalDeliveryService(runRepository,
                ragVerificationRouterProvider.getIfAvailable(),
                guardInputBuilder,
                finalResponseGuard,
                finalResponseBuilder,
                finalRepairService,
                fallbackFactory,
                persistenceService,
                new RuntimeFailureFactory(),
                eventPublisherProvider.getIfAvailable());
    }

}
