package yhx.com.trigger.http;

import jakarta.annotation.Resource;
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
import java.util.concurrent.CompletableFuture;

@RestController
@CrossOrigin("*")
@RequestMapping("/agent/runs/{runId}/debug")
public class AgentDebugController {

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

    @GetMapping("/traces")
    public Response<List<AgentDebugTraceDTO>> listTraces(@PathVariable String runId,
                                                         @RequestParam(defaultValue = "100") int limit) {
        try {
            return AgentResponseSupport.success(agentDebugFacade.listTraces(runId, limit).stream()
                    .map(trace -> AgentApiMapper.toDebugTrace(trace, agentQueryFacade))
                    .toList());
        } catch (Exception e) {
            return AgentResponseSupport.failed(e.getMessage());
        }
    }

    @GetMapping("/evidence")
    public Response<List<Map<String, Object>>> listEvidence(@PathVariable String runId) {
        try {
            return AgentResponseSupport.success(agentDebugFacade.listEvidence(runId).stream()
                    .map(this::toEvidenceSummary)
                    .toList());
        } catch (Exception e) {
            return AgentResponseSupport.failed(e.getMessage());
        }
    }

    @GetMapping("/tool-calls")
    public Response<List<Map<String, Object>>> listToolCalls(@PathVariable String runId) {
        try {
            return AgentResponseSupport.success(agentDebugFacade.listToolCalls(runId).stream()
                    .map(this::toToolCallSummary)
                    .toList());
        } catch (Exception e) {
            return AgentResponseSupport.failed(e.getMessage());
        }
    }

    @GetMapping("/payloads/{payloadId}")
    public Response<AgentDebugPayloadDTO> findPayload(@PathVariable String runId, @PathVariable String payloadId) {
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
    public SseEmitter streamDebugEvents(@PathVariable String runId,
                                        @RequestParam(required = false) Long lastSeq) {
        debugAccessPolicy.requireDebugSseEnabled();
        String streamKey = "debug:" + runId;
        SseEmitter emitter = sseEmitterRegistry.open(streamKey, 300_000L);
        CompletableFuture.runAsync(() -> debugSseEventBridge.replayDebugEvents(runId, lastSeq, 200)
                .stream()
                .map(trace -> AgentApiMapper.toDebugTrace(trace, agentQueryFacade))
                .forEach(trace -> sseEmitterRegistry.send(streamKey, "agent-debug-event", trace.getTraceId(), trace)));
        return emitter;
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
