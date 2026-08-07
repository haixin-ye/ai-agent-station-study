package yhx.com.test.domain.agent.runtime.handler;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.test.domain.agent.runtime.handler.support.ActionHandlerTestSupport;

import java.util.Map;

public class CallToolActionHandlerTest {

    @Test
    public void call_tool_allows_runtime_to_infer_capability_from_tool_name() {
        ActionHandlerTestSupport.FakeToolActionOrchestratorPort toolPort = new ActionHandlerTestSupport.FakeToolActionOrchestratorPort();
        MainActionDispatcher dispatcher = dispatcher(toolPort);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), MainAgentActionVO.builder()
                .taskUpdate(Map.of("lastDecision", "Inspect the directory."))
                .action("CALL_TOOL")
                .stateDelta(Map.of("toolIntent", Map.of(
                        "capabilityCode", "file_system_list_directory",
                        "toolName", "list_directory",
                        "goal", "Inspect the directory.",
                        "arguments", Map.of("path", "."))))
                .build());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals(1, toolPort.calls.size());
    }

    @Test
    public void call_tool_does_not_invoke_mcp_directly() {
        ActionHandlerTestSupport.FakeToolActionOrchestratorPort toolPort = new ActionHandlerTestSupport.FakeToolActionOrchestratorPort();
        MainActionDispatcher dispatcher = dispatcher(toolPort);

        dispatcher.dispatch(ActionHandlerTestSupport.context(), toolAction());

        Assert.assertEquals(1, toolPort.calls.size());
    }

    @Test
    public void call_tool_waiting_approval_returns_waiting_user() {
        ActionHandlerTestSupport.FakeToolActionOrchestratorPort toolPort = new ActionHandlerTestSupport.FakeToolActionOrchestratorPort();
        toolPort.status = ToolActionStatusEnumVO.WAITING_USER;
        MainActionDispatcher dispatcher = dispatcher(toolPort);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), toolAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.WAITING_USER, result.getStatus());
        Assert.assertEquals("pending-tool", result.getPendingInputId());
    }

    @Test
    public void call_tool_success_continues_loop() {
        MainActionDispatcher dispatcher = dispatcher(new ActionHandlerTestSupport.FakeToolActionOrchestratorPort());

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), toolAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
    }

    private MainActionDispatcher dispatcher(ActionHandlerTestSupport.FakeToolActionOrchestratorPort toolPort) {
        return ActionHandlerTestSupport.dispatcher(new ActionHandlerTestSupport.FullRepository(),
                new ActionHandlerTestSupport.FakeFinalDeliveryPort(),
                new ActionHandlerTestSupport.FakeRagRuntimePort(),
                toolPort);
    }

    private MainAgentActionVO toolAction() {
        return MainAgentActionVO.builder()
                .taskUpdate(Map.of("lastDecision", "Read the requested file."))
                .action("CALL_TOOL")
                .stateDelta(Map.of("toolIntent", Map.of(
                        "capabilityCode", "file_system_read_file",
                        "toolName", "read_file",
                        "goal", "Read the requested file.",
                        "arguments", Map.of("path", "docs/story.md"))))
                .build();
    }
}
