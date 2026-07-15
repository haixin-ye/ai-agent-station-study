package yhx.com.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import yhx.com.domain.agent.adapter.port.INodeClientPort;
import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IModelRuntimeRepository;
import yhx.com.domain.agent.adapter.repository.INodePromptRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IPendingInputRepository;
import yhx.com.domain.agent.adapter.repository.IPendingInputConsumptionRepository;
import yhx.com.domain.agent.adapter.transaction.IInteractionTransactionExecutor;
import yhx.com.domain.agent.adapter.repository.IRagAssetRepository;
import yhx.com.domain.agent.adapter.repository.IRagExecutionRepository;
import yhx.com.domain.agent.adapter.repository.IRunDiagnosticRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.adapter.repository.IRunTranscriptRepository;
import yhx.com.domain.agent.adapter.repository.ISessionTaskSummaryRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.adapter.repository.IVectorIndexRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.valobj.context.CapabilityCandidateVO;
import yhx.com.domain.agent.model.valobj.context.ToolCapabilityExposurePolicyVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.service.node.contextplanner.ContextPlannerNodeService;
import yhx.com.domain.agent.service.node.conversationrollup.ConversationRollupNodeService;
import yhx.com.domain.agent.service.node.genericsubagent.GenericSubAgentNodeService;
import yhx.com.domain.agent.service.node.mainagent.MainAgentNodeService;
import yhx.com.domain.agent.service.node.memorygovernance.MemoryGovernanceNodeService;
import yhx.com.domain.agent.service.node.memoryextraction.MemoryExtractionNodeService;
import yhx.com.domain.agent.service.node.ragasset.RagAssetAnalyzerNodeService;
import yhx.com.domain.agent.service.node.ragverifier.RagVerifierNodeService;
import yhx.com.domain.agent.service.node.sessiontasksummary.SessionTaskSummaryNodeService;
import yhx.com.domain.agent.service.node.turnsummary.TurnSummaryNodeService;
import yhx.com.domain.agent.service.artifact.ArtifactManager;
import yhx.com.domain.agent.service.artifact.ArtifactPayloadLoader;
import yhx.com.domain.agent.service.context.ContextBudgetManager;
import yhx.com.domain.agent.service.context.ContextCandidatePreselector;
import yhx.com.domain.agent.service.context.ContextMaterializer;
import yhx.com.domain.agent.service.context.ContextPlannerStatusHandler;
import yhx.com.domain.agent.service.context.ToolCapabilityCandidateProjector;
import yhx.com.domain.agent.service.context.ContextPreparationService;
import yhx.com.domain.agent.service.context.ContextSelectionValidator;
import yhx.com.domain.agent.service.context.ContextTokenEstimator;
import yhx.com.domain.agent.service.context.MainAgentStateViewBuilder;
import yhx.com.domain.agent.service.contract.ContractValidator;
import yhx.com.domain.agent.service.evidence.EvidencePackBuilder;
import yhx.com.domain.agent.service.interaction.ContextPlannerPendingInputHandler;
import yhx.com.domain.agent.service.interaction.FinalRepairPendingInputHandler;
import yhx.com.domain.agent.service.interaction.MainAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.PendingInputContinuationDispatcher;
import yhx.com.domain.agent.service.interaction.PendingInputManager;
import yhx.com.domain.agent.service.interaction.PendingInputPauseCoordinator;
import yhx.com.domain.agent.service.interaction.RagPendingInputHandler;
import yhx.com.domain.agent.service.interaction.SubAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;
import yhx.com.domain.agent.service.interaction.RuntimeContinuationSnapshotService;
import yhx.com.domain.agent.service.interaction.UserReplyProcessor;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;
import yhx.com.domain.agent.service.memory.MemoryCandidatePreselector;
import yhx.com.domain.agent.service.memory.MemoryManager;
import yhx.com.domain.agent.service.memory.MemoryVectorIndexingService;
import yhx.com.domain.agent.service.memory.NoopVectorMemoryRepository;
import yhx.com.domain.agent.service.memory.TurnCompletionPublisher;
import yhx.com.domain.agent.service.memory.VectorContextRecallPreselector;
import yhx.com.domain.agent.service.memory.gc.MemoryGcOrchestrator;
import yhx.com.domain.agent.service.memory.gc.MemoryGcFollowupScheduler;
import yhx.com.domain.agent.service.memory.gc.MemoryGcRetryService;
import yhx.com.domain.agent.service.memory.gc.MemoryGcTaskQueryService;
import yhx.com.domain.agent.service.memory.gc.MemoryGcTaskDispatcher;
import yhx.com.domain.agent.service.memory.gc.worker.MemoryGcTaskWorker;
import yhx.com.domain.agent.service.memory.gc.worker.ConversationRollupGcWorker;
import yhx.com.domain.agent.service.memory.gc.worker.LongTermMemoryGcWorker;
import yhx.com.domain.agent.service.memory.gc.worker.MemoryGovernanceGcWorker;
import yhx.com.domain.agent.service.memory.gc.worker.SessionTaskSummaryGcWorker;
import yhx.com.domain.agent.service.memory.gc.worker.TurnSummaryGcWorker;
import yhx.com.domain.agent.service.memory.gc.worker.TurnSummarySelfCheckGcWorker;
import yhx.com.domain.agent.service.modelruntime.NodeRuntimeProfileResolver;
import yhx.com.domain.agent.service.agent.AgentCapabilityResolver;
import yhx.com.domain.agent.service.agent.AgentDispatchRuntime;
import yhx.com.domain.agent.service.agent.ChildAgentResultProjector;
import yhx.com.domain.agent.service.agent.GenericSubAgentDispatchOrchestrator;
import yhx.com.domain.agent.service.agent.ParentChildRunRegistry;
import yhx.com.domain.agent.service.agent.ParentRunResumePort;
import yhx.com.domain.agent.service.agent.PayloadBackedParentChildRunRegistryStore;
import yhx.com.domain.agent.service.agent.PayloadBackedSubAgentFullContextStore;
import yhx.com.domain.agent.service.agent.SubAgentLifecycleEventPublisher;
import yhx.com.domain.agent.service.prompt.PromptAssembler;
import yhx.com.domain.agent.service.prompt.PromptContentProvider;
import yhx.com.domain.agent.service.prompt.RepositoryPromptContentProvider;
import yhx.com.domain.agent.service.prompt.StaticPromptContentProvider;
import yhx.com.domain.agent.service.rag.runtime.RagEvidenceConverter;
import yhx.com.domain.agent.service.rag.runtime.RagEvidenceSnippetPolicy;
import yhx.com.domain.agent.service.rag.runtime.RagRuntime;
import yhx.com.domain.agent.service.rag.runtime.RagRetrieverPort;
import yhx.com.domain.agent.service.rag.runtime.RagVerificationRouter;
import yhx.com.domain.agent.service.rag.runtime.RagVerifierInputBuilder;
import yhx.com.domain.agent.service.rag.RagAssetIngestionService;
import yhx.com.domain.agent.service.rag.RagAssetAnalyzer;
import yhx.com.domain.agent.service.rag.RagContextRecallPreselector;
import yhx.com.domain.agent.service.rag.DeterministicRagAssetAnalyzer;
import yhx.com.domain.agent.service.rag.RagParagraphChunker;
import yhx.com.domain.agent.service.rag.RagVectorIndexingService;
import yhx.com.domain.agent.service.runtime.AutoAgentRuntimeService;
import yhx.com.domain.agent.service.runtime.DefaultAutoAgentRuntimeService;
import yhx.com.domain.agent.service.runtime.DefaultRuntimeComponentPorts;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.domain.agent.service.runtime.MainActionHandler;
import yhx.com.domain.agent.service.runtime.PayloadBackedPlanStatePort;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RunTranscriptRecorder;
import yhx.com.domain.agent.service.runtime.RuntimeComponentPorts;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;
import yhx.com.domain.agent.service.runtime.RuntimePhaseGuard;
import yhx.com.domain.agent.service.runtime.RuntimeStateMachine;
import yhx.com.domain.agent.service.runtime.RunDiagnosticRecorder;
import yhx.com.domain.agent.service.runtime.RunWorkingStateManager;
import yhx.com.domain.agent.service.runtime.handler.AskUserActionHandler;
import yhx.com.domain.agent.service.runtime.handler.CallToolActionHandler;
import yhx.com.domain.agent.service.runtime.handler.ContinueActionHandler;
import yhx.com.domain.agent.service.runtime.handler.DefaultMainActionDispatcher;
import yhx.com.domain.agent.service.runtime.handler.DelegateAgentsActionHandler;
import yhx.com.domain.agent.service.runtime.handler.FailActionHandler;
import yhx.com.domain.agent.service.runtime.handler.FinalActionHandler;
import yhx.com.domain.agent.service.runtime.handler.MainActionHandlerRegistry;
import yhx.com.domain.agent.service.runtime.handler.PlanActionHandler;
import yhx.com.domain.agent.service.runtime.handler.RepairFinalActionHandler;
import yhx.com.domain.agent.service.runtime.handler.RetrieveRagActionHandler;
import yhx.com.domain.agent.service.runtime.port.FinalDeliveryPort;
import yhx.com.domain.agent.service.runtime.port.PlanStatePort;
import yhx.com.domain.agent.service.runtime.port.RagRuntimePort;
import yhx.com.domain.agent.service.runtime.port.ToolActionOrchestratorPort;
import yhx.com.domain.agent.service.tool.CapabilityRegistry;
import yhx.com.domain.agent.service.tool.McpToolRegistry;
import yhx.com.domain.agent.service.tool.ToolApprovalService;
import yhx.com.infrastructure.adapter.repository.AsyncFileRunDiagnosticRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@ConditionalOnProperty(prefix = "auto-agent.runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({AutoAgentRuntimeProperties.class, AutoAgentContextProperties.class, AutoAgentRagProperties.class})
public class AutoAgentRuntimeConfig {

    @Bean
    public RuntimeFailureFactory runtimeFailureFactory() {
        return new RuntimeFailureFactory();
    }

    @Bean(destroyMethod = "close")
    public IRunDiagnosticRepository runDiagnosticRepository() {
        return new AsyncFileRunDiagnosticRepository(Path.of("data", "log", "agent-run-trace"), 8192);
    }

    @Bean
    public RunDiagnosticRecorder runDiagnosticRecorder(IRunDiagnosticRepository runDiagnosticRepository) {
        return new RunDiagnosticRecorder(runDiagnosticRepository);
    }

    @Bean
    public RuntimeLoopPolicy runtimeLoopPolicy(AutoAgentRuntimeProperties properties) {
        return new RuntimeLoopPolicy(properties.getMaxLoopCount(),
                properties.getMaxContractRepairAttempts(),
                properties.getMaxFinalRepairAttempts(),
                properties.getMaxRecoveryAttemptsPerFailureCode(),
                properties.getMaxRecoveryAttemptsPerFailureCode(),
                properties.getMaxRecoveryAttemptsPerFailureCode());
    }

    @Bean
    public RuntimeStateMachine runtimeStateMachine() {
        return new RuntimeStateMachine();
    }

    @Bean
    public RunEventPublisher runEventPublisher(IEventTraceRepository eventTraceRepository,
                                               IPayloadRepository payloadRepository,
                                               RunDiagnosticRecorder runDiagnosticRecorder) {
        return new RunEventPublisher(eventTraceRepository, payloadRepository, runDiagnosticRecorder);
    }

    @Bean
    public RunTranscriptRecorder runTranscriptRecorder(IRunTranscriptRepository transcriptRepository, IPayloadRepository payloadRepository) {
        return new RunTranscriptRecorder(transcriptRepository, payloadRepository);
    }

    @Bean
    public DeveloperTraceRecorder developerTraceRecorder(IEventTraceRepository eventTraceRepository,
                                                         IPayloadRepository payloadRepository,
                                                         RunDiagnosticRecorder runDiagnosticRecorder) {
        return new DeveloperTraceRecorder(eventTraceRepository, payloadRepository, runDiagnosticRecorder);
    }

    @Bean
    public RuntimePhaseGuard runtimePhaseGuard(RuntimeStateMachine stateMachine,
                                               RuntimeFailureFactory failureFactory,
                                               DeveloperTraceRecorder traceRecorder) {
        return new RuntimePhaseGuard(stateMachine, failureFactory, traceRecorder);
    }

    @Bean
    public PromptContentProvider promptContentProvider(ObjectProvider<INodePromptRepository> nodePromptRepositoryProvider,
                                                       IPayloadRepository payloadRepository) {
        INodePromptRepository repository = nodePromptRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return new StaticPromptContentProvider();
        }
        return new RepositoryPromptContentProvider(repository, payloadRepository);
    }

    @Bean
    public PromptAssembler promptAssembler(PromptContentProvider promptContentProvider) {
        return new PromptAssembler(promptContentProvider);
    }

    @Bean
    public NodeInvocationPipeline nodeInvocationPipeline(PromptAssembler promptAssembler,
                                                         INodeClientPort nodeClientPort,
                                                         RunDiagnosticRecorder runDiagnosticRecorder) {
        return new NodeInvocationPipeline(promptAssembler, nodeClientPort, runDiagnosticRecorder);
    }

    @Bean
    public NodeRuntimeProfileResolver nodeRuntimeProfileResolver(IModelRuntimeRepository modelRuntimeRepository) {
        return new NodeRuntimeProfileResolver(modelRuntimeRepository);
    }

    @Bean
    public ContextTokenEstimator contextTokenEstimator() {
        return new ContextTokenEstimator();
    }

    @Bean
    public ArtifactManager artifactManager(IArtifactRepository artifactRepository, IPayloadRepository payloadRepository) {
        return new ArtifactManager(artifactRepository, payloadRepository);
    }

    @Bean
    public ArtifactPayloadLoader artifactPayloadLoader(IPayloadRepository payloadRepository, ContextTokenEstimator tokenEstimator) {
        return new ArtifactPayloadLoader(payloadRepository, tokenEstimator);
    }

    @Bean
    public ContextBudgetManager contextBudgetManager(ContextTokenEstimator tokenEstimator) {
        return new ContextBudgetManager(tokenEstimator);
    }

    @Bean
    public ContextCandidatePreselector contextCandidatePreselector(IConversationRepository conversationRepository,
                                                                   IArtifactRepository artifactRepository,
                                                                   IMemoryRepository memoryRepository,
                                                                   IEvidenceRepository evidenceRepository,
                                                                   IPayloadRepository payloadRepository,
                                                                   ITurnRepository turnRepository,
                                                                   ITurnSummaryRepository turnSummaryRepository,
                                                                   ISessionTaskSummaryRepository sessionTaskSummaryRepository) {
        return new ContextCandidatePreselector(conversationRepository,
                artifactRepository,
                memoryRepository,
                evidenceRepository,
                payloadRepository,
                turnRepository,
                turnSummaryRepository,
                sessionTaskSummaryRepository);
    }

    @Bean
    @ConditionalOnMissingBean(IVectorMemoryRepository.class)
    public IVectorMemoryRepository vectorMemoryRepository() {
        return new NoopVectorMemoryRepository();
    }

    @Bean
    public MemoryVectorIndexingService memoryVectorIndexingService(IVectorMemoryRepository vectorMemoryRepository,
                                                                   IVectorIndexRepository vectorIndexRepository,
                                                                   IPayloadRepository payloadRepository) {
        return new MemoryVectorIndexingService(vectorMemoryRepository, vectorIndexRepository, payloadRepository);
    }

    @Bean
    public MemoryManager memoryManager(IMemoryRepository memoryRepository,
                                       MemoryVectorIndexingService memoryVectorIndexingService) {
        return new MemoryManager(memoryRepository, new MemoryCandidatePreselector(), memoryVectorIndexingService);
    }

    @Bean
    public VectorContextRecallPreselector vectorContextRecallPreselector(IVectorMemoryRepository vectorMemoryRepository,
                                                                         ITurnSummaryRepository turnSummaryRepository,
                                                                         IArtifactRepository artifactRepository,
                                                                         IMemoryRepository memoryRepository,
                                                                         IPayloadRepository payloadRepository) {
        return new VectorContextRecallPreselector(vectorMemoryRepository,
                turnSummaryRepository,
                artifactRepository,
                memoryRepository,
                payloadRepository);
    }

    @Bean
    public RagVectorIndexingService ragVectorIndexingService(IVectorMemoryRepository vectorMemoryRepository,
                                                             IVectorIndexRepository vectorIndexRepository) {
        return new RagVectorIndexingService(vectorMemoryRepository, vectorIndexRepository);
    }

    @Bean
    public RagParagraphChunker ragParagraphChunker(AutoAgentRagProperties ragProperties) {
        return new RagParagraphChunker(ragProperties.getAsset().getChunkMaxChars(),
                ragProperties.getAsset().getChunkOverlapChars());
    }

    @Bean
    public RagAssetIngestionService ragAssetIngestionService(IRagAssetRepository ragAssetRepository,
                                                             IPayloadRepository payloadRepository,
                                                             RagParagraphChunker ragParagraphChunker,
                                                             RagVectorIndexingService ragVectorIndexingService,
                                                             RagAssetAnalyzer ragAssetAnalyzer) {
        return new RagAssetIngestionService(ragAssetRepository,
                payloadRepository,
                ragParagraphChunker,
                ragVectorIndexingService,
                ragAssetAnalyzer);
    }

    @Bean
    public RagAssetAnalyzer ragAssetAnalyzer(NodeInvocationPipeline nodeInvocationPipeline,
                                             NodeRuntimeProfileResolver nodeRuntimeProfileResolver) {
        try {
            return new RagAssetAnalyzerNodeService(nodeInvocationPipeline,
                    nodeRuntimeProfileResolver.resolveRequired(AgentComponentCodeEnumVO.RAG_ASSET_ANALYZER.name()));
        } catch (Exception ignored) {
            return new DeterministicRagAssetAnalyzer();
        }
    }

    @Bean
    public RagContextRecallPreselector ragContextRecallPreselector(IVectorMemoryRepository vectorMemoryRepository,
                                                                   IRagAssetRepository ragAssetRepository,
                                                                   AutoAgentRagProperties ragProperties) {
        return new RagContextRecallPreselector(vectorMemoryRepository,
                ragAssetRepository,
                ragProperties.getAsset().getRecallTopK(),
                ragProperties.getAsset().getRecallMinScore());
    }

    @Bean("autoAgentContextRecallExecutor")
    public Executor autoAgentContextRecallExecutor() {
        return Executors.newFixedThreadPool(4);
    }

    @Bean
    public ContextPreparationService contextPreparationService(ContextCandidatePreselector preselector,
                                                               VectorContextRecallPreselector vectorContextRecallPreselector,
                                                               RagContextRecallPreselector ragContextRecallPreselector,
                                                               @Qualifier("autoAgentContextRecallExecutor") Executor contextRecallExecutor,
                                                               AutoAgentContextProperties contextProperties,
                                                               AutoAgentRagProperties ragProperties) {
        return new ContextPreparationService(preselector,
                vectorContextRecallPreselector,
                ragContextRecallPreselector,
                contextRecallExecutor,
                Duration.ofMillis(Math.max(0, contextProperties.getVectorRecallTimeoutMillis())),
                Duration.ofMillis(Math.max(0, ragProperties.getAsset().getRecallTimeoutMillis())));
    }

    @Bean
    public MainAgentStateViewBuilder mainAgentStateViewBuilder() {
        return new MainAgentStateViewBuilder();
    }

    @Bean
    public ContextMaterializer contextMaterializer(ArtifactPayloadLoader artifactPayloadLoader,
                                                   ContextBudgetManager budgetManager,
                                                   MainAgentStateViewBuilder stateViewBuilder,
                                                   ITurnRepository turnRepository,
                                                   IPayloadRepository payloadRepository) {
        return new ContextMaterializer(new ContextSelectionValidator(),
                artifactPayloadLoader,
                new EvidencePackBuilder(),
                budgetManager,
                stateViewBuilder,
                turnRepository,
                payloadRepository);
    }

    @Bean
    public ContextPlannerNodeService contextPlannerNodeService(NodeInvocationPipeline nodeInvocationPipeline) {
        return new ContextPlannerNodeService(nodeInvocationPipeline);
    }

    @Bean
    public MainAgentNodeService mainAgentNodeService(NodeInvocationPipeline nodeInvocationPipeline,
                                                     NodeRuntimeProfileResolver nodeRuntimeProfileResolver,
                                                     AutoAgentRuntimeProperties properties) {
        var profile = nodeRuntimeProfileResolver.resolveRequired(AgentComponentCodeEnumVO.MAIN_AGENT.name());
        profile.setInvocationMode(properties.getMainAgentInvocationMode());
        return new MainAgentNodeService(nodeInvocationPipeline, profile);
    }

    @Bean
    public ContextPlannerStatusHandler contextPlannerStatusHandler(ContextMaterializer contextMaterializer,
                                                                   MainAgentStateViewBuilder stateViewBuilder) {
        return new ContextPlannerStatusHandler(contextMaterializer, stateViewBuilder);
    }

    @Bean
    public ToolCapabilityCandidateProjector toolCapabilityCandidateProjector() {
        return new ToolCapabilityCandidateProjector();
    }

    @Bean
    public RuntimeComponentPorts runtimeComponentPorts(ContextPreparationService contextPreparationService,
                                                       ContextPlannerNodeService contextPlannerNodeService,
                                                       ContextPlannerStatusHandler contextPlannerStatusHandler,
                                                       MainAgentNodeService mainAgentNodeService,
                                                       NodeRuntimeProfileResolver nodeRuntimeProfileResolver,
                                                       ObjectProvider<CapabilityRegistry> capabilityRegistryProvider,
                                                       ObjectProvider<McpToolRegistry> mcpToolRegistryProvider,
                                                       ToolCapabilityCandidateProjector capabilityProjector,
                                                       AutoAgentCapabilityProperties capabilityProperties) {
        return new DefaultRuntimeComponentPorts(contextPreparationService,
                contextPlannerNodeService,
                contextPlannerStatusHandler,
                mainAgentNodeService,
                nodeRuntimeProfileResolver.resolveAllActive(),
                capabilityCandidates(capabilityRegistryProvider.getIfAvailable(),
                        mcpToolRegistryProvider.getIfAvailable(), capabilityProjector, capabilityProperties),
                defaultTokenBudget());
    }

    @Bean
    public PendingInputManager pendingInputManager(IPendingInputRepository pendingInputRepository, IPayloadRepository payloadRepository) {
        return new PendingInputManager(pendingInputRepository, payloadRepository);
    }

    @Bean
    public RuntimeContinuationSnapshotService runtimeContinuationSnapshotService() {
        return new RuntimeContinuationSnapshotService();
    }

    @Bean
    public PendingInputPauseCoordinator pendingInputPauseCoordinator(PendingInputManager pendingInputManager,
                                                                     IRunRepository runRepository,
                                                                     RunEventPublisher eventPublisher,
                                                                     RuntimeContinuationSnapshotService snapshotService,
                                                                     IInteractionTransactionExecutor transactionExecutor) {
        return new PendingInputPauseCoordinator(pendingInputManager, runRepository, eventPublisher,
                snapshotService, transactionExecutor);
    }

    @Bean
    public UserReplyProcessor userReplyProcessor(IPayloadRepository payloadRepository) {
        return new UserReplyProcessor(payloadRepository);
    }

    @Bean
    public PendingInputContinuationDispatcher pendingInputContinuationDispatcher(ObjectProvider<ToolApprovalService> toolApprovalServiceProvider) {
        return new PendingInputContinuationDispatcher(List.of(
                new ContextPlannerPendingInputHandler(),
                new MainAgentPendingInputHandler(),
                new ToolApprovalPendingInputHandler(toolApprovalServiceProvider::getIfAvailable),
                new RagPendingInputHandler(),
                new SubAgentPendingInputHandler(),
                new FinalRepairPendingInputHandler()));
    }

    @Bean
    public UserInteractionManager userInteractionManager(PendingInputManager pendingInputManager,
                                                         UserReplyProcessor userReplyProcessor,
                                                         PendingInputContinuationDispatcher continuationDispatcher,
                                                         IPayloadRepository payloadRepository,
                                                         RunEventPublisher eventPublisher,
                                                         RunTranscriptRecorder transcriptRecorder,
                                                         RuntimeFailureFactory failureFactory,
                                                         RuntimeContinuationSnapshotService snapshotService,
                                                         PendingInputPauseCoordinator pauseCoordinator,
                                                         IPendingInputConsumptionRepository consumptionRepository) {
        return new UserInteractionManager(pendingInputManager,
                userReplyProcessor,
                continuationDispatcher,
                payloadRepository,
                eventPublisher,
                transcriptRecorder,
                failureFactory,
                snapshotService,
                pauseCoordinator,
                consumptionRepository);
    }

    @Bean
    public PlanStatePort planStatePort(IPayloadRepository payloadRepository, IEventTraceRepository eventTraceRepository) {
        return new PayloadBackedPlanStatePort(payloadRepository, eventTraceRepository);
    }

    @Bean
    public RagRuntimePort ragRuntimePort(IRunRepository runRepository,
                                         IRagExecutionRepository ragExecutionRepository,
                                         IPayloadRepository payloadRepository,
                                         IEvidenceRepository evidenceRepository,
                                         RagRetrieverPort ragRetrieverPort,
                                         RunEventPublisher eventPublisher,
                                         DeveloperTraceRecorder traceRecorder,
                                         RuntimeFailureFactory failureFactory,
                                         AutoAgentRagProperties ragProperties) {
        return new RagRuntime(runRepository,
                ragExecutionRepository,
                payloadRepository,
                evidenceRepository,
                ragRetrieverPort,
                new RagEvidenceConverter(),
                eventPublisher,
                traceRecorder,
                failureFactory,
                ragProperties.getMaxHitsPerQuery(),
                ragProperties.getMaxHitsPerQuery(),
                ragProperties.getMaxEvidenceSnippetChars());
    }

    @Bean
    public RagVerifierInputBuilder ragVerifierInputBuilder(IPayloadRepository payloadRepository) {
        return new RagVerifierInputBuilder(payloadRepository, new RagEvidenceSnippetPolicy());
    }

    @Bean
    public RagVerifierNodeService ragVerifierNodeService(NodeInvocationPipeline nodeInvocationPipeline,
                                                         NodeRuntimeProfileResolver nodeRuntimeProfileResolver,
                                                         AutoAgentRuntimeProperties properties) {
        return new RagVerifierNodeService(nodeInvocationPipeline,
                properties.getMaxContractRepairAttempts(),
                nodeRuntimeProfileResolver.resolveRequired(AgentComponentCodeEnumVO.RAG_VERIFIER.name()));
    }

    @Bean
    public TurnSummaryNodeService turnSummaryNodeService(NodeInvocationPipeline nodeInvocationPipeline,
                                                         NodeRuntimeProfileResolver nodeRuntimeProfileResolver) {
        return new TurnSummaryNodeService(nodeInvocationPipeline,
                nodeRuntimeProfileResolver.resolveRequired(AgentComponentCodeEnumVO.TURN_SUMMARY.name()));
    }

    @Bean
    public MemoryExtractionNodeService memoryExtractionNodeService(NodeInvocationPipeline nodeInvocationPipeline,
                                                                   NodeRuntimeProfileResolver nodeRuntimeProfileResolver) {
        return new MemoryExtractionNodeService(nodeInvocationPipeline,
                nodeRuntimeProfileResolver.resolveRequired(AgentComponentCodeEnumVO.MEMORY_EXTRACTOR.name()));
    }

    @Bean
    public SessionTaskSummaryNodeService sessionTaskSummaryNodeService(NodeInvocationPipeline nodeInvocationPipeline,
                                                                       NodeRuntimeProfileResolver nodeRuntimeProfileResolver) {
        return new SessionTaskSummaryNodeService(nodeInvocationPipeline,
                nodeRuntimeProfileResolver.resolveRequired(AgentComponentCodeEnumVO.SESSION_TASK_SUMMARY.name()));
    }

    @Bean
    public MemoryGovernanceNodeService memoryGovernanceNodeService(NodeInvocationPipeline nodeInvocationPipeline,
                                                                   NodeRuntimeProfileResolver nodeRuntimeProfileResolver) {
        return new MemoryGovernanceNodeService(nodeInvocationPipeline,
                nodeRuntimeProfileResolver.resolveRequired(AgentComponentCodeEnumVO.MEMORY_GOVERNANCE.name()));
    }

    @Bean
    public ConversationRollupNodeService conversationRollupNodeService(NodeInvocationPipeline nodeInvocationPipeline,
                                                                       NodeRuntimeProfileResolver nodeRuntimeProfileResolver) {
        return new ConversationRollupNodeService(nodeInvocationPipeline,
                nodeRuntimeProfileResolver.resolveRequired(AgentComponentCodeEnumVO.CONVERSATION_ROLLUP.name()));
    }

    @Bean("autoAgentMemoryTaskExecutor")
    public Executor autoAgentMemoryTaskExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    @Bean
    public MemoryGcFollowupScheduler memoryGcFollowupScheduler(IMemoryTaskRepository memoryTaskRepository,
                                                               @Qualifier("autoAgentMemoryTaskExecutor") Executor memoryTaskExecutor,
                                                               ObjectProvider<MemoryGcTaskWorker> workerProvider) {
        return new MemoryGcFollowupScheduler(memoryTaskRepository,
                memoryTaskExecutor,
                () -> workerProvider.stream().toList());
    }

    @Bean
    public TurnSummaryGcWorker turnSummaryGcWorker(ITurnRepository turnRepository,
                                                   ITurnSummaryRepository turnSummaryRepository,
                                                   IMemoryTaskRepository memoryTaskRepository,
                                                   IPayloadRepository payloadRepository,
                                                   TurnSummaryNodeService turnSummaryNodeService,
                                                   MemoryVectorIndexingService memoryVectorIndexingService,
                                                   MemoryGcFollowupScheduler memoryGcFollowupScheduler) {
        return new TurnSummaryGcWorker(turnRepository,
                turnSummaryRepository,
                memoryTaskRepository,
                payloadRepository,
                turnSummaryNodeService,
                memoryVectorIndexingService,
                memoryGcFollowupScheduler);
    }

    @Bean
    public TurnSummarySelfCheckGcWorker turnSummarySelfCheckGcWorker(ITurnRepository turnRepository,
                                                                     ITurnSummaryRepository turnSummaryRepository,
                                                                     IMemoryTaskRepository memoryTaskRepository,
                                                                     MemoryGcFollowupScheduler memoryGcFollowupScheduler) {
        return new TurnSummarySelfCheckGcWorker(turnRepository,
                turnSummaryRepository,
                memoryTaskRepository,
                memoryGcFollowupScheduler,
                50);
    }

    @Bean
    public LongTermMemoryGcWorker longTermMemoryGcWorker(ITurnRepository turnRepository,
                                                         IMemoryTaskRepository memoryTaskRepository,
                                                         IPayloadRepository payloadRepository,
                                                         MemoryManager memoryManager,
                                                         MemoryExtractionNodeService memoryExtractionNodeService) {
        return new LongTermMemoryGcWorker(turnRepository,
                memoryTaskRepository,
                payloadRepository,
                memoryManager,
                memoryExtractionNodeService);
    }

    @Bean
    public SessionTaskSummaryGcWorker sessionTaskSummaryGcWorker(ITurnSummaryRepository turnSummaryRepository,
                                                                 IMemoryTaskRepository memoryTaskRepository,
                                                                 IPayloadRepository payloadRepository,
                                                                 ISessionTaskSummaryRepository sessionTaskSummaryRepository,
                                                                 SessionTaskSummaryNodeService sessionTaskSummaryNodeService) {
        return new SessionTaskSummaryGcWorker(turnSummaryRepository,
                memoryTaskRepository,
                payloadRepository,
                sessionTaskSummaryRepository,
                sessionTaskSummaryNodeService,
                30);
    }

    @Bean
    public MemoryGovernanceGcWorker memoryGovernanceGcWorker(IMemoryRepository memoryRepository,
                                                             IMemoryTaskRepository memoryTaskRepository,
                                                             IPayloadRepository payloadRepository,
                                                             IVectorMemoryRepository vectorMemoryRepository,
                                                             IVectorIndexRepository vectorIndexRepository,
                                                             MemoryGovernanceNodeService memoryGovernanceNodeService) {
        return new MemoryGovernanceGcWorker(memoryRepository,
                memoryTaskRepository,
                payloadRepository,
                vectorMemoryRepository,
                vectorIndexRepository,
                memoryGovernanceNodeService,
                50);
    }

    @Bean
    public ConversationRollupGcWorker conversationRollupGcWorker(ITurnSummaryRepository turnSummaryRepository,
                                                                 IMemoryTaskRepository memoryTaskRepository,
                                                                 IPayloadRepository payloadRepository,
                                                                 MemoryManager memoryManager,
                                                                 ConversationRollupNodeService conversationRollupNodeService) {
        return new ConversationRollupGcWorker(turnSummaryRepository,
                memoryTaskRepository,
                payloadRepository,
                memoryManager,
                conversationRollupNodeService,
                12);
    }

    @Bean
    public MemoryGcTaskDispatcher memoryGcTaskDispatcher(@Qualifier("autoAgentMemoryTaskExecutor") Executor memoryTaskExecutor,
                                                         List<MemoryGcTaskWorker> workers) {
        return new MemoryGcTaskDispatcher(memoryTaskExecutor, workers);
    }

    @Bean
    public MemoryGcRetryService memoryGcRetryService(IMemoryTaskRepository memoryTaskRepository,
                                                     MemoryGcTaskDispatcher memoryGcTaskDispatcher) {
        return new MemoryGcRetryService(memoryTaskRepository, memoryGcTaskDispatcher);
    }

    @Bean
    public MemoryGcTaskQueryService memoryGcTaskQueryService(IMemoryTaskRepository memoryTaskRepository) {
        return new MemoryGcTaskQueryService(memoryTaskRepository);
    }

    @Bean
    public TurnCompletionPublisher turnCompletionPublisher(IMemoryTaskRepository memoryTaskRepository,
                                                           MemoryGcTaskDispatcher memoryGcTaskDispatcher) {
        return new MemoryGcOrchestrator(memoryTaskRepository, memoryGcTaskDispatcher);
    }

    @Bean
    public RagVerificationRouter ragVerificationRouter(IRagExecutionRepository ragExecutionRepository,
                                                       IEvidenceRepository evidenceRepository,
                                                       RagVerifierInputBuilder inputBuilder,
                                                       RagVerifierNodeService verifierNodeService) {
        return new RagVerificationRouter(ragExecutionRepository, evidenceRepository, inputBuilder, verifierNodeService);
    }

    @Bean
    public MainActionHandlerRegistry mainActionHandlerRegistry(List<MainActionHandler> handlers) {
        return new MainActionHandlerRegistry(handlers);
    }

    @Bean
    public MainActionDispatcher mainActionDispatcher(MainActionHandlerRegistry handlerRegistry,
                                                     RuntimeFailureFactory failureFactory,
                                                     DeveloperTraceRecorder traceRecorder) {
        return new DefaultMainActionDispatcher(handlerRegistry, ContractValidator.defaultValidator(), failureFactory, traceRecorder);
    }

    @Bean
    public FinalActionHandler finalActionHandler(FinalDeliveryPort finalDeliveryPort,
                                                 RuntimeFailureFactory failureFactory,
                                                 DeveloperTraceRecorder traceRecorder) {
        return new FinalActionHandler(finalDeliveryPort, failureFactory, traceRecorder);
    }

    @Bean
    public AskUserActionHandler askUserActionHandler(RuntimeFailureFactory failureFactory,
                                                     DeveloperTraceRecorder traceRecorder) {
        return new AskUserActionHandler(failureFactory, traceRecorder);
    }

    @Bean
    public RetrieveRagActionHandler retrieveRagActionHandler(IRunRepository runRepository,
                                                             RagRuntimePort ragRuntimePort,
                                                             RunEventPublisher eventPublisher,
                                                             RuntimeFailureFactory failureFactory,
                                                             DeveloperTraceRecorder traceRecorder) {
        return new RetrieveRagActionHandler(runRepository, ragRuntimePort, eventPublisher, failureFactory, traceRecorder);
    }

    @Bean
    public CallToolActionHandler callToolActionHandler(ObjectProvider<ToolActionOrchestratorPort> toolActionOrchestratorPortProvider,
                                                       RuntimeFailureFactory failureFactory,
                                                       DeveloperTraceRecorder traceRecorder) {
        return new CallToolActionHandler(toolActionOrchestratorPortProvider.getIfAvailable(), failureFactory, traceRecorder);
    }

    @Bean
    public PlanActionHandler planActionHandler(PlanStatePort planStatePort,
                                               RuntimeFailureFactory failureFactory,
                                               DeveloperTraceRecorder traceRecorder) {
        return new PlanActionHandler(planStatePort, failureFactory, traceRecorder);
    }

    @Bean
    public ContinueActionHandler continueActionHandler(RuntimeLoopPolicy loopPolicy,
                                                       RuntimeFailureFactory failureFactory,
                                                       DeveloperTraceRecorder traceRecorder) {
        return new ContinueActionHandler(loopPolicy, failureFactory, traceRecorder);
    }

    @Bean
    public RepairFinalActionHandler repairFinalActionHandler(FinalDeliveryPort finalDeliveryPort,
                                                             RuntimeFailureFactory failureFactory,
                                                             DeveloperTraceRecorder traceRecorder) {
        return new RepairFinalActionHandler(finalDeliveryPort, failureFactory, traceRecorder);
    }

    @Bean
    public FailActionHandler failActionHandler(FinalDeliveryPort finalDeliveryPort,
                                               RuntimeFailureFactory failureFactory,
                                               DeveloperTraceRecorder traceRecorder) {
        return new FailActionHandler(finalDeliveryPort, failureFactory, traceRecorder);
    }

    @Bean
    public ParentChildRunRegistry parentChildRunRegistry(IPayloadRepository payloadRepository) {
        return new ParentChildRunRegistry(new PayloadBackedParentChildRunRegistryStore(payloadRepository));
    }

    @Bean
    public AgentDispatchRuntime agentDispatchRuntime(ParentChildRunRegistry parentChildRunRegistry) {
        return new AgentDispatchRuntime(parentChildRunRegistry);
    }

    @Bean
    public GenericSubAgentNodeService genericSubAgentNodeService(NodeInvocationPipeline nodeInvocationPipeline,
                                                                NodeRuntimeProfileResolver nodeRuntimeProfileResolver) {
        return new GenericSubAgentNodeService(nodeInvocationPipeline,
                nodeRuntimeProfileResolver.resolveRequired(AgentComponentCodeEnumVO.GENERIC_SUB_AGENT.name()));
    }

    @Bean
    public ChildAgentResultProjector childAgentResultProjector() {
        return new ChildAgentResultProjector(new RunWorkingStateManager());
    }

    @Bean
    public SubAgentLifecycleEventPublisher subAgentLifecycleEventPublisher(RunEventPublisher eventPublisher) {
        return new SubAgentLifecycleEventPublisher(eventPublisher);
    }

    @Bean
    public ParentRunResumePort parentRunResumePort(ObjectProvider<AutoAgentRuntimeService> runtimeServiceProvider,
                                                   IRunRepository runRepository) {
        return new RuntimeParentRunResumePort(runtimeServiceProvider, runRepository);
    }

    @Bean
    public GenericSubAgentDispatchOrchestrator genericSubAgentDispatchOrchestrator(AgentDispatchRuntime agentDispatchRuntime,
                                                                                   ParentChildRunRegistry parentChildRunRegistry,
                                                                                   ChildAgentResultProjector childAgentResultProjector,
                                                                                   GenericSubAgentNodeService genericSubAgentNodeService,
                                                                                   ToolActionOrchestratorPort toolActionOrchestratorPort,
                                                                                   RagRuntimePort ragRuntimePort,
                                                                                   UserInteractionManager userInteractionManager,
                                                                                   IPayloadRepository payloadRepository,
                                                                                   SubAgentLifecycleEventPublisher subAgentLifecycleEventPublisher,
                                                                                   ParentRunResumePort parentRunResumePort) {
        return new GenericSubAgentDispatchOrchestrator(agentDispatchRuntime,
                parentChildRunRegistry,
                childAgentResultProjector,
                Map.of(),
                genericSubAgentNodeService,
                new AgentCapabilityResolver(),
                toolActionOrchestratorPort,
                ragRuntimePort,
                userInteractionManager,
                new PayloadBackedSubAgentFullContextStore(payloadRepository),
                subAgentLifecycleEventPublisher,
                parentRunResumePort,
                null);
    }

    @Bean
    public DelegateAgentsActionHandler delegateAgentsActionHandler(AgentDispatchRuntime agentDispatchRuntime,
                                                                   GenericSubAgentDispatchOrchestrator genericSubAgentDispatchOrchestrator,
                                                                   RuntimeFailureFactory failureFactory,
                                                                   DeveloperTraceRecorder traceRecorder) {
        return new DelegateAgentsActionHandler(agentDispatchRuntime,
                genericSubAgentDispatchOrchestrator,
                failureFactory,
                traceRecorder);
    }

    @Bean
    public AutoAgentRuntimeService autoAgentRuntimeService(IConversationRepository conversationRepository,
                                                           IRunRepository runRepository,
                                                           IPayloadRepository payloadRepository,
                                                           RuntimeComponentPorts componentPorts,
                                                           MainActionDispatcher actionDispatcher,
                                                           UserInteractionManager userInteractionManager,
                                                           RuntimeLoopPolicy loopPolicy,
                                                           RuntimeStateMachine stateMachine,
                                                           RuntimeFailureFactory failureFactory,
                                                           RuntimePhaseGuard phaseGuard,
                                                           RunEventPublisher eventPublisher,
                                                           RunTranscriptRecorder transcriptRecorder,
                                                           DeveloperTraceRecorder traceRecorder,
                                                           RunDiagnosticRecorder runDiagnosticRecorder,
                                                           ParentChildRunRegistry parentChildRunRegistry,
                                                           GenericSubAgentDispatchOrchestrator genericSubAgentDispatchOrchestrator) {
        return new DefaultAutoAgentRuntimeService(conversationRepository,
                runRepository,
                payloadRepository,
                componentPorts,
                actionDispatcher,
                userInteractionManager,
                loopPolicy,
                null,
                stateMachine,
                failureFactory,
                phaseGuard,
                eventPublisher,
                transcriptRecorder,
                traceRecorder,
                runDiagnosticRecorder,
                parentChildRunRegistry,
                genericSubAgentDispatchOrchestrator);
    }

    private List<CapabilityCandidateVO> capabilityCandidates(CapabilityRegistry capabilityRegistry,
                                                              McpToolRegistry mcpToolRegistry,
                                                              ToolCapabilityCandidateProjector projector,
                                                              AutoAgentCapabilityProperties properties) {
        if (capabilityRegistry == null || projector == null) {
            return List.of();
        }
        return projector.projectAll(capabilityRegistry.listEnabledCapabilities(), mcpToolRegistry,
                exposurePolicy(properties == null ? null : properties.getPromptExposure()));
    }

    private ToolCapabilityExposurePolicyVO exposurePolicy(AutoAgentCapabilityProperties.PromptExposureProperties properties) {
        if (properties == null) {
            return ToolCapabilityExposurePolicyVO.builder().build();
        }
        return ToolCapabilityExposurePolicyVO.builder()
                .maxTools(properties.getMaxTools())
                .maxDescriptionChars(properties.getMaxDescriptionChars())
                .maxSchemaDepth(properties.getMaxSchemaDepth())
                .maxSchemaPropertiesPerTool(properties.getMaxSchemaPropertiesPerTool())
                .maxSchemaCharsPerTool(properties.getMaxSchemaCharsPerTool())
                .maxTotalSchemaChars(properties.getMaxTotalSchemaChars())
                .maxRequiredArgumentsPerTool(properties.getMaxRequiredArgumentsPerTool())
                .maxCapabilityCharsPerTool(properties.getMaxCapabilityCharsPerTool())
                .maxTotalCapabilityChars(properties.getMaxTotalCapabilityChars())
                .build();
    }

    private TokenBudgetVO defaultTokenBudget() {
        return TokenBudgetVO.builder()
                .maxStateViewTokens(6000)
                .reservedOutputTokens(1000)
                .maxArtifactInlineChars(4000)
                .maxEvidenceSummaryChars(800)
                .overBudget(false)
                .build();
    }

}
