package yhx.com.test.frontend;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AgentObservabilityStudioFrontendTest {

    @Test
    public void standalone_studio_contains_live_graph_and_node_evidence_hooks() throws Exception {
        Path root = Files.exists(Path.of("docs/dev-ops/nginx/html/agent_observability.html"))
                ? Path.of(".") : Path.of("..");
        String page = Files.readString(root.resolve("docs/dev-ops/nginx/html/agent_observability.html"), StandardCharsets.UTF_8);
        String chat = Files.readString(root.resolve("docs/dev-ops/nginx/html/agent_runtime.html"), StandardCharsets.UTF_8);

        for (String token : new String[]{
                "agent-debug-studio", "/debug/studio", "/debug/events/stream", "stateViewSources", "graphNodes",
                "mainNodePrompt", "showDetail", "renderStructured", "TOOL_USE", "ASK_USER",
                "RAG_RETRIEVAL", "DELEGATE", "READY_TO_DELIVER", "FINAL_DELIVERY", "loopSwitcher",
                "data-loop-filter", "hover-preview", "Tool Results", "Child Agents", "Pending Input",
                "detail-intro", "resetGraphScroll", "detailLayer", "CONTEXT_PREPARE", "CONTEXT_PLANNER",
                "FINAL_DELIVERY", "recallDiagnostics", "evidence-module", "moduleCard", "候选输入总览",
                "物化记忆总览", "候选片段明细"}) {
            Assert.assertTrue("missing token: " + token, page.contains(token));
        }
        Assert.assertFalse("the graph must not depend on a fixed right inspector", page.contains("renderInspector"));
        Assert.assertFalse("the graph must not render a fixed inspector column", page.contains("grid-template-columns:minmax(0,1fr) 390px"));
        Assert.assertTrue(chat.contains("agent_observability.html"));
        Assert.assertTrue(page.contains("stateView.payloadManifest"));
        Assert.assertTrue(page.contains("stateView.activePayloads"));
        Assert.assertTrue(chat.contains("stateDelta.toolIntent"));
        Assert.assertTrue(chat.contains("READY_TO_DELIVER: \"准备最终交付\""));
    }

    @Test
    public void message_copy_uses_a_browser_fallback_and_checks_the_real_result() throws Exception {
        Path root = Files.exists(Path.of("docs/dev-ops/nginx/html/agent_runtime.html"))
                ? Path.of(".") : Path.of("..");
        String chat = Files.readString(root.resolve("docs/dev-ops/nginx/html/agent_runtime.html"), StandardCharsets.UTF_8);

        Assert.assertTrue(chat.contains("async function copyTextToClipboard(text)"));
        Assert.assertTrue(chat.contains("navigator.clipboard.writeText(value)"));
        Assert.assertTrue(chat.contains("document.execCommand(\"copy\")"));
        Assert.assertTrue(chat.contains("const copied = await copyTextToClipboard(message.content || \"\")"));
        Assert.assertFalse(chat.contains("navigator.clipboard?.writeText"));
    }

    @Test
    public void studio_uses_content_driven_module_boards_for_main_node_and_final_delivery() throws Exception {
        Path root = Files.exists(Path.of("docs/dev-ops/nginx/html/agent_observability.html"))
                ? Path.of(".") : Path.of("..");
        String page = Files.readString(root.resolve("docs/dev-ops/nginx/html/agent_observability.html"),
                StandardCharsets.UTF_8);

        for (String token : new String[]{
                "module-board", "mini-card", "hasContent", "contentCount",
                "renderPlanBoard", "renderRoundHistory", "renderActionBoard", "renderFinalDelivery",
                "本轮任务规划", "跨轮工作记忆", "本轮新增 / 更新", "完整 Action JSON",
                "最终交付内容", "数据来源诊断", "真实内容"
        }) {
            Assert.assertTrue("missing content-driven UI token: " + token, page.contains(token));
        }
        Assert.assertTrue("the detail dispatcher must explicitly activate the content-driven renderer",
                page.contains("renderDetailBody=renderDetailBodyV3"));
        Assert.assertTrue("action nodes must route through the content-driven action renderer",
                page.contains("case\"TOOL_USE\":return renderActionV3"));
        Assert.assertTrue("final delivery must route through the real-delivery renderer",
                page.contains("case\"FINAL_DELIVERY\":return renderFinalDelivery"));
    }

    @Test
    public void studio_preserves_inspection_state_and_renders_effective_plan_child_lanes_and_run_history() throws Exception {
        Path root = Files.exists(Path.of("docs/dev-ops/nginx/html/agent_observability.html"))
                ? Path.of(".") : Path.of("..");
        String page = Files.readString(root.resolve("docs/dev-ops/nginx/html/agent_observability.html"),
                StandardCharsets.UTF_8);
        String logic = Files.readString(root.resolve("docs/dev-ops/nginx/html/agent_observability_logic.js"),
                StandardCharsets.UTF_8);

        for (String token : new String[]{
                "agent_observability_logic.js", "captureDetailState", "restoreDetailState", "data-ui-key",
                "run-explorer", "groupSessionRuns", "selectRun", "graph-pan", "zoom-control",
                "mergeEffectivePlan", "plan-record-obsolete", "未关联交付物", "本轮更新",
                "MainAgent 分配的任务", "子 Agent 执行链路", "返回给 MainNode 的真实内容",
                "历史记录只保存了完成摘要", "renderReadyToDeliver", "完整 Action JSON"
        }) {
            Assert.assertTrue("missing refined studio token: " + token, page.contains(token));
        }
        Assert.assertTrue(page.contains("runtimeRouteLabel"));
        Assert.assertTrue(page.contains("renderLoopTimelineCore"));
        Assert.assertTrue(page.contains("renderRuntimeControlCore"));
        Assert.assertTrue(page.contains("renderResolvedPayloadsCore"));
        Assert.assertTrue(page.contains("renderDebugFailureBoard"));
        for (String token : new String[]{"mergeEffectivePlan", "groupSessionRuns", "groupChildLifecycle", "enrichGraphNodeDetails", "collectDebugFailures",
                "cancelledStepIds", "attachedDeliverables", "obsolete"}) {
            Assert.assertTrue("missing projection token: " + token, logic.contains(token));
        }
    }

}
