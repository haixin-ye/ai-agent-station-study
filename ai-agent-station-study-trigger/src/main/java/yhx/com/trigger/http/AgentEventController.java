package yhx.com.trigger.http;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import yhx.com.api.dto.agent.AgentUserVisibleEventDTO;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.service.api.AgentQueryFacade;
import yhx.com.domain.agent.service.api.SseUserEventBridge;
import yhx.com.trigger.http.sse.SseEmitterRegistry;
import yhx.com.trigger.http.support.AgentApiMapper;
import yhx.com.trigger.http.support.AgentResponseSupport;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@CrossOrigin("*")
@RequestMapping("/agent/runs")
public class AgentEventController {

    @Resource
    private AgentQueryFacade agentQueryFacade;

    @Resource
    private SseUserEventBridge sseUserEventBridge;

    @Resource
    private SseEmitterRegistry sseEmitterRegistry;

    @GetMapping("/{runId}/events")
    public Response<List<AgentUserVisibleEventDTO>> listEvents(@PathVariable String runId,
                                                               @RequestParam(defaultValue = "100") int limit) {
        return AgentResponseSupport.success(agentQueryFacade.listUserVisibleEvents(runId, limit).stream()
                .map(event -> AgentApiMapper.toUserEvent(event, agentQueryFacade))
                .toList());
    }

    @GetMapping("/{runId}/events/stream")
    public SseEmitter streamEvents(@PathVariable String runId,
                                   @RequestParam(required = false) Long lastSeq) {
        String streamKey = "normal:" + runId;
        SseEmitter emitter = sseEmitterRegistry.open(streamKey, 300_000L);
        CompletableFuture.runAsync(() -> sseUserEventBridge.replayUserVisibleEvents(runId, lastSeq, 200)
                .stream()
                .map(event -> AgentApiMapper.toUserEvent(event, agentQueryFacade))
                .forEach(event -> sseEmitterRegistry.send(streamKey, "agent-event", event.getEventId(), event)));
        return emitter;
    }
}

