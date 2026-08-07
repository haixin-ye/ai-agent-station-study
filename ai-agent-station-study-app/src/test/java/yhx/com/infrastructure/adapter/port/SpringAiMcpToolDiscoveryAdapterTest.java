package yhx.com.infrastructure.adapter.port;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;
import yhx.com.domain.agent.service.tool.McpClientRegistry;
import yhx.com.infrastructure.adapter.port.SpringAiMcpToolDiscoveryAdapter;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpringAiMcpToolDiscoveryAdapterTest {

    @Test
    public void discovers_canonical_server_tool_name_without_spring_callback_prefix() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.isInitialized()).thenReturn(true);
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object",
                Map.of("request", Map.of("type", "object")),
                List.of("request"),
                false,
                Map.of(),
                Map.of());
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(
                List.of(new McpSchema.Tool("publishArticle", "Publish an article.", schema)),
                null));
        SpringAiMcpToolDiscoveryAdapter adapter = new SpringAiMcpToolDiscoveryAdapter(
                new McpClientRegistry(Map.of("csdn-publisher", client)));

        List<McpToolSpecVO> tools = adapter.discover("csdn-publisher");

        Assert.assertEquals(1, tools.size());
        Assert.assertEquals("publishArticle", tools.get(0).getToolName());
        Assert.assertEquals(List.of("request"), tools.get(0).getInputSchema().get("required"));
        Assert.assertEquals(false, tools.get(0).getInputSchema().get("additionalProperties"));
    }
}
