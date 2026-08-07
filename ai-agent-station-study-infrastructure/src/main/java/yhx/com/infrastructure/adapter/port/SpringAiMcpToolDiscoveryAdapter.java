package yhx.com.infrastructure.adapter.port;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import yhx.com.domain.agent.model.valobj.enums.tool.McpTransportTypeEnumVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;
import yhx.com.domain.agent.service.tool.McpClientRegistry;
import yhx.com.domain.agent.service.tool.port.McpToolDiscoveryPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        ensureInitialized(client);
        List<McpToolSpecVO> tools = new ArrayList<>();
        String cursor = null;
        do {
            McpSchema.ListToolsResult result = cursor == null ? client.listTools() : client.listTools(cursor);
            if (result == null || result.tools() == null) {
                break;
            }
            for (McpSchema.Tool tool : result.tools()) {
                if (tool == null || tool.name() == null || tool.name().isBlank()) {
                    continue;
                }
                tools.add(McpToolSpecVO.builder()
                        .mcpServerCode(mcpServerCode)
                        .toolName(tool.name())
                        .description(tool.description())
                        .transportType(McpTransportTypeEnumVO.UNKNOWN)
                        .inputSchema(schema(tool.inputSchema()))
                        .schemaLessAllowed(false)
                        .build());
            }
            cursor = result.nextCursor();
        } while (cursor != null && !cursor.isBlank());
        return List.copyOf(tools);
    }

    private void ensureInitialized(McpSyncClient client) {
        if (!client.isInitialized()) {
            client.initialize();
        }
    }

    private Map<String, Object> schema(McpSchema.JsonSchema source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        put(schema, "type", source.type());
        put(schema, "properties", source.properties());
        put(schema, "required", source.required());
        put(schema, "additionalProperties", source.additionalProperties());
        put(schema, "$defs", source.defs());
        put(schema, "definitions", source.definitions());
        return Map.copyOf(schema);
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
