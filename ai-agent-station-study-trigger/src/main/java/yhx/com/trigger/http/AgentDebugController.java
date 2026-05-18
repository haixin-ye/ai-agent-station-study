package yhx.com.trigger.http;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import yhx.com.api.dto.agent.AgentDebugPayloadDTO;
import yhx.com.api.dto.agent.AgentDebugTraceDTO;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.service.api.AgentDebugFacade;
import yhx.com.domain.agent.service.api.AgentQueryFacade;
import yhx.com.domain.agent.service.api.DebugSseEventBridge;
import yhx.com.domain.agent.service.debug.DebugAccessPolicy;
import yhx.com.trigger.http.sse.SseEmitterRegistry;
import yhx.com.trigger.http.support.AgentApiMapper;
import yhx.com.trigger.http.support.AgentResponseSupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@CrossOrigin("*")
@RequestMapping("/agent/runs/{runId}/debug")
@Slf4j
public class AgentDebugController {

    private static final long STREAM_TIMEOUT_MS = 300_000L;
    private static final long STREAM_POLL_INTERVAL_MS = 700L;
    private static final long STREAM_HEARTBEAT_INTERVAL_MS = 15_000L;

    @Resource
    private AgentDebugFacade agentDebugFacade;

    @Resource
    private AgentQueryFacade agentQueryFacade;

    @Resource
    private DebugSseEventBridge debugSseEventBridge;

    @Resource
    private DebugAccessPolicy debugAccessPolicy;

    @Resource
    private SseEmitterRegistry sseEmitterRegistry;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @GetMapping("/traces")
    public Response<List<AgentDebugTraceDTO>> listTraces(@PathVariable("runId") String runId,
                                                         @RequestParam(value = "limit", defaultValue = "100") int limit) {
        try {
            return AgentResponseSupport.success(agentDebugFacade.listTraces(runId, limit).stream()
                    .map(trace -> AgentApiMapper.toDebugTrace(trace, agentQueryFacade))
                    .toList());
        } catch (Exception e) {
            return AgentResponseSupport.failed(e.getMessage());
        }
    }

    @GetMapping("/evidence")
    public Response<List<Map<String, Object>>> listEvidence(@PathVariable("runId") String runId) {
        try {
            return AgentResponseSupport.success(agentDebugFacade.listEvidence(runId).stream()
                    .map(this::toEvidenceSummary)
                    .toList());
        } catch (Exception e) {
            return AgentResponseSupport.failed(e.getMessage());
        }
    }

    @GetMapping("/tool-calls")
    public Response<List<Map<String, Object>>> listToolCalls(@PathVariable("runId") String runId) {
        try {
            return AgentResponseSupport.success(agentDebugFacade.listToolCalls(runId).stream()
                    .map(this::toToolCallSummary)
                    .toList());
        } catch (Exception e) {
            return AgentResponseSupport.failed(e.getMessage());
        }
    }

    @GetMapping("/payloads/{payloadId}")
    public Response<AgentDebugPayloadDTO> findPayload(@PathVariable("runId") String runId, @PathVariable("payloadId") String payloadId) {
        try {
            return agentDebugFacade.findPayload(payloadId)
                    .map(AgentApiMapper::toDebugPayload)
                    .map(AgentResponseSupport::success)
                    .orElseGet(() -> AgentResponseSupport.failed("payload not found"));
        } catch (Exception e) {
            return AgentResponseSupport.failed(e.getMessage());
        }
    }

    @GetMapping("/events/stream")
    public SseEmitter streamDebugEvents(@PathVariable("runId") String runId,
                                        @RequestParam(value = "lastSeq", required = false) Long lastSeq) {
        debugAccessPolicy.requireDebugSseEnabled();
        String streamKey = "debug:" + runId;
        SseEmitter emitter = sseEmitterRegistry.open(streamKey, STREAM_TIMEOUT_MS);
        threadPoolExecutor.execute(() -> streamIncrementalDebugEvents(streamKey, runId, lastSeq));
        return emitter;
    }

    private void streamIncrementalDebugEvents(String streamKey, String runId, Long lastSeq) {
        long cursor = lastSeq == null ? 0L : lastSeq;
        long startedAt = System.currentTimeMillis();
        long lastHeartbeatAt = 0L;
        try {
            while (System.currentTimeMillis() - startedAt < STREAM_TIMEOUT_MS) {
                List<AgentDebugTraceDTO> traces = debugSseEventBridge.replayDebugEvents(runId, cursor, 200).stream()
                        .map(trace -> AgentApiMapper.toDebugTrace(trace, agentQueryFacade))
                        .toList();
                for (AgentDebugTraceDTO trace : traces) {
                    sseEmitterRegistry.send(streamKey, "agent-debug-event", trace.getTraceId(), trace);
                    cursor = Math.max(cursor, trace.getSeq() == null ? cursor : trace.getSeq());
                }
                long now = System.currentTimeMillis();
                if (now - lastHeartbeatAt >= STREAM_HEARTBEAT_INTERVAL_MS) {
                    sseEmitterRegistry.send(streamKey, "agent-debug-heartbeat", "debug-heartbeat-" + runId + "-" + cursor,
                            Map.of("runId", runId, "lastSeq", cursor, "timestamp", now));
                    lastHeartbeatAt = now;
                }
                Thread.sleep(STREAM_POLL_INTERVAL_MS);
            }
            sseEmitterRegistry.complete(streamKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sseEmitterRegistry.completeWithError(streamKey, e);
        } catch (Exception e) {
            log.error("[AutoAgent][debug-sse-stream-error] runId={}", runId, e);
            sseEmitterRegistry.completeWithError(streamKey, e);
        }
    }

    private Map<String, Object> toEvidenceSummary(AgentEvidenceEntity evidence) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("evidenceId", evidence.getEvidenceId());
        summary.put("runId", evidence.getRunId());
        summary.put("evidenceType", evidence.getEvidenceType());
        summary.put("sourceRef", evidence.getSourceRef());
        summary.put("summary", evidence.getSummary());
        summary.put("confidence", evidence.getConfidence());
        summary.put("usedByFinal", evidence.getUsedByFinal());
        summary.put("createdAt", evidence.getCreatedAt());
        return summary;
    }

    private Map<String, Object> toToolCallSummary(ToolCallEntity toolCall) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolCallId", toolCall.getToolCallId());
        summary.put("runId", toolCall.getRunId());
        summary.put("toolName", toolCall.getToolName());
        summary.put("mcpServerName", toolCall.getMcpServerName());
        summary.put("status", toolCall.getStatus() == null ? null : toolCall.getStatus().code());
        summary.put("argumentsRef", toolCall.getArgumentsRef());
        summary.put("receiptRef", toolCall.getReceiptRef());
        summary.put("failureCode", toolCall.getFailureCode());
        summary.put("createdAt", toolCall.getCreatedAt());
        return summary;
    }
}
