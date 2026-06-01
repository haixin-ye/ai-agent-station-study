package yhx.com.infrastructure.adapter.port;

import com.alibaba.fastjson.JSON;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeCommandVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeResultVO;
import yhx.com.domain.agent.service.tool.McpClientRegistry;
import yhx.com.domain.agent.service.tool.port.McpToolInvokerPort;

import java.util.LinkedHashMap;
import java.util.List;
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
        try {
            ensureInitialized(client);
            McpSchema.CallToolResult callToolResult = client.callTool(new McpSchema.CallToolRequest(
                    command.getToolName(),
                    command.getArguments() == null ? Map.of() : command.getArguments()
            ));
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("rawResult", JSON.toJSONString(callToolResult));
            receipt.put("mcpServerCode", command.getMcpServerCode());
            receipt.put("toolName", command.getToolName());
            receipt.put("contentText", contentText(callToolResult));
            boolean toolError = Boolean.TRUE.equals(callToolResult == null ? null : callToolResult.isError());
            return McpToolInvokeResultVO.builder()
                    .called(true)
                    .success(!toolError)
                    .receipt(receipt)
                    .errorCode(toolError ? "MCP_TOOL_ERROR" : null)
                    .errorMessage(toolError ? contentText(callToolResult) : null)
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .build();
        } catch (RuntimeException e) {
            String errorCode = client.isInitialized() ? e.getClass().getSimpleName() : "TOOL_CLIENT_INITIALIZATION_FAILED";
            return McpToolInvokeResultVO.builder()
                    .called(true)
                    .success(false)
                    .errorCode(errorCode)
                    .errorMessage(e.getMessage())
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .build();
        }
    }

    private void ensureInitialized(McpSyncClient client) {
        if (!client.isInitialized()) {
            client.initialize();
        }
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

    private String contentText(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null || result.content().isEmpty()) {
            return null;
        }
        List<String> parts = result.content().stream()
                .map(content -> content instanceof McpSchema.TextContent textContent
                        ? textContent.text()
                        : String.valueOf(content))
                .toList();
        return String.join("\n", parts);
    }
}
