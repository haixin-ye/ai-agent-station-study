package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.runtime.AutoAgentRuntimeService;
import yhx.com.domain.agent.service.runtime.RuntimeLoopPolicy;
import yhx.com.test.domain.agent.runtime.support.RuntimeTestSupport;

import java.util.Map;

public class RuntimeLifecycleBoundaryTest {

    @Test
    public void node_output_cannot_set_runtime_phase() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        MainAgentActionVO action = MainAgentActionVO.builder()
                .action("FINAL")
                .stateDelta(Map.of(
                        "runtimePhase", "FAILED",
                        "finalAnswerCandidate", Map.of("content", "done")
                ))
                .build();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(repository, RuntimeTestSupport.fixedPorts(action), true, new RuntimeLoopPolicy());

        runtime.start(RuntimeStartCommand.builder().runId("run-001").sessionId("sess-001").userId("u1").userInput("hello").build());

        Assert.assertEquals(RuntimePhaseEnumVO.COMPLETED, repository.runs.get("run-001").getPhase());
    }

    @Test
    public void node_output_cannot_set_run_status() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        MainAgentActionVO action = MainAgentActionVO.builder()
                .action("FINAL")
                .stateDelta(Map.of(
                        "runStatus", "FAILED",
                        "finalAnswerCandidate", Map.of("content", "done")
                ))
                .build();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(repository, RuntimeTestSupport.fixedPorts(action), true, new RuntimeLoopPolicy());

        runtime.start(RuntimeStartCommand.builder().runId("run-002").sessionId("sess-002").userId("u1").userInput("hello").build());

        Assert.assertEquals(RunStatusEnumVO.COMPLETED, repository.runs.get("run-002").getStatus());
    }

    @Test
    public void runtime_writes_user_event_and_developer_trace_separately() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(repository, RuntimeTestSupport.fixedPorts(finalAction()), true, new RuntimeLoopPolicy());

        runtime.start(RuntimeStartCommand.builder().runId("run-003").sessionId("sess-003").userId("u1").userInput("hello").build());

        Assert.assertFalse(repository.events.isEmpty());
        Assert.assertFalse(repository.traces.isEmpty());
        Assert.assertTrue(repository.events.stream().allMatch(event -> Boolean.TRUE.equals(event.getUserVisible())));
    }

    @Test
    public void missing_action_handler_returns_safe_failure() {
        RuntimeTestSupport.InMemoryRuntimeRepository repository = new RuntimeTestSupport.InMemoryRuntimeRepository();
        AutoAgentRuntimeService runtime = RuntimeTestSupport.runtime(repository,
                RuntimeTestSupport.fixedPorts(MainAgentActionVO.builder().action("CALL_TOOL").stateDelta(Map.of()).build()),
                false,
                new RuntimeLoopPolicy());

        RuntimeStepResult result = runtime.start(RuntimeStartCommand.builder().runId("run-004").sessionId("sess-004").userId("u1").userInput("tool").build());

        Assert.assertEquals(RuntimeStepStatusEnumVO.FAILED, result.getStatus());
        Assert.assertNotNull(result.getSafeFailure());
    }

    private MainAgentActionVO finalAction() {
        return MainAgentActionVO.builder()
                .action("FINAL")
                .stateDelta(Map.of("finalAnswerCandidate", Map.of("content", "done")))
                .build();
    }
}
