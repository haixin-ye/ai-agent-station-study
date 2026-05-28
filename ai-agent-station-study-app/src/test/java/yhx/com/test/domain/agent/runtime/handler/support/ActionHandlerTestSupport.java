package yhx.com.test.domain.agent.runtime.handler.support;

import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.FinalDeliveryStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RagRuntimeStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalDeliveryResultVO;
import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeResultVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.ToolActionCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.ToolActionResultVO;
import yhx.com.domain.agent.service.contract.ContractValidator;
import yhx.com.domain.agent.service.interaction.ContextPlannerPendingInputHandler;
import yhx.com.domain.agent.service.interaction.FinalRepairPendingInputHandler;
import yhx.com.domain.agent.service.interaction.MainAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.PendingInputContinuationDispatcher;
import yhx.com.domain.agent.service.interaction.PendingInputManager;
import yhx.com.domain.agent.service.interaction.RagPendingInputHandler;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;
import yhx.com.domain.agent.service.interaction.UserReplyProcessor;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.domain.agent.service.runtime.MainActionHandler;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RunTranscriptRecorder;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;
import yhx.com.domain.agent.service.runtime.handler.AskUserActionHandler;
import yhx.com.domain.agent.service.runtime.handler.CallToolActionHandler;
import yhx.com.domain.agent.service.runtime.handler.ContinueActionHandler;
import yhx.com.domain.agent.service.runtime.handler.DefaultMainActionDispatcher;
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
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ActionHandlerTestSupport {

    public static RuntimeExecutionContext context() {
        return RuntimeExecutionContext.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .userId("user-001")
                .loopIndex(0)
                .currentPhase(RuntimePhaseEnumVO.HANDLING_ACTION)
                .runtimeFacts(new LinkedHashMap<>())
                .build();
    }

    public static MainActionDispatcher dispatcher(FullRepository repository,
                                                  FakeFinalDeliveryPort finalPort,
                                                  FakeRagRuntimePort ragPort,
                                                  FakeToolActionOrchestratorPort toolPort,
                                                  FakePlanStatePort planPort) {
        RuntimeFailureFactory failureFactory = new RuntimeFailureFactory();
        DeveloperTraceRecorder traceRecorder = new DeveloperTraceRecorder(repository, repository);
        RunEventPublisher eventPublisher = new RunEventPublisher(repository, repository);
        RunTranscriptRecorder transcriptRecorder = new RunTranscriptRecorder(repository, repository);
        UserInteractionManager interactionManager = new UserInteractionManager(
                new PendingInputManager(repository, repository),
                new UserReplyProcessor(repository),
                new PendingInputContinuationDispatcher(List.of(
                        new ContextPlannerPendingInputHandler(),
                        new MainAgentPendingInputHandler(),
                        new ToolApprovalPendingInputHandler(),
                        new RagPendingInputHandler(),
                        new FinalRepairPendingInputHandler())),
                repository,
                eventPublisher,
                transcriptRecorder,
                failureFactory);
        List<MainActionHandler> handlers = handlers(repository, finalPort, ragPort, toolPort, planPort,
                failureFactory, traceRecorder, eventPublisher, interactionManager);
        return new DefaultMainActionDispatcher(new MainActionHandlerRegistry(handlers),
                ContractValidator.defaultValidator(), failureFactory, traceRecorder);
    }

    public static MainActionHandlerRegistry registry(FullRepository repository,
                                                     FakeFinalDeliveryPort finalPort,
                                                     FakeRagRuntimePort ragPort,
                                                     FakeToolActionOrchestratorPort toolPort,
                                                     FakePlanStatePort planPort) {
        RuntimeFailureFactory failureFactory = new RuntimeFailureFactory();
        DeveloperTraceRecorder traceRecorder = new DeveloperTraceRecorder(repository, repository);
        RunEventPublisher eventPublisher = new RunEventPublisher(repository, repository);
        UserInteractionManager interactionManager = new UserInteractionManager(
                new PendingInputManager(repository, repository),
                new UserReplyProcessor(repository),
                new PendingInputContinuationDispatcher(List.of(
                        new ContextPlannerPendingInputHandler(),
                        new MainAgentPendingInputHandler(),
                        new ToolApprovalPendingInputHandler(),
                        new RagPendingInputHandler(),
                        new FinalRepairPendingInputHandler())),
                repository,
                eventPublisher,
                new RunTranscriptRecorder(repository, repository),
                failureFactory);
        return new MainActionHandlerRegistry(handlers(repository, finalPort, ragPort, toolPort, planPort,
                failureFactory, traceRecorder, eventPublisher, interactionManager));
    }

    private static List<MainActionHandler> handlers(FullRepository repository,
                                                    FakeFinalDeliveryPort finalPort,
                                                    FakeRagRuntimePort ragPort,
                                                    FakeToolActionOrchestratorPort toolPort,
                                                    FakePlanStatePort planPort,
                                                    RuntimeFailureFactory failureFactory,
                                                    DeveloperTraceRecorder traceRecorder,
                                                    RunEventPublisher eventPublisher,
                                                    UserInteractionManager interactionManager) {
        return List.of(
                new FinalActionHandler(finalPort, failureFactory, traceRecorder),
                new AskUserActionHandler(interactionManager, failureFactory, traceRecorder),
                new RetrieveRagActionHandler(repository, ragPort, eventPublisher, failureFactory, traceRecorder),
                new CallToolActionHandler(toolPort, failureFactory, traceRecorder),
                new PlanActionHandler(planPort, failureFactory, traceRecorder),
                new ContinueActionHandler(new RuntimeLoopPolicy(), failureFactory, traceRecorder),
                new RepairFinalActionHandler(finalPort, failureFactory, traceRecorder),
                new FailActionHandler(finalPort, failureFactory, traceRecorder)
        );
    }

    public static class FullRepository extends RuntimeTestSupport.InMemoryRuntimeRepository implements IArtifactRepository {
        public final Map<String, AgentArtifactEntity> artifacts = new LinkedHashMap<>();

        @Override
        public String saveArtifact(AgentArtifactEntity artifact) {
            artifacts.put(artifact.getArtifactId(), artifact);
            return artifact.getArtifactId();
        }

        @Override
        public Optional<AgentArtifactEntity> findArtifact(String artifactId) {
            return Optional.ofNullable(artifacts.get(artifactId));
        }

        @Override
        public List<AgentArtifactEntity> findArtifactCandidates(String sessionId, String userInput, int limit) {
            return artifacts.values().stream().limit(limit).toList();
        }
    }

    public static class FakeFinalDeliveryPort implements FinalDeliveryPort {
        public FinalDeliveryStatusEnumVO status = FinalDeliveryStatusEnumVO.DELIVERED;
        public final List<FinalDeliveryCommandVO> calls = new ArrayList<>();

        @Override
        public FinalDeliveryResultVO deliver(FinalDeliveryCommandVO command) {
            calls.add(command);
            return FinalDeliveryResultVO.builder()
                    .status(status)
                    .finalMessageId("msg-final")
                    .finalAnswerRef("payload-final")
                    .deliveredContent(command.getFinalAnswerCandidate() == null ? null : command.getFinalAnswerCandidate().getContent())
                    .message("final delivered")
                    .build();
        }
    }

    public static class FakeRagRuntimePort implements RagRuntimePort {
        public RagRuntimeStatusEnumVO status = RagRuntimeStatusEnumVO.SUCCESS;
        public final List<RagRuntimeCommandVO> calls = new ArrayList<>();

        @Override
        public RagRuntimeResultVO retrieve(RagRuntimeCommandVO command) {
            calls.add(command);
            return RagRuntimeResultVO.builder()
                    .status(status)
                    .evidenceIds(List.of("evidence-001"))
                    .message("rag handled")
                    .build();
        }
    }

    public static class FakeToolActionOrchestratorPort implements ToolActionOrchestratorPort {
        public ToolActionStatusEnumVO status = ToolActionStatusEnumVO.CONTINUE_LOOP;
        public final List<ToolActionCommandVO> calls = new ArrayList<>();

        @Override
        public ToolActionResultVO handleToolAction(ToolActionCommandVO command) {
            calls.add(command);
            return ToolActionResultVO.builder()
                    .status(status)
                    .pendingInputId(status == ToolActionStatusEnumVO.WAITING_USER ? "pending-tool" : null)
                    .askUserRequest(status == ToolActionStatusEnumVO.WAITING_USER ? AskUserRequestVO.builder()
                            .question("approve?")
                            .inputMode("SINGLE_CHOICE")
                            .allowFreeText(false)
                            .options(List.of(Map.of("id", "approve", "label", "同意", "value", Map.of("decision", "APPROVED"))))
                            .build() : null)
                    .evidenceIds(List.of("tool-evidence-001"))
                    .message("tool handled")
                    .build();
        }
    }

    public static class FakePlanStatePort implements PlanStatePort {
        public final Map<String, yhx.com.domain.agent.model.valobj.runtime.PlanStateVO> plans = new LinkedHashMap<>();

        @Override
        public String savePlan(String runId, yhx.com.domain.agent.model.valobj.runtime.PlanStateVO plan) {
            plans.put(runId, plan);
            return "plan-" + runId;
        }

        @Override
        public yhx.com.domain.agent.model.valobj.runtime.PlanStateVO findPlan(String runId) {
            return plans.get(runId);
        }
    }
}
