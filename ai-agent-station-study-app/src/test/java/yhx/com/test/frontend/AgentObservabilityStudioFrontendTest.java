package yhx.com.test.frontend;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AgentObservabilityStudioFrontendTest {

    @Test
    public void standalone_studio_contains_live_graph_and_structured_inspector_hooks() throws Exception {
        Path root = Files.exists(Path.of("docs/dev-ops/nginx/html/agent_observability.html"))
                ? Path.of(".") : Path.of("..");
        String page = Files.readString(root.resolve("docs/dev-ops/nginx/html/agent_observability.html"), StandardCharsets.UTF_8);
        String chat = Files.readString(root.resolve("docs/dev-ops/nginx/html/agent_runtime.html"), StandardCharsets.UTF_8);

        for (String token : new String[]{
                "agent-debug-studio", "/debug/studio", "/debug/events/stream", "stateViewSources",
                "mainNodePrompt", "showDetail", "renderStructured", "tool_use", "ask_user",
                "retrieve_rag", "delegate", "ready_to_deliver", "final", "loopSwitcher",
                "data-loop-filter", "hover-preview", "Tool Results", "Child Agents", "Pending Input",
                "detail-intro", "resetGraphScroll"}) {
            Assert.assertTrue("missing token: " + token, page.contains(token));
        }
        Assert.assertTrue(chat.contains("agent_observability.html"));
    }
}
