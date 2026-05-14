package yhx.com.domain.agent.service.tool;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IToolRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.entity.persistence.ToolVerificationEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolApprovalStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.VerificationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationRequestVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationResultVO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class ToolVerifier {

    private final IToolRepository toolRepository;
    private final IPayloadRepository payloadRepository;

    public ToolVerifier(IToolRepository toolRepository, IPayloadRepository payloadRepository) {
        this.toolRepository = toolRepository;
        this.payloadRepository = payloadRepository;
    }

    public VerificationResultVO verify(ToolInvocationRequestVO request, ToolInvocationResultVO invocationResult) {
        String toolCallId = request == null ? null : request.getToolCallId();
        ToolCallEntity call = toolCallId == null ? null : toolRepository.findToolCall(toolCallId).orElse(null);
        VerificationResultVO result = verifyInternal(request, invocationResult, call);
        saveVerification(toolCallId, call == null ? null : call.getRunId(), result);
        return result;
    }

    private VerificationResultVO verifyInternal(ToolInvocationRequestVO request, ToolInvocationResultVO invocationResult, ToolCallEntity call) {
        if (call == null) {
            return failed("TOOL_NOT_CALLED", "Tool call record does not exist.");
        }
        if (isBlank(call.getToolInvocationId())) {
            return failed("TOOL_NOT_CALLED", "Tool invocation id is missing.");
        }
        if (Boolean.TRUE.equals(request == null ? null : request.getApprovalRequired())) {
            ToolApprovalEntity approval = toolRepository.findApprovalByToolCallId(call.getToolCallId()).orElse(null);
            if (approval == null || approval.getStatus() != ToolApprovalStatusEnumVO.APPROVED) {
                return failed("TOOL_APPROVAL_REQUIRED", "Required tool approval is missing or not approved.");
            }
        }
        if (call.getStatus() == ToolCallStatusEnumVO.PERMISSION_DENIED) {
            return failed("TOOL_PERMISSION_DENIED", "Tool call was denied by permission policy.");
        }
        if (call.getStatus() == ToolCallStatusEnumVO.APPROVAL_PENDING) {
            return failed("TOOL_APPROVAL_REQUIRED", "Tool call is still waiting for approval.");
        }
        if (call.getStatus() == ToolCallStatusEnumVO.NOT_CALLED) {
            return failed("TOOL_NOT_CALLED", "Tool runtime did not call the MCP tool.");
        }
        if (call.getStatus() == ToolCallStatusEnumVO.FAILED) {
            return failed(firstNonBlank(call.getFailureCode(), invocationResult == null ? null : invocationResult.getFailureCode(), "TOOL_FAILED"),
                    "Tool runtime reported failure.");
        }
        if (call.getStatus() == ToolCallStatusEnumVO.SUCCEEDED && isBlank(call.getReceiptRef())) {
            return failed("TOOL_RECEIPT_MISSING", "Successful tool call has no captured receipt.");
        }
        if (call.getStatus() == ToolCallStatusEnumVO.SUCCEEDED) {
            return VerificationResultVO.builder().status(VerificationStatusEnumVO.PASSED.code()).detail("Tool execution proof passed.").build();
        }
        return failed("TOOL_NOT_CALLED", "Tool call did not reach a verifiable terminal status.");
    }

    private void saveVerification(String toolCallId, String runId, VerificationResultVO result) {
        if (toolCallId == null || result == null) {
            return;
        }
        String detailRef = saveDetail(result);
        toolRepository.saveToolVerification(ToolVerificationEntity.builder()
                .runId(runId)
                .toolCallId(toolCallId)
                .status(VerificationStatusEnumVO.ofCode(result.getStatus()).orElse(VerificationStatusEnumVO.FAILED))
                .failureCode(result.getFailureCode())
                .detailRef(detailRef)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String saveDetail(VerificationResultVO result) {
        if (payloadRepository == null) {
            return null;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("status", result.getStatus());
        detail.put("failureCode", result.getFailureCode());
        detail.put("detail", result.getDetail());
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(detail))
                .preview("tool-verification")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private VerificationResultVO failed(String failureCode, String detail) {
        return VerificationResultVO.builder()
                .status(VerificationStatusEnumVO.FAILED.code())
                .failureCode(failureCode)
                .detail(detail)
                .build();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
