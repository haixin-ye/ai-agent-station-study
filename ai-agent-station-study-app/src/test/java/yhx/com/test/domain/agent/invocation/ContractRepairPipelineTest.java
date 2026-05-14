package yhx.com.test.domain.agent.invocation;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.service.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;
import yhx.com.domain.agent.service.invocation.NodeInvocationResult;
import yhx.com.domain.agent.service.invocation.NodeInvocationStatusEnumVO;
import yhx.com.domain.agent.service.prompt.PromptAssembler;
import yhx.com.test.domain.agent.invocation.support.FakeNodeClientPort;
import yhx.com.test.domain.agent.invocation.support.InMemoryPromptContentProvider;

public class ContractRepairPipelineTest {

    @Test
    public void invalid_json_then_repair_success_returns_repair_succeeded() {
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueue("{bad json")
                .enqueue("{\"action\":\"FINAL\",\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"ok\"}}}");

        NodeInvocationResult result = pipeline(client).invoke(command(1));

        Assert.assertEquals(NodeInvocationStatusEnumVO.REPAIR_SUCCEEDED, result.getStatus());
        Assert.assertEquals(2, result.getAttempts().size());
    }

    @Test
    public void contract_violation_then_repair_success_returns_repair_succeeded() {
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueue("{\"action\":\"CALL_TOOL\",\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"bad\"}}}")
                .enqueue("{\"action\":\"CALL_TOOL\",\"stateDelta\":{\"toolIntent\":{\"toolName\":\"demo.tool\",\"intent\":\"demo\"}}}");

        NodeInvocationResult result = pipeline(client).invoke(command(1));

        Assert.assertEquals(NodeInvocationStatusEnumVO.REPAIR_SUCCEEDED, result.getStatus());
    }

    @Test
    public void repair_budget_exhausted_returns_repair_failed() {
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueue("{bad json")
                .enqueue("{still bad");

        NodeInvocationResult result = pipeline(client).invoke(command(1));

        Assert.assertEquals(NodeInvocationStatusEnumVO.REPAIR_FAILED, result.getStatus());
    }

    @Test
    public void repair_prompt_contains_validation_error_and_original_output() {
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueue("{bad json")
                .enqueue("{\"action\":\"FINAL\",\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"ok\"}}}");

        pipeline(client).invoke(command(1));

        String repairPrompt = client.requests().get(1).getPrompt();
        Assert.assertTrue(repairPrompt.contains("invalidRawOutput"));
        Assert.assertTrue(repairPrompt.contains("{bad json"));
        Assert.assertTrue(repairPrompt.contains("validationFailures"));
    }

    private NodeInvocationPipeline pipeline(FakeNodeClientPort client) {
        return new NodeInvocationPipeline(new PromptAssembler(new InMemoryPromptContentProvider()), client);
    }

    private NodeInvocationCommand command(int repairAttempts) {
        return NodeInvocationCommand.builder()
                .runId("run-1")
                .agentId("agent-1")
                .componentCode(AgentComponentCodeEnumVO.MAIN_AGENT.name())
                .contractVersion("v1")
                .promptVersion("v1")
                .modelCode("fake")
                .maxRepairAttempts(repairAttempts)
                .inputView("{\"userInput\":\"hello\"}")
                .build();
    }
}
