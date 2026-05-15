package yhx.com.test.trigger.agent;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import yhx.com.trigger.http.sse.SseEmitterRegistry;

public class AgentSseEventApiTest {

    @Test
    public void normal_sse_registry_opens_sends_and_completes() {
        SseEmitterRegistry registry = new SseEmitterRegistry();

        SseEmitter emitter = registry.open("normal:run-1", 1000L);
        registry.send("normal:run-1", "agent-event", "event-1", "ok");
        registry.complete("normal:run-1");

        Assert.assertNotNull(emitter);
    }
}

