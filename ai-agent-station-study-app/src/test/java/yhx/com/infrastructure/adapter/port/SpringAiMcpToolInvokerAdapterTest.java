package yhx.com.infrastructure.adapter.port;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeCommandVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeResultVO;
import yhx.com.domain.agent.service.tool.McpClientRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpringAiMcpToolInvokerAdapterTest {

    @Test
    public void invoke_initializes_client_before_listing_tools_and_wraps_initialization_failure() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.isInitialized()).thenReturn(false);
        when(client.initialize()).thenThrow(new IllegalStateException("init failed"));
        SpringAiMcpToolInvokerAdapter adapter = new SpringAiMcpToolInvokerAdapter(
                new McpClientRegistry(Map.of("file-system", client)),
                Runnable::run
        );

        McpToolInvokeResultVO result = adapter.invoke(McpToolInvokeCommandVO.builder()
                .mcpServerCode("file-system")
                .toolName("list_directory")
                .arguments(Map.of("path", "E:/javaProject/ai-agent-station-study"))
                .build());

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals("TOOL_CLIENT_INITIALIZATION_FAILED", result.getErrorCode());
        Assert.assertTrue(result.getErrorMessage().contains("init failed"));
    }

    @Test
    public void invoke_calls_mcp_client_directly_with_configured_tool_name() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.isInitialized()).thenReturn(true);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("ok")),
                false
        ));
        SpringAiMcpToolInvokerAdapter adapter = new SpringAiMcpToolInvokerAdapter(
                new McpClientRegistry(Map.of("file-system", client)),
                Runnable::run
        );

        McpToolInvokeResultVO result = adapter.invoke(McpToolInvokeCommandVO.builder()
                .mcpServerCode("file-system")
                .toolName("list_directory")
                .arguments(Map.of("path", "E:/javaProject/ai-agent-station-study"))
                .build());

        Assert.assertTrue(result.isSuccess());
        verify(client).callTool(new McpSchema.CallToolRequest("list_directory",
                Map.of("path", "E:/javaProject/ai-agent-station-study")));
    }

    @Test
    public void timed_call_uses_the_supplied_executor() {
        McpSyncClient client = successfulClient();
        AtomicBoolean executorUsed = new AtomicBoolean();
        SpringAiMcpToolInvokerAdapter adapter = new SpringAiMcpToolInvokerAdapter(
                new McpClientRegistry(Map.of("file-system", client)),
                command -> {
                    executorUsed.set(true);
                    command.run();
                });

        McpToolInvokeResultVO result = adapter.invoke(command(1000L));

        Assert.assertTrue(result.isSuccess());
        Assert.assertTrue(executorUsed.get());
    }

    @Test
    public void rejected_timed_call_returns_deterministic_saturation_failure() {
        SpringAiMcpToolInvokerAdapter adapter = new SpringAiMcpToolInvokerAdapter(
                new McpClientRegistry(Map.of("file-system", successfulClient())),
                command -> {
                    throw new RejectedExecutionException("saturated");
                });

        McpToolInvokeResultVO result = adapter.invoke(command(1000L));

        Assert.assertFalse(result.isCalled());
        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals("MCP_EXECUTOR_SATURATED", result.getErrorCode());
    }

    private McpSyncClient successfulClient() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.isInitialized()).thenReturn(true);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("ok")), false));
        return client;
    }

    private McpToolInvokeCommandVO command(Long timeoutMs) {
        return McpToolInvokeCommandVO.builder()
                .mcpServerCode("file-system")
                .toolName("list_directory")
                .arguments(Map.of("path", "E:/javaProject/ai-agent-station-study"))
                .timeoutMs(timeoutMs)
                .build();
    }

}
