package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionEffectStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.ActionEffectVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.interaction.UserInteractionManager;
import yhx.com.domain.agent.service.runtime.DefaultAutoAgentRuntimeService;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RunTranscriptRecorder;
import yhx.com.domain.agent.service.runtime.RuntimeComponentPorts;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;
import yhx.com.domain.agent.service.runtime.RuntimePhaseGuard;
import yhx.com.domain.agent.service.runtime.RunWorkingStateManager;
import yhx.com.domain.agent.service.runtime.RuntimeStateMachine;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RuntimeRepeatedActionGuardTest {

    @Test
    public void repeated_same_call_tool_action_stops_before_max_loop() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        RuntimeFailureFactory failureFactory = new RuntimeFailureFactory();
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();
        DeveloperTraceRecorder traceRecorder = new DeveloperTraceRecorder(repository, repository);
        RunEventPublisher eventPublisher = new RunEventPublisher(repository, repository);
        RunTranscriptRecorder transcriptRecorder = new RunTranscriptRecorder(repository, repository);
        UserInteractionManager interactionManager = new UserInteractionManager(
                new yhx.com.domain.agent.service.interaction.PendingInputManager(repository, repository),
                new yhx.com.domain.agent.service.interaction.UserReplyProcessor(repository),
                RuntimeTestSupport.defaultContinuationDispatcher(),
                repository,
                eventPublisher,
                transcriptRecorder,
                failureFactory);
        DefaultAutoAgentRuntimeService runtime = new DefaultAutoAgentRuntimeService(
                repository,
                repository,
                repository,
                repeatedToolPorts(),
                continueLoopDispatcher(),
                interactionManager,
                new RuntimeLoopPolicy(10, 1, 1, 1, 1, 1),
                stateMachine,
                failureFactory,
                new RuntimePhaseGuard(stateMachine, failureFactory, traceRecorder),
                eventPublisher,
                transcriptRecorder,
                traceRecorder);

        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run-repeat-tool")
                .sessionId("sess-repeat-tool")
                .userId("user-001")
                .userInput("List project directory.")
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.FAILED, result.getStatus());
        Assert.assertEquals(RuntimeFailureCodeEnumVO.TOOL_RETRY_EXHAUSTED, result.getSafeFailure().getFailureCode());
        Assert.assertTrue(repository.transcriptBlocks.size() < 10);
    }

    @Test
    public void repeated_same_tool_execution_stops_even_when_goal_text_changes() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        RuntimeFailureFactory failureFactory = new RuntimeFailureFactory();
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();
        DeveloperTraceRecorder traceRecorder = new DeveloperTraceRecorder(repository, repository);
        RunEventPublisher eventPublisher = new RunEventPublisher(repository, repository);
        RunTranscriptRecorder transcriptRecorder = new RunTranscriptRecorder(repository, repository);
        UserInteractionManager interactionManager = new UserInteractionManager(
                new yhx.com.domain.agent.service.interaction.PendingInputManager(repository, repository),
                new yhx.com.domain.agent.service.interaction.UserReplyProcessor(repository),
                RuntimeTestSupport.defaultContinuationDispatcher(),
                repository,
                eventPublisher,
                transcriptRecorder,
                failureFactory);
        DefaultAutoAgentRuntimeService runtime = new DefaultAutoAgentRuntimeService(
                repository,
                repository,
                repository,
                changingGoalToolPorts(),
                continueLoopDispatcher(),
                interactionManager,
                new RuntimeLoopPolicy(10, 1, 1, 1, 1, 1),
                stateMachine,
                failureFactory,
                new RuntimePhaseGuard(stateMachine, failureFactory, traceRecorder),
                eventPublisher,
                transcriptRecorder,
                traceRecorder);

        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run-repeat-tool-changing-goal")
                .sessionId("sess-repeat-tool-changing-goal")
                .userId("user-001")
                .userInput("Find 04_blue_train_ticket.txt.")
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.FAILED, result.getStatus());
        Assert.assertEquals(RuntimeFailureCodeEnumVO.TOOL_RETRY_EXHAUSTED, result.getSafeFailure().getFailureCode());
        Assert.assertTrue(repository.transcriptBlocks.size() < 10);
    }

    @Test
    public void successful_tool_action_is_projected_to_next_main_agent_state_view() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        RuntimeFailureFactory failureFactory = new RuntimeFailureFactory();
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();
        DeveloperTraceRecorder traceRecorder = new DeveloperTraceRecorder(repository, repository);
        RunEventPublisher eventPublisher = new RunEventPublisher(repository, repository);
        RunTranscriptRecorder transcriptRecorder = new RunTranscriptRecorder(repository, repository);
        UserInteractionManager interactionManager = new UserInteractionManager(
                new yhx.com.domain.agent.service.interaction.PendingInputManager(repository, repository),
                new yhx.com.domain.agent.service.interaction.UserReplyProcessor(repository),
                RuntimeTestSupport.defaultContinuationDispatcher(),
                repository,
                eventPublisher,
                transcriptRecorder,
                failureFactory);
        AtomicInteger dispatchCount = new AtomicInteger();
        AtomicInteger mainCallCount = new AtomicInteger();
        DefaultAutoAgentRuntimeService runtime = new DefaultAutoAgentRuntimeService(
                repository,
                repository,
                repository,
                toolThenFinalPorts(mainCallCount),
                (context, action) -> {
                    if ("FINAL".equals(action.getAction())) {
                        return MainActionHandlerResult.builder()
                                .status(MainActionHandlerStatusEnumVO.COMPLETED)
                                .nextPhase(RuntimePhaseEnumVO.COMPLETED)
                                .finalAnswerCandidate(FinalAnswerCandidateVO.builder()
                                        .content("The file has been written.")
                                        .format("text")
                                        .build())
                                .message("Final delivered.")
                                .build();
                    }
                    dispatchCount.incrementAndGet();
                    MaterializedEvidenceVO evidence = MaterializedEvidenceVO.builder()
                            .evidenceId("evidence-success")
                            .evidenceType("TOOL")
                            .summary("Tool action succeeded: wrote file")
                            .boundedSnippet("Tool action succeeded: wrote file")
                            .build();
                    return MainActionHandlerResult.builder()
                            .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                            .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                            .createdEvidenceIds(List.of("evidence-success"))
                            .createdEvidence(List.of(evidence))
                            .actionEffect(ActionEffectVO.builder()
                                    .action("CALL_TOOL")
                                    .status(ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED.name())
                                    .createdEvidenceIds(List.of("evidence-success"))
                                    .createdEvidence(List.of(evidence))
                                    .build())
                            .message("Tool execution proof passed.")
                            .build();
                },
                interactionManager,
                new RuntimeLoopPolicy(10, 1, 1, 2, 1, 1),
                stateMachine,
                failureFactory,
                new RuntimePhaseGuard(stateMachine, failureFactory, traceRecorder),
                eventPublisher,
                transcriptRecorder,
                traceRecorder);

        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run-repeat-success-tool")
                .sessionId("sess-repeat-success-tool")
                .userId("user-001")
                .userInput("Write file.")
                .build());

        Assert.assertEquals(result.getMessage(), RuntimeStepStatusEnumVO.COMPLETED, result.getStatus());
        Assert.assertEquals(1, dispatchCount.get());
        Assert.assertEquals(2, mainCallCount.get());
        Assert.assertEquals("The file has been written.", result.getFinalAnswer());
    }

    @Test
    public void repeated_successful_tool_action_returns_to_main_agent_without_second_dispatch() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        RuntimeFailureFactory failureFactory = new RuntimeFailureFactory();
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();
        DeveloperTraceRecorder traceRecorder = new DeveloperTraceRecorder(repository, repository);
        RunEventPublisher eventPublisher = new RunEventPublisher(repository, repository);
        RunTranscriptRecorder transcriptRecorder = new RunTranscriptRecorder(repository, repository);
        UserInteractionManager interactionManager = new UserInteractionManager(
                new yhx.com.domain.agent.service.interaction.PendingInputManager(repository, repository),
                new yhx.com.domain.agent.service.interaction.UserReplyProcessor(repository),
                RuntimeTestSupport.defaultContinuationDispatcher(),
                repository,
                eventPublisher,
                transcriptRecorder,
                failureFactory);
        AtomicInteger dispatchCount = new AtomicInteger();
        AtomicInteger mainCallCount = new AtomicInteger();
        DefaultAutoAgentRuntimeService runtime = new DefaultAutoAgentRuntimeService(
                repository,
                repository,
                repository,
                toolRepeatThenFinalPorts(mainCallCount),
                (context, action) -> {
                    if ("FINAL".equals(action.getAction())) {
                        return MainActionHandlerResult.builder()
                                .status(MainActionHandlerStatusEnumVO.COMPLETED)
                                .nextPhase(RuntimePhaseEnumVO.COMPLETED)
                                .finalAnswerCandidate(FinalAnswerCandidateVO.builder()
                                        .content("The repeated call was skipped and the original success was used.")
                                        .format("text")
                                        .build())
                                .message("Final delivered.")
                                .build();
                    }
                    dispatchCount.incrementAndGet();
                    return successfulToolResult();
                },
                interactionManager,
                new RuntimeLoopPolicy(10, 1, 1, 2, 1, 1),
                stateMachine,
                failureFactory,
                new RuntimePhaseGuard(stateMachine, failureFactory, traceRecorder),
                eventPublisher,
                transcriptRecorder,
                traceRecorder);

        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run-repeat-success-tool-no-dispatch")
                .sessionId("sess-repeat-success-tool-no-dispatch")
                .userId("user-001")
                .userInput("Write file.")
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.COMPLETED, result.getStatus());
        Assert.assertEquals(1, dispatchCount.get());
        Assert.assertEquals(3, mainCallCount.get());
        Assert.assertEquals("The repeated call was skipped and the original success was used.", result.getFinalAnswer());
    }

    @Test
    public void successful_tool_repeat_is_skipped_from_action_history_even_when_previous_action_differs() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        RuntimeFailureFactory failureFactory = new RuntimeFailureFactory();
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();
        DeveloperTraceRecorder traceRecorder = new DeveloperTraceRecorder(repository, repository);
        RunEventPublisher eventPublisher = new RunEventPublisher(repository, repository);
        RunTranscriptRecorder transcriptRecorder = new RunTranscriptRecorder(repository, repository);
        UserInteractionManager interactionManager = new UserInteractionManager(
                new yhx.com.domain.agent.service.interaction.PendingInputManager(repository, repository),
                new yhx.com.domain.agent.service.interaction.UserReplyProcessor(repository),
                RuntimeTestSupport.defaultContinuationDispatcher(),
                repository,
                eventPublisher,
                transcriptRecorder,
                failureFactory);
        AtomicInteger dispatchCount = new AtomicInteger();
        AtomicInteger mainCallCount = new AtomicInteger();
        DefaultAutoAgentRuntimeService runtime = new DefaultAutoAgentRuntimeService(
                repository,
                repository,
                repository,
                toolRepeatAfterDifferentActionPorts(mainCallCount),
                (context, action) -> {
                    if ("FINAL".equals(action.getAction())) {
                        return MainActionHandlerResult.builder()
                                .status(MainActionHandlerStatusEnumVO.COMPLETED)
                                .nextPhase(RuntimePhaseEnumVO.COMPLETED)
                                .finalAnswerCandidate(FinalAnswerCandidateVO.builder()
                                        .content("The repeated call was skipped from historical tool success.")
                                        .format("text")
                                        .build())
                                .message("Final delivered.")
                                .build();
                    }
                    dispatchCount.incrementAndGet();
                    if ("CONTINUE".equals(action.getAction())) {
                        return MainActionHandlerResult.builder()
                                .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                                .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                                .message("Intermediate semantic step.")
                                .build();
                    }
                    return successfulToolResult();
                },
                interactionManager,
                new RuntimeLoopPolicy(10, 1, 1, 2, 1, 1),
                stateMachine,
                failureFactory,
                new RuntimePhaseGuard(stateMachine, failureFactory, traceRecorder),
                eventPublisher,
                transcriptRecorder,
                traceRecorder);

        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run-repeat-success-tool-history")
                .sessionId("sess-repeat-success-tool-history")
                .userId("user-001")
                .userInput("Write file.")
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.COMPLETED, result.getStatus());
        Assert.assertEquals(2, dispatchCount.get());
        Assert.assertEquals(4, mainCallCount.get());
        Assert.assertEquals("The repeated call was skipped from historical tool success.", result.getFinalAnswer());
    }

    @Test
    public void resumed_tool_success_is_recorded_even_when_base_state_view_is_missing() {
        RuntimeExecutionContext context = RuntimeExecutionContext.builder()
                .runId("run-resumed-tool-success")
                .sessionId("sess-resumed-tool-success")
                .userId("user-001")
                .userInput("Write file.")
                .loopIndex(0)
                .build();
        RunWorkingStateManager manager = new RunWorkingStateManager();

        manager.apply(context, writeFileAction(), successfulToolResult());

        Assert.assertNotNull(context.getWorkingState());
        MainAgentStateViewVO projected = manager.project(context.getWorkingState());
        Assert.assertNotNull(projected);
        Assert.assertEquals(1, projected.getActionHistory().size());
        ActionEffectVO effect = projected.getActionHistory().get(0);
        Assert.assertEquals("CALL_TOOL", effect.getAction());
        Assert.assertEquals(ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED.name(), effect.getStatus());
        Assert.assertEquals(writeFileIntent(), effect.getToolIntent());
    }

    @Test
    public void denied_tool_execution_is_blocked_before_dispatching_again() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        RuntimeFailureFactory failureFactory = new RuntimeFailureFactory();
        RuntimeStateMachine stateMachine = new RuntimeStateMachine();
        DeveloperTraceRecorder traceRecorder = new DeveloperTraceRecorder(repository, repository);
        RunEventPublisher eventPublisher = new RunEventPublisher(repository, repository);
        RunTranscriptRecorder transcriptRecorder = new RunTranscriptRecorder(repository, repository);
        UserInteractionManager interactionManager = new UserInteractionManager(
                new yhx.com.domain.agent.service.interaction.PendingInputManager(repository, repository),
                new yhx.com.domain.agent.service.interaction.UserReplyProcessor(repository),
                RuntimeTestSupport.defaultContinuationDispatcher(),
                repository,
                eventPublisher,
                transcriptRecorder,
                failureFactory);
        AtomicInteger dispatchCount = new AtomicInteger();
        DefaultAutoAgentRuntimeService runtime = new DefaultAutoAgentRuntimeService(
                repository,
                repository,
                repository,
                deniedToolPorts(),
                (context, action) -> {
                    dispatchCount.incrementAndGet();
                    return MainActionHandlerResult.builder().status(MainActionHandlerStatusEnumVO.WAITING_USER).build();
                },
                interactionManager,
                new RuntimeLoopPolicy(10, 1, 1, 1, 1, 1),
                stateMachine,
                failureFactory,
                new RuntimePhaseGuard(stateMachine, failureFactory, traceRecorder),
                eventPublisher,
                transcriptRecorder,
                traceRecorder);

        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run-denied-tool")
                .sessionId("sess-denied-tool")
                .userId("user-001")
                .userInput("Write file.")
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.FAILED, result.getStatus());
        Assert.assertEquals(RuntimeFailureCodeEnumVO.TOOL_ACTION_DENIED_BY_USER, result.getSafeFailure().getFailureCode());
        Assert.assertEquals(0, dispatchCount.get());
    }

    private RuntimeComponentPorts repeatedToolPorts() {
        return new RuntimeComponentPorts() {
            @Override
            public ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context) {
                return stateView();
            }

            @Override
            public ContextPlannerHandlingResult refreshContext(RuntimeExecutionContext context) {
                return stateView();
            }

            @Override
            public MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context) {
                return toolAction();
            }
        };
    }

    private RuntimeComponentPorts changingGoalToolPorts() {
        AtomicInteger callCount = new AtomicInteger();
        return new RuntimeComponentPorts() {
            @Override
            public ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context) {
                return stateView();
            }

            @Override
            public ContextPlannerHandlingResult refreshContext(RuntimeExecutionContext context) {
                return stateView();
            }

            @Override
            public MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context) {
                return searchFileAction("search attempt " + callCount.incrementAndGet());
            }
        };
    }

    private RuntimeComponentPorts deniedToolPorts() {
        return new RuntimeComponentPorts() {
            @Override
            public ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context) {
                return deniedStateView();
            }

            @Override
            public ContextPlannerHandlingResult refreshContext(RuntimeExecutionContext context) {
                return deniedStateView();
            }

            @Override
            public MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context) {
                return writeFileAction();
            }
        };
    }

    private RuntimeComponentPorts toolThenFinalPorts(AtomicInteger mainCallCount) {
        return new RuntimeComponentPorts() {
            @Override
            public ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context) {
                return stateView();
            }

            @Override
            public ContextPlannerHandlingResult refreshContext(RuntimeExecutionContext context) {
                return stateView();
            }

            @Override
            public MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context) {
                int callNo = mainCallCount.incrementAndGet();
                if (callNo == 1) {
                    return writeFileAction();
                }
                List<ActionEffectVO> history = context.getLastStateView() == null
                        ? List.of()
                        : context.getLastStateView().getActionHistory();
                Assert.assertEquals(1, history.size());
                ActionEffectVO last = history.get(0);
                Assert.assertEquals("CALL_TOOL", last.getAction());
                Assert.assertEquals(ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED.name(), last.getStatus());
                Assert.assertEquals(writeFileIntent(), last.getToolIntent());
                Assert.assertEquals(List.of("evidence-success"), last.getCreatedEvidenceIds());
                Assert.assertEquals("Tool action succeeded: wrote file", last.getCreatedEvidence().get(0).getSummary());
                return MainAgentActionVO.builder()
                        .action("FINAL")
                        .stateDelta(Map.of("finalAnswerCandidate", Map.of(
                                "content", "The file has been written.",
                                "format", "text"
                        )))
                        .build();
            }
        };
    }

    private RuntimeComponentPorts toolRepeatThenFinalPorts(AtomicInteger mainCallCount) {
        return new RuntimeComponentPorts() {
            @Override
            public ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context) {
                return stateView();
            }

            @Override
            public ContextPlannerHandlingResult refreshContext(RuntimeExecutionContext context) {
                return stateView();
            }

            @Override
            public MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context) {
                int callNo = mainCallCount.incrementAndGet();
                if (callNo <= 2) {
                    return writeFileAction();
                }
                List<ActionEffectVO> history = context.getLastStateView() == null
                        ? List.of()
                        : context.getLastStateView().getActionHistory();
                Assert.assertEquals(2, history.size());
                Assert.assertEquals("CALL_TOOL", history.get(0).getAction());
                Assert.assertEquals(ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED.name(), history.get(0).getStatus());
                Assert.assertEquals(writeFileIntent(), history.get(0).getToolIntent());
                Assert.assertEquals("CALL_TOOL", history.get(1).getAction());
                Assert.assertEquals("SKIPPED_ALREADY_SUCCEEDED", history.get(1).getStatus());
                Assert.assertEquals(writeFileIntent(), history.get(1).getToolIntent());
                return MainAgentActionVO.builder()
                        .action("FINAL")
                        .stateDelta(Map.of("finalAnswerCandidate", Map.of(
                                "content", "The repeated call was skipped and the original success was used.",
                                "format", "text"
                        )))
                        .build();
            }
        };
    }

    private RuntimeComponentPorts toolRepeatAfterDifferentActionPorts(AtomicInteger mainCallCount) {
        return new RuntimeComponentPorts() {
            @Override
            public ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context) {
                return stateView();
            }

            @Override
            public ContextPlannerHandlingResult refreshContext(RuntimeExecutionContext context) {
                return stateView();
            }

            @Override
            public MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context) {
                int callNo = mainCallCount.incrementAndGet();
                if (callNo == 1) {
                    return writeFileAction();
                }
                if (callNo == 2) {
                    return MainAgentActionVO.builder()
                            .action("CONTINUE")
                            .stateDelta(Map.of("note", "intermediate action"))
                            .build();
                }
                if (callNo == 3) {
                    return writeFileAction();
                }
                List<ActionEffectVO> history = context.getLastStateView() == null
                        ? List.of()
                        : context.getLastStateView().getActionHistory();
                Assert.assertEquals(history.toString(), 3, history.size());
                Assert.assertEquals(ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED.name(), history.get(0).getStatus());
                Assert.assertEquals("CONTINUE_LOOP", history.get(1).getStatus());
                Assert.assertEquals("SKIPPED_ALREADY_SUCCEEDED", history.get(2).getStatus());
                Assert.assertEquals(writeFileIntent(), history.get(2).getToolIntent());
                return MainAgentActionVO.builder()
                        .action("FINAL")
                        .stateDelta(Map.of("finalAnswerCandidate", Map.of(
                                "content", "The repeated call was skipped from historical tool success.",
                                "format", "text"
                        )))
                        .build();
            }
        };
    }

    private ContextPlannerHandlingResult stateView() {
        return ContextPlannerHandlingResult.builder()
                .stateView(MainAgentStateViewVO.builder().build())
                .effectiveSelections(List.of())
                .build();
    }

    private ContextPlannerHandlingResult deniedStateView() {
        return ContextPlannerHandlingResult.builder()
                .stateView(MainAgentStateViewVO.builder()
                        .userClarifications(List.of(UserClarificationVO.builder()
                                .sourceComponent("TOOL_APPROVAL")
                                .answerType("TOOL_APPROVAL_REJECTED")
                                .value(Map.of("decision", "REJECTED"))
                                .metadata(Map.of("toolIntent", writeFileIntent()))
                                .build()))
                        .build())
                .effectiveSelections(List.of())
                .build();
    }

    private MainActionDispatcher continueLoopDispatcher() {
        return (context, action) -> MainActionHandlerResult.builder()
                .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                .message("simulated tool failure evidence")
                .build();
    }

    private MainActionHandlerResult successfulToolResult() {
        return MainActionHandlerResult.builder()
                .status(MainActionHandlerStatusEnumVO.CONTINUE_LOOP)
                .nextPhase(RuntimePhaseEnumVO.BUILDING_STATE_VIEW)
                .createdEvidenceIds(List.of("evidence-success"))
                .createdEvidence(List.of(MaterializedEvidenceVO.builder()
                        .evidenceId("evidence-success")
                        .evidenceType("TOOL")
                        .summary("Tool action succeeded: wrote file")
                        .boundedSnippet("Tool action succeeded: wrote file")
                        .build()))
                .actionEffect(ActionEffectVO.builder()
                        .action("CALL_TOOL")
                        .status(ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED.name())
                        .createdEvidenceIds(List.of("evidence-success"))
                        .createdEvidence(List.of(MaterializedEvidenceVO.builder()
                                .evidenceId("evidence-success")
                                .evidenceType("TOOL")
                                .summary("Tool action succeeded: wrote file")
                                .boundedSnippet("Tool action succeeded: wrote file")
                                .build()))
                        .build())
                .message("Tool execution proof passed.")
                .build();
    }

    private MainAgentActionVO toolAction() {
        return searchFileAction("Find the file.");
    }

    private MainAgentActionVO searchFileAction(String goal) {
        return MainAgentActionVO.builder()
                .action("CALL_TOOL")
                .stateDelta(Map.of("toolIntent", Map.of(
                        "capabilityCode", "file_system_search_files",
                        "toolName", "search_files",
                        "goal", goal,
                        "arguments", Map.of(
                                "path", "E:\\javaProject\\ai-agent-station-study",
                                "pattern", "04_blue_train_ticket.txt")
                )))
                .build();
    }

    private MainAgentActionVO writeFileAction() {
        return MainAgentActionVO.builder()
                .action("CALL_TOOL")
                .stateDelta(Map.of("toolIntent", writeFileIntent()))
                .build();
    }

    private Map<String, Object> writeFileIntent() {
        return Map.of(
                "capabilityCode", "file_system_write_file",
                "toolName", "write_file",
                "goal", "write generated content",
                "arguments", Map.of("path", "E:/tmp/story.txt", "content", "hello")
        );
    }
}
