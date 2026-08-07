package yhx.com.trigger.http;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import yhx.com.api.dto.agent.AgentDebugPayloadDTO;
import yhx.com.api.dto.agent.AgentDebugTraceDTO;
import yhx.com.api.dto.agent.AgentObservabilityStudioDTO;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.service.api.AgentDebugFacade;
import yhx.com.domain.agent.service.api.AgentQueryFacade;
import yhx.com.domain.agent.model.valobj.observability.AgentObservabilitySnapshotVO;
import yhx.com.domain.agent.service.api.DebugSseEventBridge;
import yhx.com.domain.agent.service.debug.DebugAccessPolicy;
import yhx.com.trigger.http.sse.SseEmitterRegistry;
import yhx.com.trigger.http.support.AgentApiMapper;
import yhx.com.trigger.http.support.AgentResponseSupport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

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

    @Resource(name = "autoAgentSseExecutor")
    private Executor sseExecutor;

    @GetMapping("/studio")
    public Response<AgentObservabilityStudioDTO> loadStudio(@PathVariable("runId") String runId) {
        try {
            AgentObservabilitySnapshotVO snapshot = agentDebugFacade.loadStudio(runId);
            AgentObservabilityStudioDTO studio = AgentApiMapper.toObservabilityStudio(snapshot);
            if (agentQueryFacade != null) {
                agentQueryFacade.findActivePendingInput(runId)
                        .map(input -> AgentApiMapper.toPendingInput(input, agentQueryFacade))
                        .ifPresent(studio::setPendingInput);
                studio.setFinalAnswer(agentQueryFacade.findFinalAnswer(runId).orElse(null));
            }
            return AgentResponseSupport.success(studio);
        } catch (Exception e) {
            return AgentResponseSupport.failed(e.getMessage());
        }
    }

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

    @PostMapping("/export")
    public Response<Map<String, Object>> exportDebug(@PathVariable("runId") String runId,
                                                     @RequestBody(required = false) Map<String, Object> browserSnapshot) {
        try {
            Map<String, Object> export = new LinkedHashMap<>();
            export.put("runId", runId);
            export.put("exportedAt", LocalDateTime.now().toString());
            export.put("run", agentQueryFacade.findRun(runId).map(AgentApiMapper::toRun).orElse(null));
            export.put("finalAnswer", agentQueryFacade.findFinalAnswer(runId).orElse(null));
            export.put("events", agentQueryFacade.listUserVisibleEvents(runId, 200).stream()
                    .map(event -> AgentApiMapper.toUserEvent(event, agentQueryFacade))
                    .toList());
            List<AgentDebugTraceDTO> traces = agentDebugFacade.listTraces(runId, 500).stream()
                    .map(trace -> AgentApiMapper.toDebugTrace(trace, agentQueryFacade))
                    .toList();
            export.put("traces", traces);
            export.put("evidence", agentDebugFacade.listEvidence(runId).stream()
                    .map(this::toEvidenceSummary)
                    .toList());
            export.put("toolCalls", agentDebugFacade.listToolCalls(runId).stream()
                    .map(this::toToolCallSummary)
                    .toList());
            Map<String, Object> payloads = new LinkedHashMap<>();
            traces.stream()
                    .map(AgentDebugTraceDTO::getPayloadRef)
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(payloadRef -> agentDebugFacade.findPayload(payloadRef)
                            .map(AgentApiMapper::toDebugPayload)
                            .ifPresent(payload -> payloads.put(payloadRef, payload)));
            export.put("payloads", payloads);
            export.put("browserSnapshot", browserSnapshot == null ? Map.of() : browserSnapshot);

            Path directory = Path.of("data", "debug", "auto-agent");
            Files.createDirectories(directory);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path file = directory.resolve(safeFileName(runId) + "_" + timestamp + ".json");
            Files.writeString(file, JSON.toJSONString(export, true), StandardCharsets.UTF_8);
            return AgentResponseSupport.success(Map.of(
                    "runId", runId,
                    "path", file.toAbsolutePath().toString()
            ));
        } catch (Exception e) {
            log.error("[AutoAgent][debug-export-error] runId={}", runId, e);
            return AgentResponseSupport.failed(e.getMessage());
        }
    }

    @GetMapping("/events/stream")
    public SseEmitter streamDebugEvents(@PathVariable("runId") String runId,
                                        @RequestParam(value = "lastSeq", required = false) Long lastSeq) {
        debugAccessPolicy.requireDebugSseEnabled();
        String streamKey = "debug:" + runId;
        SseEmitter emitter = sseEmitterRegistry.open(streamKey, STREAM_TIMEOUT_MS);
        if (!sseEmitterRegistry.tryAcquireStreamWorker(streamKey)) {
            return emitter;
        }
        try {
            sseExecutor.execute(() -> streamIncrementalDebugEvents(streamKey, runId, lastSeq));
        } catch (RejectedExecutionException error) {
            log.warn("[AutoAgent][debug-sse-executor-rejected] runId={}", runId, error);
            sseEmitterRegistry.releaseStreamWorker(streamKey);
            sseEmitterRegistry.completeWithError(streamKey, error);
        }
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
                    if (!sseEmitterRegistry.send(streamKey, "agent-debug-event", trace.getTraceId(), trace)) {
                        return;
                    }
                    cursor = Math.max(cursor, trace.getSeq() == null ? cursor : trace.getSeq());
                }
                long now = System.currentTimeMillis();
                if (now - lastHeartbeatAt >= STREAM_HEARTBEAT_INTERVAL_MS) {
                    if (!sseEmitterRegistry.send(streamKey, "agent-debug-heartbeat", "debug-heartbeat-" + runId + "-" + cursor,
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
            log.error("[AutoAgent][debug-sse-stream-error] runId={}", runId, e);
            sseEmitterRegistry.completeWithError(streamKey, e);
        } finally {
            sseEmitterRegistry.releaseStreamWorker(streamKey);
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

    private String safeFileName(String value) {
        String normalized = value == null || value.isBlank() ? "run" : value;
        return normalized.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
