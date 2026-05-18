package yhx.com.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
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
@EnableConfigurationProperties({AutoAgentRuntimeProperties.class, AutoAgentNodeProperties.class})
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
                                                 AutoAgentNodeProperties nodeProperties,
                                                 AutoAgentRuntimeProperties runtimeProperties) {
        return new FinalRepairService(nodeInvocationPipelineProvider.getIfAvailable(),
                nodeProfile(nodeProperties, runtimeProperties, "FINAL_REPAIR", "main-agent-action-v1"));
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

    private NodeInvocationProfileVO nodeProfile(AutoAgentNodeProperties nodeProperties,
                                                AutoAgentRuntimeProperties runtimeProperties,
                                                String componentCode,
                                                String contractVersion) {
        AutoAgentNodeProperties.NodeModelProperties model = nodeProperties.getModels().get(componentCode);
        if (model == null) {
            return NodeInvocationProfileVO.builder()
                    .componentCode(componentCode)
                    .promptVersion("v1")
                    .contractVersion(contractVersion)
                    .maxRepairAttempts(runtimeProperties.getMaxContractRepairAttempts())
                    .build();
        }
        return NodeInvocationProfileVO.builder()
                .componentCode(componentCode)
                .modelCode(defaultIfBlank(model.getModelCode(), model.getModelName()))
                .promptVersion(defaultIfBlank(model.getPromptVersion(), "v1"))
                .contractVersion(contractVersion)
                .temperature(model.getTemperature())
                .maxOutputTokens(model.getMaxOutputTokens())
                .maxRepairAttempts(model.getMaxRepairAttempts() == null
                        ? runtimeProperties.getMaxContractRepairAttempts()
                        : model.getMaxRepairAttempts())
                .build();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
