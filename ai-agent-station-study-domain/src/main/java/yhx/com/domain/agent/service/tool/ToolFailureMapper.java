package yhx.com.domain.agent.service.tool;

import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeResultVO;

public class ToolFailureMapper {

    public ToolInvocationStatusEnumVO invocationStatus(McpToolInvokeResultVO result) {
        if (result == null || !result.isCalled()) {
            return ToolInvocationStatusEnumVO.NOT_CALLED;
        }
        return result.isSuccess() ? ToolInvocationStatusEnumVO.SUCCESS : ToolInvocationStatusEnumVO.FAILED;
    }

    public ToolCallStatusEnumVO callStatus(McpToolInvokeResultVO result) {
        if (result == null || !result.isCalled()) {
            return ToolCallStatusEnumVO.NOT_CALLED;
        }
        return result.isSuccess() ? ToolCallStatusEnumVO.SUCCEEDED : ToolCallStatusEnumVO.FAILED;
    }

    public String failureCode(McpToolInvokeResultVO result) {
        if (result == null) {
            return "TOOL_NOT_CALLED";
        }
        if (!result.isCalled()) {
            return "TOOL_NOT_CALLED";
        }
        if (result.isSuccess()) {
            return null;
        }
        return result.getErrorCode() == null || result.getErrorCode().isBlank() ? "TOOL_FAILED" : result.getErrorCode();
    }
}
