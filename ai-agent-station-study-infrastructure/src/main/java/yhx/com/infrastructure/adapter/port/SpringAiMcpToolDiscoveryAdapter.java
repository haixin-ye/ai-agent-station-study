package yhx.com.infrastructure.adapter.port;

import com.alibaba.fastjson.JSON;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import yhx.com.domain.agent.model.valobj.enums.tool.McpTransportTypeEnumVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;
import yhx.com.domain.agent.service.tool.McpClientRegistry;
import yhx.com.domain.agent.service.tool.port.McpToolDiscoveryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpringAiMcpToolDiscoveryAdapter implements McpToolDiscoveryPort {

    private final McpClientRegistry mcpClientRegistry;

    public SpringAiMcpToolDiscoveryAdapter(McpClientRegistry mcpClientRegistry) {
        this.mcpClientRegistry = mcpClientRegistry;
    }

    @Override
    public List<McpToolSpecVO> discover(String mcpServerCode) {
        Object handle = mcpClientRegistry.getClientHandle(mcpServerCode);
        if (!(handle instanceof McpSyncClient client)) {
            return List.of();
        }
        List<McpToolSpecVO> tools = new ArrayList<>();
        ToolCallback[] callbacks = new SyncMcpToolCallbackProvider(java.util.List.of(client)).getToolCallbacks();
        for (ToolCallback callback : callbacks) {
            if (callback.getToolDefinition() == null) {
                continue;
            }
            tools.add(McpToolSpecVO.builder()
                    .mcpServerCode(mcpServerCode)
                    .toolName(callback.getToolDefinition().name())
                    .description(callback.getToolDefinition().description())
                    .transportType(McpTransportTypeEnumVO.UNKNOWN)
                    .inputSchema(parseSchema(callback.getToolDefinition().inputSchema()))
                    .schemaLessAllowed(false)
                    .build());
        }
        return tools;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSchema(String inputSchema) {
        if (inputSchema == null || inputSchema.isBlank()) {
            return Map.of();
        }
        Object parsed = JSON.parse(inputSchema);
        if (parsed instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
