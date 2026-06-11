package yhx.com.domain.agent.service.tool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class McpClientRegistry implements AutoCloseable {

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

    @Override
    public void close() {
        for (Object handle : clientHandles.values()) {
            if (handle instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    // Best-effort shutdown only. Runtime calls report invocation failures explicitly.
                }
            }
        }
    }
}
