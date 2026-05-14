package yhx.com.domain.agent.service.tool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class McpClientRegistry {

    private final Map<String, Object> clientHandles = new ConcurrentHashMap<>();

    public McpClientRegistry(Map<String, Object> clientHandles) {
        if (clientHandles != null) {
            this.clientHandles.putAll(clientHandles);
        }
    }

    public boolean hasClient(String mcpServerCode) {
        return mcpServerCode != null && clientHandles.containsKey(mcpServerCode);
    }

    public Object getClientHandle(String mcpServerCode) {
        return clientHandles.get(mcpServerCode);
    }
}
