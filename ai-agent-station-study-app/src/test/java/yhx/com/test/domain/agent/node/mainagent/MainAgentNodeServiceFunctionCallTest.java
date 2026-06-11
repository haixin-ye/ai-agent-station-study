package yhx.com.test.domain.agent.node.mainagent;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationModeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionCallVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;
import yhx.com.domain.agent.service.node.mainagent.MainAgentNodeService;
import yhx.com.domain.agent.service.prompt.PromptAssembler;
import yhx.com.test.domain.agent.invocation.support.FakeNodeClientPort;
import yhx.com.test.domain.agent.invocation.support.InMemoryPromptContentProvider;

import java.util.Map;

public class MainAgentNodeServiceFunctionCallTest {

    @Test
    public void profile_can_enable_function_call_mode_for_main_agent() {
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueueFunctionCall(NodeFunctionCallVO.builder()
                        .name("main_final_answer")
                        .arguments(Map.of("content", "ok"))
                        .build());
        MainAgentNodeService service = new MainAgentNodeService(
                new NodeInvocationPipeline(new PromptAssembler(new InMemoryPromptContentProvider()), client),
                NodeInvocationProfileVO.builder()
                        .modelCode("fake")
                        .invocationMode(NodeInvocationModeEnumVO.FUNCTION_CALL)
                        .maxRepairAttempts(0)
                        .build());

        MainAgentActionVO action = service.invoke(RuntimeExecutionContext.builder()
                .runId("run-1")
                .agentId("agent-1")
                .loopIndex(0)
                .lastStateView(MainAgentStateViewVO.builder().build())
                .build());

        Assert.assertEquals("FINAL", action.getAction());
        Assert.assertEquals(NodeInvocationModeEnumVO.FUNCTION_CALL, client.requests().get(0).getInvocationMode());
        Assert.assertFalse(client.requests().get(0).getFunctionSpecs().isEmpty());
    }
}
