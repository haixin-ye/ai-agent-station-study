package yhx.com.test.domain.agent.harness;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentTaskVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentsRequestVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentDispatchOrchestrationResultVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.agent.AgentCapabilityResolver;
import yhx.com.domain.agent.service.agent.AgentDispatchRuntime;
import yhx.com.domain.agent.service.agent.GenericSubAgentDispatchOrchestrator;
import yhx.com.domain.agent.service.agent.NoopChildAgentResultProjector;
import yhx.com.domain.agent.service.agent.ParentChildRunRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class GenericSubAgentDeferredStartTest {

    @Test
    public void invalid_prepared_batch_does_not_partially_start_children() {
        Fixture fixture = fixture();
        GenericSubAgentDispatchOrchestrationResultVO prepared = fixture.orchestrator.prepareDispatch(
                fixture.parentContext, fixture.request);
        prepared.setChildRunIds(List.of(prepared.getChildRunIds().get(0), "run-parent-child-missing"));

        try {
            fixture.orchestrator.startPreparedDispatch(fixture.parentContext, fixture.request, prepared);
            Assert.fail("Expected invalid prepared batch to be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("missing"));
        }
        Assert.assertEquals(0, fixture.nodeCalls.get());
    }

    @Test
    public void prepared_children_do_not_start_until_parent_action_has_been_applied() {
        Fixture fixture = fixture();

        GenericSubAgentDispatchOrchestrationResultVO prepared = fixture.orchestrator.prepareDispatch(
                fixture.parentContext, fixture.request);

        Assert.assertEquals(0, fixture.nodeCalls.get());
        fixture.orchestrator.startPreparedDispatch(fixture.parentContext, fixture.request, prepared);
        Assert.assertEquals(1, fixture.nodeCalls.get());
    }

    private Fixture fixture() {
        ParentChildRunRegistry registry = new ParentChildRunRegistry();
        AtomicInteger nodeCalls = new AtomicInteger();
        GenericSubAgentDispatchOrchestrator orchestrator = new GenericSubAgentDispatchOrchestrator(
                new AgentDispatchRuntime(registry),
                registry,
                new NoopChildAgentResultProjector(),
                Map.of(),
                fullContext -> {
                    nodeCalls.incrementAndGet();
                    return SubAgentActionVO.builder()
                            .action("FAIL")
                            .actionInput(Map.of("message", "stop"))
                            .build();
                },
                new AgentCapabilityResolver(),
                null,
                null,
                null,
                null,
                null,
                null,
                Runnable::run);
        RuntimeExecutionContext parentContext = RuntimeExecutionContext.builder()
                .runId("run-parent")
                .sessionId("sess-parent")
                .runtimeFacts(new java.util.HashMap<>())
                .build();
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

        return new Fixture(orchestrator, parentContext, request, nodeCalls);
    }

    private record Fixture(GenericSubAgentDispatchOrchestrator orchestrator,
                           RuntimeExecutionContext parentContext,
                           DelegateAgentsRequestVO request,
                           AtomicInteger nodeCalls) {
    }
}
