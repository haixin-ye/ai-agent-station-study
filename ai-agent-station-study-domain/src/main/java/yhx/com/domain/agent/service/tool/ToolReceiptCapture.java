package yhx.com.domain.agent.service.tool;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeResultVO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class ToolReceiptCapture {

    private final IPayloadRepository payloadRepository;

    public ToolReceiptCapture(IPayloadRepository payloadRepository) {
        this.payloadRepository = payloadRepository;
    }

    public String capture(McpToolInvokeResultVO result) {
        if (payloadRepository == null || result == null) {
            return null;
        }
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("called", result.isCalled());
        receipt.put("success", result.isSuccess());
        receipt.put("receipt", result.getReceipt());
        receipt.put("errorCode", result.getErrorCode());
        receipt.put("errorMessage", result.getErrorMessage());
        receipt.put("latencyMs", result.getLatencyMs());
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.TOOL_RECEIPT)
                .content(JSON.toJSONString(receipt))
                .preview(preview(result))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String preview(McpToolInvokeResultVO result) {
        if (result.isSuccess()) {
            return "tool-success";
        }
        if (result.getErrorCode() != null) {
            return "tool-error:" + result.getErrorCode();
        }
        return "tool-error";
    }
}
