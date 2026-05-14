package yhx.com.infrastructure.adapter.port;

import com.alibaba.fastjson.JSON;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeCommandVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeResultVO;
import yhx.com.domain.agent.service.tool.McpClientRegistry;
import yhx.com.domain.agent.service.tool.port.McpToolInvokerPort;

import java.util.LinkedHashMap;
import java.util.Map;

public class SpringAiMcpToolInvokerAdapter implements McpToolInvokerPort {

    private final McpClientRegistry mcpClientRegistry;

    public SpringAiMcpToolInvokerAdapter(McpClientRegistry mcpClientRegistry) {
        this.mcpClientRegistry = mcpClientRegistry;
    }

    @Override
    public McpToolInvokeResultVO invoke(McpToolInvokeCommandVO command) {
        long startedAt = System.currentTimeMillis();
        if (command == null || isBlank(command.getMcpServerCode()) || isBlank(command.getToolName())) {
            return failed(false, "TOOL_INVALID_INTENT", "mcpServerCode and toolName are required.", startedAt);
        }
        Object handle = mcpClientRegistry.getClientHandle(command.getMcpServerCode());
        if (!(handle instanceof McpSyncClient client)) {
            return failed(false, "TOOL_CLIENT_UNAVAILABLE", "MCP client is not available: " + command.getMcpServerCode(), startedAt);
        }
        ToolCallback callback = findToolCallback(client, command.getToolName());
        if (callback == null) {
            return failed(false, "TOOL_NOT_FOUND", "MCP tool callback is not available: " + command.getToolName(), startedAt);
        }
        try {
            String rawResult = callback.call(JSON.toJSONString(command.getArguments() == null ? Map.of() : command.getArguments()));
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("rawResult", rawResult);
            receipt.put("mcpServerCode", command.getMcpServerCode());
            receipt.put("toolName", command.getToolName());
            return McpToolInvokeResultVO.builder()
                    .called(true)
                    .success(true)
                    .receipt(receipt)
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .build();
        } catch (RuntimeException e) {
            return McpToolInvokeResultVO.builder()
                    .called(true)
                    .success(false)
                    .errorCode(e.getClass().getSimpleName())
                    .errorMessage(e.getMessage())
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .build();
        }
    }

    private ToolCallback findToolCallback(McpSyncClient client, String toolName) {
        ToolCallback[] callbacks = new SyncMcpToolCallbackProvider(java.util.List.of(client)).getToolCallbacks();
        for (ToolCallback callback : callbacks) {
            if (callback.getToolDefinition() != null && toolName.equals(callback.getToolDefinition().name())) {
                return callback;
            }
        }
        return null;
    }

    private McpToolInvokeResultVO failed(boolean called, String errorCode, String errorMessage, long startedAt) {
        return McpToolInvokeResultVO.builder()
                .called(called)
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .latencyMs(System.currentTimeMillis() - startedAt)
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
