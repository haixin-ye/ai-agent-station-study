package yhx.com.domain.agent.service.tool;

import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class McpToolRegistry {

    private final Map<String, McpToolSpecVO> tools = new ConcurrentHashMap<>();

    public McpToolRegistry(List<McpToolSpecVO> toolSpecs) {
        if (toolSpecs != null) {
            toolSpecs.forEach(spec -> {
                if (spec != null && spec.getMcpServerCode() != null && spec.getToolName() != null) {
                    tools.put(key(spec.getMcpServerCode(), spec.getToolName()), spec);
                }
            });
        }
    }

    public Optional<McpToolSpecVO> findTool(String mcpServerCode, String toolName) {
        return Optional.ofNullable(tools.get(key(mcpServerCode, toolName)));
    }

    public McpToolSpecVO requireTool(String mcpServerCode, String toolName) {
        return findTool(mcpServerCode, toolName)
                .orElseThrow(() -> new IllegalArgumentException("MCP tool is missing: " + mcpServerCode + "/" + toolName));
    }

    private String key(String mcpServerCode, String toolName) {
        return String.valueOf(mcpServerCode) + "::" + String.valueOf(toolName);
    }
}
