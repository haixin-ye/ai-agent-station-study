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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SpringAiMcpToolInvokerAdapter implements McpToolInvokerPort {

    private final McpClientRegistry mcpClientRegistry;
    private final Executor mcpExecutor;

    public SpringAiMcpToolInvokerAdapter(McpClientRegistry mcpClientRegistry, Executor mcpExecutor) {
        this.mcpClientRegistry = Objects.requireNonNull(mcpClientRegistry, "McpClientRegistry is required.");
        this.mcpExecutor = Objects.requireNonNull(mcpExecutor, "MCP Executor is required.");
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
            McpSchema.CallToolResult callToolResult = callToolWithTimeout(client, command, startedAt);
            String contentText = contentText(callToolResult);
            boolean toolError = Boolean.TRUE.equals(callToolResult == null ? null : callToolResult.isError());
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("rawResult", JSON.toJSONString(callToolResult));
            receipt.put("mcpServerCode", command.getMcpServerCode());
            receipt.put("toolName", command.getToolName());
            receipt.put("contentText", contentText);
            return McpToolInvokeResultVO.builder()
                    .called(true)
                    .success(!toolError)
                    .receipt(receipt)
                    .errorCode(toolError ? "MCP_TOOL_ERROR" : null)
                    .errorMessage(toolError ? contentText(callToolResult) : null)
                    .latencyMs(System.currentTimeMillis() - startedAt)
                    .build();
        } catch (McpToolTimeoutException e) {
            return timeout(command, startedAt, e.getMessage());
        } catch (McpExecutorSaturatedException e) {
            return failed(false, "MCP_EXECUTOR_SATURATED", e.getMessage(), startedAt);
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

    private McpSchema.CallToolResult callToolWithTimeout(McpSyncClient client,
                                                         McpToolInvokeCommandVO command,
                                                         long startedAt) {
        long timeoutMs = command.getTimeoutMs() == null ? 0L : command.getTimeoutMs();
        if (timeoutMs <= 0L) {
            ensureInitialized(client);
            return client.callTool(request(command));
        }
        CompletableFuture<McpSchema.CallToolResult> future;
        try {
            future = CompletableFuture.supplyAsync(() -> {
                ensureInitialized(client);
                return client.callTool(request(command));
            }, mcpExecutor);
        } catch (RejectedExecutionException error) {
            throw new McpExecutorSaturatedException(
                    "MCP tool execution was rejected by the configured executor.", error);
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new McpToolTimeoutException("MCP tool call timed out after " + timeoutMs + "ms.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpToolTimeoutException("MCP tool call was interrupted after " + (System.currentTimeMillis() - startedAt) + "ms.", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        }
    }

    private McpSchema.CallToolRequest request(McpToolInvokeCommandVO command) {
        return new McpSchema.CallToolRequest(
                command.getToolName(),
                command.getArguments() == null ? Map.of() : command.getArguments()
        );
    }

    private McpToolInvokeResultVO timeout(McpToolInvokeCommandVO command, long startedAt, String message) {
        long timeoutMs = command == null || command.getTimeoutMs() == null ? 0L : command.getTimeoutMs();
        return McpToolInvokeResultVO.builder()
                .called(true)
                .success(false)
                .errorCode("MCP_TOOL_TIMEOUT")
                .errorMessage(isBlank(message) ? "MCP tool call timed out after " + timeoutMs + "ms." : message)
                .latencyMs(System.currentTimeMillis() - startedAt)
                .build();
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

    private static class McpToolTimeoutException extends RuntimeException {
        private McpToolTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class McpExecutorSaturatedException extends RuntimeException {
        private McpExecutorSaturatedException(String message, Throwable cause) {
            super(message, cause);
        }
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
