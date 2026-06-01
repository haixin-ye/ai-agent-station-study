package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
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
import yhx.com.domain.agent.service.runtime.RuntimeStateMachine;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.util.List;
import java.util.Map;

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

    private ContextPlannerHandlingResult stateView() {
        return ContextPlannerHandlingResult.builder()
                .stateView(MainAgentStateViewVO.builder().build())
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

    private MainAgentActionVO toolAction() {
        return MainAgentActionVO.builder()
                .action("CALL_TOOL")
                .stateDelta(Map.of("toolIntent", Map.of(
                        "capabilityCode", "file_system_list_directory",
                        "toolName", "list_directory",
                        "arguments", Map.of("path", ".")
                )))
                .build();
    }
}
