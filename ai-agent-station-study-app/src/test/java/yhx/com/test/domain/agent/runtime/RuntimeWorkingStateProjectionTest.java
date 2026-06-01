package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.runtime.DefaultAutoAgentRuntimeService;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RunTranscriptRecorder;
import yhx.com.domain.agent.service.runtime.RuntimeComponentPorts;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;
import yhx.com.domain.agent.service.runtime.RuntimePhaseGuard;
import yhx.com.domain.agent.service.runtime.RuntimeStateMachine;
import yhx.com.domain.agent.service.interaction.PendingInputManager;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;
import yhx.com.domain.agent.service.interaction.UserReplyProcessor;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.util.List;
import java.util.Map;

public class RuntimeWorkingStateProjectionTest {

    @Test
    public void action_effect_updates_working_state_without_refreshing_context() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        CapturingPorts ports = new CapturingPorts();
        DefaultAutoAgentRuntimeService runtime = runtime(repository, ports, actionDispatcher());

        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run-working-state")
                .sessionId("sess-working-state")
                .userId("user-001")
                .userInput("List desktop.")
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.COMPLETED, result.getStatus());
        Assert.assertEquals(2, ports.invokeCount);
        Assert.assertNotNull(ports.secondStateView);
        Assert.assertEquals(1, ports.secondStateView.getEvidencePack().size());
        Assert.assertTrue(ports.secondStateView.getEvidencePack().get(0).getSummary().contains("Desktop"));
        Assert.assertEquals(0, ports.refreshCount);
    }

    private DefaultAutoAgentRuntimeService runtime(RuntimeTestSupport.InMemoryRuntimeRepository repository,
                                                   RuntimeComponentPorts ports,
                                                   MainActionDispatcher dispatcher) {
        RuntimeFailureFactory failureFactory = new RuntimeFailureFactory();
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();
        DeveloperTraceRecorder traceRecorder = new DeveloperTraceRecorder(repository, repository);
        RunEventPublisher eventPublisher = new RunEventPublisher(repository, repository);
        RunTranscriptRecorder transcriptRecorder = new RunTranscriptRecorder(repository, repository);
        UserInteractionManager interactionManager = new UserInteractionManager(
                new PendingInputManager(repository, repository),
                new UserReplyProcessor(repository),
                RuntimeTestSupport.defaultContinuationDispatcher(),
                repository,
                eventPublisher,
                transcriptRecorder,
                failureFactory);
        return new DefaultAutoAgentRuntimeService(
                repository,
                repository,
                repository,
                ports,
                dispatcher,
                interactionManager,
                new RuntimeLoopPolicy(6, 1, 2, 1, 2, 2),
                stateMachine,
                failureFactory,
                new RuntimePhaseGuard(stateMachine, failureFactory, traceRecorder),
                eventPublisher,
                transcriptRecorder,
                traceRecorder);
    }

    private MainActionDispatcher actionDispatcher() {
        return (context, action) -> {
            if ("CALL_TOOL".equals(action.getAction())) {
                return MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                        .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                        .createdEvidenceIds(List.of("evidence-tool-1"))
                        .createdEvidence(List.of(MaterializedEvidenceVO.builder()
                                .evidenceId("evidence-tool-1")
                                .evidenceType("TOOL")
                                .sourceRef("tool-call-1")
                                .summary("Tool action succeeded: C:/Users/hp/Desktop/report.txt")
                                .boundedSnippet("Tool action succeeded: C:/Users/hp/Desktop/report.txt")
                                .build()))
                        .message("Tool flow completed.")
                        .build();
            }
            return MainActionHandlerResult.builder()
                    .status(MainActionHandlerStatusEnumVO.COMPLETED)
                    .nextPhase(RuntimePhaseEnumVO.COMPLETED)
                    .finalAnswerCandidate(FinalAnswerCandidateVO.builder()
                            .content("done")
                            .build())
                    .message("completed")
                    .build();
        };
    }

    private static class CapturingPorts implements RuntimeComponentPorts {

        private int refreshCount;
        private int invokeCount;
        private MainAgentStateViewVO secondStateView;

        @Override
        public ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context) {
            return ContextPlannerHandlingResult.builder()
                    .stateView(MainAgentStateViewVO.builder()
                            .evidencePack(List.of())
                            .build())
                    .effectiveSelections(List.of())
                    .build();
        }

        @Override
        public ContextPlannerHandlingResult refreshContext(RuntimeExecutionContext context) {
            refreshCount++;
            return ContextPlannerHandlingResult.builder()
                    .stateView(MainAgentStateViewVO.builder()
                            .evidencePack(List.of())
                            .build())
                    .effectiveSelections(List.of())
                    .build();
        }

        @Override
        public MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context) {
            invokeCount++;
            if (invokeCount == 1) {
                return MainAgentActionVO.builder()
                        .action("CALL_TOOL")
                        .stateDelta(Map.of("toolIntent", Map.of("toolName", "list_directory")))
                        .build();
            }
            secondStateView = context.getLastStateView();
            return MainAgentActionVO.builder()
                    .action("FINAL")
                    .stateDelta(Map.of("finalAnswer", "done"))
                    .build();
        }
    }
}
