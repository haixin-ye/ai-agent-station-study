package yhx.com.trigger.http;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import yhx.com.api.dto.agent.AgentUserVisibleEventDTO;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.service.api.AgentQueryFacade;
import yhx.com.domain.agent.service.api.SseUserEventBridge;
import yhx.com.trigger.http.sse.SseEmitterRegistry;
import yhx.com.trigger.http.support.AgentApiMapper;
import yhx.com.trigger.http.support.AgentResponseSupport;

import java.util.Map;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@RestController
@CrossOrigin("*")
@RequestMapping("/agent/runs")
@Slf4j
public class AgentEventController {

    private static final long STREAM_TIMEOUT_MS = 300_000L;
    private static final long STREAM_POLL_INTERVAL_MS = 700L;
    private static final long STREAM_HEARTBEAT_INTERVAL_MS = 15_000L;

    @Resource
    private AgentQueryFacade agentQueryFacade;

    @Resource
    private SseUserEventBridge sseUserEventBridge;

    @Resource
    private SseEmitterRegistry sseEmitterRegistry;

    @Resource(name = "autoAgentSseExecutor")
    private Executor sseExecutor;

    @GetMapping("/{runId}/events")
    public Response<List<AgentUserVisibleEventDTO>> listEvents(@PathVariable("runId") String runId,
                                                               @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return AgentResponseSupport.success(agentQueryFacade.listUserVisibleEvents(runId, limit).stream()
                .map(event -> AgentApiMapper.toUserEvent(event, agentQueryFacade))
                .toList());
    }

    @GetMapping("/{runId}/events/stream")
    public SseEmitter streamEvents(@PathVariable("runId") String runId,
                                   @RequestParam(value = "lastSeq", required = false) Long lastSeq) {
        String streamKey = "normal:" + runId;
        SseEmitter emitter = sseEmitterRegistry.open(streamKey, STREAM_TIMEOUT_MS);
        if (!sseEmitterRegistry.tryAcquireStreamWorker(streamKey)) {
            return emitter;
        }
        try {
            sseExecutor.execute(() -> streamIncrementalEvents(streamKey, runId, lastSeq));
        } catch (RejectedExecutionException error) {
            log.warn("[AutoAgent][sse-executor-rejected] runId={}", runId, error);
            sseEmitterRegistry.releaseStreamWorker(streamKey);
            sseEmitterRegistry.completeWithError(streamKey, error);
        }
        return emitter;
    }

    private void streamIncrementalEvents(String streamKey, String runId, Long lastSeq) {
        long cursor = lastSeq == null ? 0L : lastSeq;
        long startedAt = System.currentTimeMillis();
        long lastHeartbeatAt = 0L;
        try {
            while (System.currentTimeMillis() - startedAt < STREAM_TIMEOUT_MS) {
                List<AgentUserVisibleEventDTO> events = sseUserEventBridge.replayUserVisibleEvents(runId, cursor, 200).stream()
                        .map(event -> AgentApiMapper.toUserEvent(event, agentQueryFacade))
                        .toList();
                for (AgentUserVisibleEventDTO event : events) {
                    if (!sseEmitterRegistry.send(streamKey, "agent-event", event.getEventId(), event)) {
                        return;
                    }
                    cursor = Math.max(cursor, event.getSeq() == null ? cursor : event.getSeq());
                    if (isTerminal(event)) {
                        sseEmitterRegistry.complete(streamKey);
                        return;
                    }
                }
                long now = System.currentTimeMillis();
                if (now - lastHeartbeatAt >= STREAM_HEARTBEAT_INTERVAL_MS) {
                    if (!sseEmitterRegistry.send(streamKey, "agent-heartbeat", "heartbeat-" + runId + "-" + cursor,
                            Map.of("runId", runId, "lastSeq", cursor, "timestamp", now))) {
                        return;
                    }
                    lastHeartbeatAt = now;
                }
                Thread.sleep(STREAM_POLL_INTERVAL_MS);
            }
            sseEmitterRegistry.complete(streamKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sseEmitterRegistry.completeWithError(streamKey, e);
        } catch (Exception e) {
            log.error("[AutoAgent][sse-stream-error] runId={}", runId, e);
            sseEmitterRegistry.completeWithError(streamKey, e);
        } finally {
            sseEmitterRegistry.releaseStreamWorker(streamKey);
        }
    }

    private boolean isTerminal(AgentUserVisibleEventDTO event) {
        return event != null && ("FINAL_READY".equals(event.getEventType()) || "RUN_FAILED".equals(event.getEventType()));
    }
}
