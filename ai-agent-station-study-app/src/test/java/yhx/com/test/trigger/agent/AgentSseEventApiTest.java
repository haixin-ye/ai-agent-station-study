package yhx.com.test.trigger.agent;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import yhx.com.domain.agent.service.debug.DebugAccessPolicy;
import yhx.com.domain.agent.service.api.AgentMockScenarioService;
import yhx.com.trigger.http.AgentDebugController;
import yhx.com.trigger.http.AgentEventController;
import yhx.com.trigger.http.AgentMockController;
import yhx.com.trigger.http.sse.SseEmitterRegistry;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AgentSseEventApiTest {

    @Test
    public void normal_sse_registry_opens_sends_and_completes() {
        SseEmitterRegistry registry = new SseEmitterRegistry();

        SseEmitter emitter = registry.open("normal:run-1", 1000L);
        registry.send("normal:run-1", "agent-event", "event-1", "ok");
        registry.complete("normal:run-1");

        Assert.assertNotNull(emitter);
    }

    @Test
    public void normal_sse_rejection_closes_registered_emitter() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        AgentEventController controller = new AgentEventController();
        ReflectionTestUtils.setField(controller, "sseEmitterRegistry", registry);
        ReflectionTestUtils.setField(controller, "sseExecutor", rejectingExecutor());

        Assert.assertNotNull(controller.streamEvents("run-1", null));
        Assert.assertFalse(registry.hasEmitters("normal:run-1"));
    }

    @Test
    public void debug_sse_rejection_closes_registered_emitter() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        AgentDebugController controller = new AgentDebugController();
        ReflectionTestUtils.setField(controller, "debugAccessPolicy", mock(DebugAccessPolicy.class));
        ReflectionTestUtils.setField(controller, "sseEmitterRegistry", registry);
        ReflectionTestUtils.setField(controller, "sseExecutor", rejectingExecutor());

        Assert.assertNotNull(controller.streamDebugEvents("run-2", null));
        Assert.assertFalse(registry.hasEmitters("debug:run-2"));
    }

    @Test
    public void mock_sse_rejection_closes_registered_emitter() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        AgentMockController controller = new AgentMockController();
        ReflectionTestUtils.setField(controller, "sseEmitterRegistry", registry);
        ReflectionTestUtils.setField(controller, "sseExecutor", rejectingExecutor());

        Assert.assertNotNull(controller.streamMockEvents("happy", "run-3"));
        Assert.assertFalse(registry.hasEmitters("mock:run-3"));
    }

    @Test
    public void mock_sse_worker_failure_closes_registered_emitter() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        AgentMockScenarioService scenarioService = mock(AgentMockScenarioService.class);
        when(scenarioService.buildEvents("broken", "run-4")).thenThrow(new IllegalStateException("broken scenario"));
        AgentMockController controller = new AgentMockController();
        ReflectionTestUtils.setField(controller, "agentMockScenarioService", scenarioService);
        ReflectionTestUtils.setField(controller, "sseEmitterRegistry", registry);
        ReflectionTestUtils.setField(controller, "sseExecutor", (Executor) Runnable::run);

        Assert.assertNotNull(controller.streamMockEvents("broken", "run-4"));
        Assert.assertFalse(registry.hasEmitters("mock:run-4"));
    }

    private Executor rejectingExecutor() {
        return command -> {
            throw new RejectedExecutionException("saturated");
        };
    }
}

