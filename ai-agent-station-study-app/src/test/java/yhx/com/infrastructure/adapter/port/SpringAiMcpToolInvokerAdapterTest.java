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
                new McpClientRegistry(Map.of("file-system", client))
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
                new McpClientRegistry(Map.of("file-system", client))
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
}
