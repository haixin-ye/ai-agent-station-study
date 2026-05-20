package yhx.com.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import yhx.com.domain.agent.adapter.port.INodeClientPort;
import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IEventTraceRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IModelRuntimeRepository;
import yhx.com.domain.agent.adapter.repository.INodePromptRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IPendingInputRepository;
import yhx.com.domain.agent.adapter.repository.IRagExecutionRepository;
import yhx.com.domain.agent.adapter.repository.IRunDiagnosticRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.adapter.repository.IRunTranscriptRepository;
import yhx.com.domain.agent.model.valobj.context.CapabilityCandidateVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.service.artifact.ArtifactManager;
import yhx.com.domain.agent.service.artifact.ArtifactPayloadLoader;
import yhx.com.domain.agent.service.context.ContextBudgetManager;
import yhx.com.domain.agent.service.context.ContextCandidatePreselector;
import yhx.com.domain.agent.service.context.ContextMaterializer;
import yhx.com.domain.agent.service.context.ContextPlannerNodeService;
import yhx.com.domain.agent.service.context.ContextPlannerStatusHandler;
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
import yhx.com.domain.agent.service.interaction.RagPendingInputHandler;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;
import yhx.com.domain.agent.service.interaction.UserReplyProcessor;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;
import yhx.com.domain.agent.service.modelruntime.NodeRuntimeProfileResolver;
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
import yhx.com.domain.agent.service.rag.runtime.RagVerifierNodeService;
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
import yhx.com.domain.agent.service.runtime.handler.AskUserActionHandler;
import yhx.com.domain.agent.service.runtime.handler.CallToolActionHandler;
import yhx.com.domain.agent.service.runtime.handler.ContinueActionHandler;
import yhx.com.domain.agent.service.runtime.handler.CreateArtifactActionHandler;
import yhx.com.domain.agent.service.runtime.handler.DefaultMainActionDispatcher;
import yhx.com.domain.agent.service.runtime.handler.FailActionHandler;
import yhx.com.domain.agent.service.runtime.handler.FinalActionHandler;
import yhx.com.domain.agent.service.runtime.handler.MainActionHandlerRegistry;
import yhx.com.domain.agent.service.runtime.handler.PlanActionHandler;
import yhx.com.domain.agent.service.runtime.handler.RepairFinalActionHandler;
import yhx.com.domain.agent.service.runtime.handler.RetrieveRagActionHandler;
import yhx.com.domain.agent.service.runtime.handler.UpdateArtifactActionHandler;
import yhx.com.domain.agent.service.runtime.port.FinalDeliveryPort;
import yhx.com.domain.agent.service.runtime.port.PlanStatePort;
import yhx.com.domain.agent.service.runtime.port.RagRuntimePort;
import yhx.com.domain.agent.service.runtime.port.ToolActionOrchestratorPort;
import yhx.com.domain.agent.service.tool.CapabilityRegistry;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
import yhx.com.infrastructure.adapter.repository.AsyncFileRunDiagnosticRepository;

import java.nio.file.Path;
import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "auto-agent.runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AutoAgentRuntimeProperties.class)
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
                                                                   IPayloadRepository payloadRepository) {
        return new ContextCandidatePreselector(conversationRepository, artifactRepository, memoryRepository, evidenceRepository, payloadRepository);
    }

    @Bean
    public ContextPreparationService contextPreparationService(ContextCandidatePreselector preselector) {
        return new ContextPreparationService(preselector);
    }

    @Bean
    public MainAgentStateViewBuilder mainAgentStateViewBuilder() {
        return new MainAgentStateViewBuilder();
    }

    @Bean
    public ContextMaterializer contextMaterializer(ArtifactPayloadLoader artifactPayloadLoader,
                                                   ContextBudgetManager budgetManager,
                                                   MainAgentStateViewBuilder stateViewBuilder) {
        return new ContextMaterializer(new ContextSelectionValidator(),
                artifactPayloadLoader,
                new EvidencePackBuilder(),
                budgetManager,
                stateViewBuilder);
    }

    @Bean
    public ContextPlannerNodeService contextPlannerNodeService(NodeInvocationPipeline nodeInvocationPipeline) {
        return new ContextPlannerNodeService(nodeInvocationPipeline);
    }

    @Bean
    public ContextPlannerStatusHandler contextPlannerStatusHandler(ContextMaterializer contextMaterializer,
                                                                   MainAgentStateViewBuilder stateViewBuilder) {
        return new ContextPlannerStatusHandler(contextMaterializer, stateViewBuilder);
    }

    @Bean
    public RuntimeComponentPorts runtimeComponentPorts(ContextPreparationService contextPreparationService,
                                                       ContextPlannerNodeService contextPlannerNodeService,
                                                       ContextPlannerStatusHandler contextPlannerStatusHandler,
                                                       NodeInvocationPipeline nodeInvocationPipeline,
                                                       NodeRuntimeProfileResolver nodeRuntimeProfileResolver,
                                                       ObjectProvider<CapabilityRegistry> capabilityRegistryProvider) {
        return new DefaultRuntimeComponentPorts(contextPreparationService,
                contextPlannerNodeService,
                contextPlannerStatusHandler,
                nodeInvocationPipeline,
                nodeRuntimeProfileResolver.resolveAllActive(),
                capabilityCandidates(capabilityRegistryProvider.getIfAvailable()),
                defaultTokenBudget());
    }

    @Bean
    public PendingInputManager pendingInputManager(IPendingInputRepository pendingInputRepository, IPayloadRepository payloadRepository) {
        return new PendingInputManager(pendingInputRepository, payloadRepository);
    }

    @Bean
    public UserReplyProcessor userReplyProcessor(IPayloadRepository payloadRepository) {
        return new UserReplyProcessor(payloadRepository);
    }

    @Bean
    public PendingInputContinuationDispatcher pendingInputContinuationDispatcher() {
        return new PendingInputContinuationDispatcher(List.of(
                new ContextPlannerPendingInputHandler(),
                new MainAgentPendingInputHandler(),
                new ToolApprovalPendingInputHandler(),
                new RagPendingInputHandler(),
                new FinalRepairPendingInputHandler()));
    }

    @Bean
    public UserInteractionManager userInteractionManager(PendingInputManager pendingInputManager,
                                                         UserReplyProcessor userReplyProcessor,
                                                         PendingInputContinuationDispatcher continuationDispatcher,
                                                         IPayloadRepository payloadRepository,
                                                         RunEventPublisher eventPublisher,
                                                         RunTranscriptRecorder transcriptRecorder,
                                                         RuntimeFailureFactory failureFactory) {
        return new UserInteractionManager(pendingInputManager,
                userReplyProcessor,
                continuationDispatcher,
                payloadRepository,
                eventPublisher,
                transcriptRecorder,
                failureFactory);
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
                                         RuntimeFailureFactory failureFactory) {
        return new RagRuntime(runRepository,
                ragExecutionRepository,
                payloadRepository,
                evidenceRepository,
                ragRetrieverPort,
                new RagEvidenceConverter(),
                eventPublisher,
                traceRecorder,
                failureFactory,
                5,
                10,
                1200);
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
    public CreateArtifactActionHandler createArtifactActionHandler(ArtifactManager artifactManager,
                                                                   FinalDeliveryPort finalDeliveryPort,
                                                                   RuntimeFailureFactory failureFactory,
                                                                   DeveloperTraceRecorder traceRecorder,
                                                                   RunEventPublisher eventPublisher) {
        return new CreateArtifactActionHandler(artifactManager, finalDeliveryPort, failureFactory, traceRecorder, eventPublisher);
    }

    @Bean
    public UpdateArtifactActionHandler updateArtifactActionHandler(ArtifactManager artifactManager,
                                                                   FinalDeliveryPort finalDeliveryPort,
                                                                   RuntimeFailureFactory failureFactory,
                                                                   DeveloperTraceRecorder traceRecorder,
                                                                   RunEventPublisher eventPublisher) {
        return new UpdateArtifactActionHandler(artifactManager, finalDeliveryPort, failureFactory, traceRecorder, eventPublisher);
    }

    @Bean
    public AskUserActionHandler askUserActionHandler(UserInteractionManager userInteractionManager,
                                                     RuntimeFailureFactory failureFactory,
                                                     DeveloperTraceRecorder traceRecorder) {
        return new AskUserActionHandler(userInteractionManager, failureFactory, traceRecorder);
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
                                                           RunDiagnosticRecorder runDiagnosticRecorder) {
        return new DefaultAutoAgentRuntimeService(conversationRepository,
                runRepository,
                payloadRepository,
                componentPorts,
                actionDispatcher,
                userInteractionManager,
                loopPolicy,
                stateMachine,
                failureFactory,
                phaseGuard,
                eventPublisher,
                transcriptRecorder,
                traceRecorder,
                runDiagnosticRecorder);
    }

    private List<CapabilityCandidateVO> capabilityCandidates(CapabilityRegistry capabilityRegistry) {
        if (capabilityRegistry == null) {
            return List.of();
        }
        return capabilityRegistry.listEnabledCapabilities().stream()
                .map(this::toCapabilityCandidate)
                .toList();
    }

    private CapabilityCandidateVO toCapabilityCandidate(CapabilitySpecVO spec) {
        return CapabilityCandidateVO.builder()
                .capabilityCode(spec.getCapabilityCode())
                .capabilityType(spec.getCapabilityType())
                .summary(summary(spec))
                .enabled(spec.getEnabled())
                .build();
    }

    private String summary(CapabilitySpecVO spec) {
        return String.format("tool=%s, permission=%s, approval=%s, risk=%s",
                spec.getToolName(),
                spec.getRequiredPermission() == null ? "NONE" : spec.getRequiredPermission().code(),
                spec.getApprovalPolicy() == null ? "NEVER" : spec.getApprovalPolicy().code(),
                spec.getRiskLevel());
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
