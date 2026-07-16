package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentTaskVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentsRequestVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentDispatchOrchestrationResultVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.agent.AgentDispatchRuntime;
import yhx.com.domain.agent.service.agent.GenericSubAgentDispatchOrchestrator;
import yhx.com.domain.agent.service.agent.NoopChildAgentResultProjector;
import yhx.com.domain.agent.service.agent.ParentChildRunRegistry;
import yhx.com.domain.agent.service.runtime.AutoAgentRuntimeService;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class RuntimeDeferredSubAgentStartTest {

    @Test
    public void parent_is_durably_waiting_before_prepared_children_start() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AtomicReference<RunStatusEnumVO> statusAtStart = new AtomicReference<>();
        ParentChildRunRegistry registry = new ParentChildRunRegistry();
        GenericSubAgentDispatchOrchestrator orchestrator = new GenericSubAgentDispatchOrchestrator(
                new AgentDispatchRuntime(registry), registry, new NoopChildAgentResultProjector(), Map.of()) {
            @Override
            public void startPreparedDispatch(RuntimeExecutionContext parentContext,
                                              DelegateAgentsRequestVO request,
                                              GenericSubAgentDispatchOrchestrationResultVO prepared) {
                statusAtStart.set(repository.findRun(parentContext.getRunId()).orElseThrow().getStatus());
            }
        };
        DelegateAgentsRequestVO request = DelegateAgentsRequestVO.builder()
                .waitMode("WAIT_ALL")
                .tasks(List.of(DelegateAgentTaskVO.builder()
                        .taskId("task-1")
                        .name("research")
                        .objective("Find the answer")
                        .requiredOutput("A concise result")
                        .requestedCapabilities(List.of("RAG"))
                        .parentContext(Map.of())
                        .build()))
                .build();
        GenericSubAgentDispatchOrchestrationResultVO prepared = GenericSubAgentDispatchOrchestrationResultVO.builder()
                .parentRunId("run-parent-order")
                .waitMode("WAIT_ALL")
                .childRunIds(List.of("run-parent-order-child-1"))
                .parentReady(false)
                .build();
        MainAgentActionVO action = MainAgentActionVO.builder()
                .action("DELEGATE_AGENTS")
                .stateDelta(Map.of())
                .build();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(
                repository,
                RuntimeTestSupport.fixedPorts(action),
                (context, ignored) -> MainActionHandlerResult.builder()
                        .status(MainActionHandlerStatusEnumVO.WAITING_CHILDREN)
                        .nextPhase(RuntimePhaseEnumVO.WAITING_CHILDREN)
                        .deferredAgentRequest(request)
                        .deferredAgentDispatch(prepared)
                        .message("waiting for child")
                        .build(),
                new RuntimeLoopPolicy(),
                orchestrator);

        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder()
                .runId("run-parent-order")
                .sessionId("sess-parent-order")
                .userId("user-1")
                .agentId("agent-1")
                .userInput("delegate this task")
                .build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.WAITING_CHILDREN, result.getStatus());
        Assert.assertEquals(RunStatusEnumVO.WAITING_CHILDREN, statusAtStart.get());
    }
}
