package yhx.com.domain.agent.service.tool;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRunTranscriptRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTranscriptEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TranscriptBlockTypeEnumVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationRequestVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationResultVO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class ToolTranscriptRecorder {

    private final IRunTranscriptRepository transcriptRepository;
    private final IPayloadRepository payloadRepository;

    public ToolTranscriptRecorder(IRunTranscriptRepository transcriptRepository, IPayloadRepository payloadRepository) {
        this.transcriptRepository = transcriptRepository;
        this.payloadRepository = payloadRepository;
    }

    public void appendToolRequest(ToolInvocationRequestVO request) {
        if (request == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", request.getToolCallId());
        payload.put("toolInvocationId", request.getToolInvocationId());
        payload.put("capabilityCode", request.getCapabilitySpec() == null ? null : request.getCapabilitySpec().getCapabilityCode());
        payload.put("mcpServerCode", request.getMcpTool() == null ? null : request.getMcpTool().getMcpServerCode());
        payload.put("toolName", request.getMcpTool() == null ? null : request.getMcpTool().getToolName());
        payload.put("argumentsRef", request.getArgumentsRef());
        append(request.getRunId(), TranscriptBlockTypeEnumVO.TOOL_CALL_REQUEST, payload);
    }

    public void appendToolResult(String runId, ToolInvocationResultVO result) {
        if (result == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallId", result.getToolCallId());
        payload.put("toolInvocationId", result.getToolInvocationId());
        payload.put("status", result.getStatus() == null ? null : result.getStatus().name());
        payload.put("receiptRef", result.getReceiptRef());
        payload.put("resultSummary", result.getResultSummary());
        payload.put("failureCode", result.getFailureCode());
        payload.put("failureMessage", result.getFailureMessage());
        append(runId, TranscriptBlockTypeEnumVO.TOOL_RESULT, payload);
    }

    private void append(String runId, TranscriptBlockTypeEnumVO blockType, Map<String, Object> payload) {
        if (transcriptRepository == null || payloadRepository == null || runId == null) {
            return;
        }
        String payloadRef = payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.TRANSCRIPT_BLOCK)
                .content(JSON.toJSONString(payload))
                .preview(blockType.code())
                .createdAt(LocalDateTime.now())
                .build());
        transcriptRepository.appendBlock(AgentRunTranscriptEntity.builder()
                .runId(runId)
                .blockType(blockType)
                .payloadRef(payloadRef)
                .compactable(true)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
