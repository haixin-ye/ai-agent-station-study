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
import yhx.com.domain.agent.service.agent.GenericSubAgentNodePort;
import yhx.com.domain.agent.service.agent.NoopChildAgentResultProjector;
import yhx.com.domain.agent.service.agent.ParentChildRunRegistry;
import yhx.com.domain.agent.service.agent.ParentRunResumePort;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    public void prepared_children_are_submitted_to_the_configured_executor() {
        AtomicReference<Runnable> submitted = new AtomicReference<>();
        Fixture fixture = fixture(submitted::set);
        GenericSubAgentDispatchOrchestrationResultVO prepared = fixture.orchestrator.prepareDispatch(
                fixture.parentContext, fixture.request);

        fixture.orchestrator.startPreparedDispatch(fixture.parentContext, fixture.request, prepared);

        Assert.assertNotNull(submitted.get());
        Assert.assertEquals(0, fixture.nodeCalls.get());
        submitted.get().run();
        Assert.assertEquals(1, fixture.nodeCalls.get());
    }

    @Test
    public void rejected_child_submission_marks_the_child_terminal() {
        Fixture fixture = fixture(command -> {
            throw new RejectedExecutionException("saturated");
        });
        GenericSubAgentDispatchOrchestrationResultVO prepared = fixture.orchestrator.prepareDispatch(
                fixture.parentContext, fixture.request);

        fixture.orchestrator.startPreparedDispatch(fixture.parentContext, fixture.request, prepared);

        String childRunId = prepared.getChildRunIds().get(0);
        Assert.assertTrue(fixture.registry.findByChildRunId(childRunId).orElseThrow().getStatus().terminal());
        Assert.assertTrue(fixture.registry.findByChildRunId(childRunId).orElseThrow().getFailureMessage()
                .contains("configured executor"));
    }

    @Test
    public void rejected_parent_resume_submission_releases_the_idempotency_guard() {
        AtomicInteger resumeAttempts = new AtomicInteger();
        ParentRunResumePort rejectedResume = parentRunId -> {
            resumeAttempts.incrementAndGet();
            return false;
        };
        Fixture fixture = fixture(Runnable::run, rejectedResume);
        GenericSubAgentDispatchOrchestrationResultVO prepared = fixture.orchestrator.prepareDispatch(
                fixture.parentContext, fixture.request);

        fixture.orchestrator.startPreparedDispatch(fixture.parentContext, fixture.request, prepared);

        Assert.assertEquals(1, resumeAttempts.get());
        Assert.assertTrue(fixture.registry.markParentResumeRequested(fixture.parentContext.getRunId()));
    }

    @Test
    public void fatal_child_failure_still_marks_the_child_terminal() {
        Fixture fixture = fixture(Runnable::run, null, fullContext -> {
            throw new AssertionError("fatal child failure");
        });
        GenericSubAgentDispatchOrchestrationResultVO prepared = fixture.orchestrator.prepareDispatch(
                fixture.parentContext, fixture.request);

        Assert.assertThrows(AssertionError.class,
                () -> fixture.orchestrator.startPreparedDispatch(fixture.parentContext, fixture.request, prepared));

        String childRunId = prepared.getChildRunIds().get(0);
        Assert.assertTrue(fixture.registry.findByChildRunId(childRunId).orElseThrow().getStatus().terminal());
        Assert.assertTrue(fixture.registry.findByChildRunId(childRunId).orElseThrow().getFailureMessage()
                .contains("fatal child failure"));
    }

    private Fixture fixture() {
        return fixture(Runnable::run);
    }

    private Fixture fixture(Executor executor) {
        return fixture(executor, null);
    }

    private Fixture fixture(Executor executor, ParentRunResumePort parentRunResumePort) {
        return fixture(executor, parentRunResumePort, fullContext -> {
            return SubAgentActionVO.builder()
                    .action("FAIL")
                    .actionInput(Map.of("message", "stop"))
                    .build();
        });
    }

    private Fixture fixture(Executor executor,
                            ParentRunResumePort parentRunResumePort,
                            GenericSubAgentNodePort nodePort) {
        ParentChildRunRegistry registry = new ParentChildRunRegistry();
        AtomicInteger nodeCalls = new AtomicInteger();
        GenericSubAgentDispatchOrchestrator orchestrator = new GenericSubAgentDispatchOrchestrator(
                new AgentDispatchRuntime(registry),
                registry,
                new NoopChildAgentResultProjector(),
                Map.of(),
                fullContext -> {
                    nodeCalls.incrementAndGet();
                    return nodePort.invoke(fullContext);
                },
                new AgentCapabilityResolver(),
                null,
                null,
                null,
                null,
                null,
                parentRunResumePort,
                executor);
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

        return new Fixture(orchestrator, registry, parentContext, request, nodeCalls);
    }

    private record Fixture(GenericSubAgentDispatchOrchestrator orchestrator,
                           ParentChildRunRegistry registry,
                           RuntimeExecutionContext parentContext,
                           DelegateAgentsRequestVO request,
                           AtomicInteger nodeCalls) {
    }
}
