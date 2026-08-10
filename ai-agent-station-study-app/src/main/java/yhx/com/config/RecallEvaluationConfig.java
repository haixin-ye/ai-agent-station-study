package yhx.com.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRagAssetRepository;
import yhx.com.domain.agent.adapter.repository.IRecallEvaluationRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.service.evaluation.ContextPlannerEvaluationAdapter;
import yhx.com.domain.agent.service.evaluation.RecallEvaluationComparisonService;
import yhx.com.domain.agent.service.evaluation.RecallEvaluationFacade;
import yhx.com.domain.agent.service.evaluation.RecallEvaluationIngestionService;
import yhx.com.domain.agent.service.evaluation.RecallEvaluationRunner;
import yhx.com.domain.agent.service.evaluation.RecallMetricsCalculator;
import yhx.com.domain.agent.service.memory.LongTermMemoryService;
import yhx.com.domain.agent.service.memory.MemoryVectorIndexingService;
import yhx.com.domain.agent.service.memory.VectorContextRecallPreselector;
import yhx.com.domain.agent.service.node.contextplanner.ContextPlannerNodeService;
import yhx.com.domain.agent.service.rag.RagContextRecallPreselector;
import yhx.com.domain.agent.service.rag.IRagDomainService;
import yhx.com.domain.agent.service.rag.RagVectorIndexingService;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@Profile("dev")
@EnableConfigurationProperties(RecallEvaluationProperties.class)
public class RecallEvaluationConfig {

    @Bean("recallEvaluationExecutor")
    public ThreadPoolTaskExecutor recallEvaluationExecutor(RecallEvaluationProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("recall-eval-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Bean
    public LongTermMemoryService longTermMemoryService(IPayloadRepository payloadRepository,
                                                       IMemoryRepository memoryRepository,
                                                       MemoryVectorIndexingService vectorIndexingService) {
        return new LongTermMemoryService(payloadRepository, memoryRepository, vectorIndexingService);
    }

    @Bean
    public RecallEvaluationIngestionService recallEvaluationIngestionService(
            IRecallEvaluationRepository evaluationRepository,
            IRagAssetRepository ragAssetRepository,
            LongTermMemoryService longTermMemoryService,
            IMemoryRepository memoryRepository,
            IPayloadRepository payloadRepository,
            IVectorMemoryRepository vectorMemoryRepository,
            MemoryVectorIndexingService memoryVectorIndexingService,
            RagVectorIndexingService ragVectorIndexingService,
            IRagDomainService ragDomainService) {
        return new RecallEvaluationIngestionService(evaluationRepository, ragAssetRepository,
                longTermMemoryService, memoryRepository, payloadRepository,
                vectorMemoryRepository, memoryVectorIndexingService, ragVectorIndexingService, ragDomainService);
    }

    @Bean
    public RecallMetricsCalculator recallMetricsCalculator() {
        return new RecallMetricsCalculator();
    }

    @Bean
    public ContextPlannerEvaluationAdapter contextPlannerEvaluationAdapter(
            ContextPlannerNodeService contextPlannerNodeService) {
        return new ContextPlannerEvaluationAdapter(contextPlannerNodeService);
    }

    @Bean
    public RecallEvaluationRunner recallEvaluationRunner(IRecallEvaluationRepository repository,
                                                         VectorContextRecallPreselector memoryRecall,
                                                         RagContextRecallPreselector ragRecall,
                                                         ContextPlannerEvaluationAdapter planner,
                                                         RecallMetricsCalculator metricsCalculator) {
        return new RecallEvaluationRunner(repository, memoryRecall, ragRecall, planner, metricsCalculator);
    }

    @Bean
    public RecallEvaluationComparisonService recallEvaluationComparisonService(
            IRecallEvaluationRepository repository) {
        return new RecallEvaluationComparisonService(repository);
    }

    @Bean
    public RecallEvaluationFacade recallEvaluationFacade(IRecallEvaluationRepository repository,
                                                         RecallEvaluationIngestionService ingestionService,
                                                         RecallEvaluationRunner runner,
                                                         RecallEvaluationComparisonService comparisonService,
                                                         IVectorMemoryRepository vectorMemoryRepository,
                                                         @Qualifier("recallEvaluationExecutor") Executor executor) {
        return new RecallEvaluationFacade(repository, ingestionService, runner, comparisonService,
                vectorMemoryRepository, executor);
    }
}
