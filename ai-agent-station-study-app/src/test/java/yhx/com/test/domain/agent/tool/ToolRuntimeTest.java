package yhx.com.test.domain.agent.tool;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.ToolCallEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolCallStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeCommandVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeResultVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationRequestVO;
import yhx.com.domain.agent.model.valobj.tool.ToolInvocationResultVO;
import yhx.com.domain.agent.service.tool.ToolFailureMapper;
import yhx.com.domain.agent.service.tool.ToolReceiptCapture;
import yhx.com.domain.agent.service.tool.ToolRuntime;
import yhx.com.domain.agent.service.tool.port.McpToolInvokerPort;

import java.util.Map;

public class ToolRuntimeTest {

    @Test
    public void success_requires_real_receipt() {
        ToolTestSupport.Repository repository = repository();
        ToolRuntime runtime = runtime(repository, command -> McpToolInvokeResultVO.builder()
                .called(true)
                .success(true)
                .receipt(Map.of("url", "https://example.com/post/1"))
                .latencyMs(10L)
                .build());

        ToolInvocationResultVO result = runtime.invoke(request(false, null, Map.of()));

        Assert.assertEquals(ToolInvocationStatusEnumVO.SUCCESS, result.getStatus());
        Assert.assertNotNull(result.getReceiptRef());
        Assert.assertEquals(ToolCallStatusEnumVO.SUCCEEDED, repository.toolCalls.get("tool-call-001").getStatus());
    }

    @Test
    public void missing_approval_returns_needs_user_action() {
        ToolTestSupport.Repository repository = repository();
        ToolRuntime runtime = runtime(repository, command -> McpToolInvokeResultVO.builder().called(true).success(true).build());

        ToolInvocationResultVO result = runtime.invoke(request(true, null, Map.of()));

        Assert.assertEquals(ToolInvocationStatusEnumVO.NEEDS_USER_ACTION, result.getStatus());
        Assert.assertEquals(ToolCallStatusEnumVO.APPROVAL_PENDING, repository.toolCalls.get("tool-call-001").getStatus());
    }

    @Test
    public void schema_validation_failure_returns_invalid_intent() {
        ToolTestSupport.Repository repository = repository();
        ToolRuntime runtime = runtime(repository, command -> McpToolInvokeResultVO.builder().called(true).success(true).build());

        ToolInvocationResultVO result = runtime.invoke(request(false, null, Map.of("required", java.util.List.of("title"))));

        Assert.assertEquals(ToolInvocationStatusEnumVO.INVALID_INTENT, result.getStatus());
        Assert.assertEquals("TOOL_SCHEMA_ERROR", result.getFailureCode());
    }

    @Test
    public void mcp_error_persists_failed_receipt() {
        ToolTestSupport.Repository repository = repository();
        ToolRuntime runtime = runtime(repository, command -> McpToolInvokeResultVO.builder()
                .called(true)
                .success(false)
                .errorCode("MCP_TIMEOUT")
                .errorMessage("timeout")
                .build());

        ToolInvocationResultVO result = runtime.invoke(request(false, null, Map.of()));

        Assert.assertEquals(ToolInvocationStatusEnumVO.FAILED, result.getStatus());
        Assert.assertNotNull(result.getReceiptRef());
        Assert.assertEquals(ToolCallStatusEnumVO.FAILED, repository.toolCalls.get("tool-call-001").getStatus());
    }

    private ToolRuntime runtime(ToolTestSupport.Repository repository, McpToolInvokerPort invoker) {
        return new ToolRuntime(invoker, new ToolReceiptCapture(repository), new ToolFailureMapper(), repository);
    }

    private ToolInvocationRequestVO request(boolean approvalRequired, String approvalId, Map<String, Object> schema) {
        return ToolInvocationRequestVO.builder()
                .runId("run-001")
                .toolCallId("tool-call-001")
                .toolInvocationId("tool-invocation-001")
                .mcpTool(McpToolSpecVO.builder().mcpServerCode("server").toolName("tool").inputSchema(schema).build())
                .arguments(Map.of())
                .argumentsRef("payload-args")
                .approvalRequired(approvalRequired)
                .approvalId(approvalId)
                .build();
    }

    private ToolTestSupport.Repository repository() {
        ToolTestSupport.Repository repository = new ToolTestSupport.Repository();
        repository.createToolCall(ToolCallEntity.builder()
                .toolCallId("tool-call-001")
                .toolInvocationId("tool-invocation-001")
                .runId("run-001")
                .status(ToolCallStatusEnumVO.CREATED)
                .build());
        return repository;
    }
}
