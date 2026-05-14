package yhx.com.test.domain.agent.invocation;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.service.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.service.invocation.MainAgentActionVO;
import yhx.com.domain.agent.service.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;
import yhx.com.domain.agent.service.invocation.NodeInvocationResult;
import yhx.com.domain.agent.service.invocation.NodeInvocationStatusEnumVO;
import yhx.com.domain.agent.service.prompt.PromptAssembler;
import yhx.com.test.domain.agent.invocation.support.FakeNodeClientPort;
import yhx.com.test.domain.agent.invocation.support.InMemoryPromptContentProvider;

public class NodeInvocationPipelineTest {

    @Test
    public void valid_main_agent_json_returns_success() {
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueue("{\"action\":\"FINAL\",\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"ok\"}}}");

        NodeInvocationResult result = pipeline(client).invoke(command(AgentComponentCodeEnumVO.MAIN_AGENT.name(), 0));

        Assert.assertEquals(NodeInvocationStatusEnumVO.SUCCESS, result.getStatus());
        Assert.assertTrue(result.getTypedOutput() instanceof MainAgentActionVO);
    }

    @Test
    public void markdown_wrapped_json_is_rejected_or_safely_extracted_by_parser_policy() {
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueue("```json\n{\"action\":\"FINAL\",\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"ok\"}}}\n```");

        NodeInvocationResult result = pipeline(client).invoke(command(AgentComponentCodeEnumVO.MAIN_AGENT.name(), 0));

        Assert.assertTrue(result.getStatus() == NodeInvocationStatusEnumVO.SUCCESS || result.getStatus() == NodeInvocationStatusEnumVO.PARSE_FAILED);
    }

    @Test
    public void prose_after_json_fails_contract_pipeline() {
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueue("{\"action\":\"FINAL\",\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"ok\"}}} trailing prose");

        NodeInvocationResult result = pipeline(client).invoke(command(AgentComponentCodeEnumVO.MAIN_AGENT.name(), 0));

        Assert.assertEquals(NodeInvocationStatusEnumVO.PARSE_FAILED, result.getStatus());
    }

    @Test
    public void call_tool_with_final_answer_candidate_fails_state_scope() {
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueue("{\"action\":\"CALL_TOOL\",\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"done\"}}}");

        NodeInvocationResult result = pipeline(client).invoke(command(AgentComponentCodeEnumVO.MAIN_AGENT.name(), 0));

        Assert.assertEquals(NodeInvocationStatusEnumVO.CONTRACT_FAILED, result.getStatus());
        Assert.assertEquals("CONTRACT_VIOLATION", result.getFailureCode());
    }

    @Test
    public void context_planner_ready_output_maps_to_typed_result() {
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueue("{\"status\":\"READY\",\"selectedContext\":[{\"artifactId\":\"artifact-1\",\"useLevel\":\"FULL_TEXT\"}]}");

        NodeInvocationResult result = pipeline(client).invoke(command(AgentComponentCodeEnumVO.CONTEXT_PLANNER.name(), 0));

        Assert.assertEquals(NodeInvocationStatusEnumVO.SUCCESS, result.getStatus());
        Assert.assertTrue(result.getTypedOutput() instanceof ContextPlannerOutputVO);
    }

    private NodeInvocationPipeline pipeline(FakeNodeClientPort client) {
        return new NodeInvocationPipeline(new PromptAssembler(new InMemoryPromptContentProvider()), client);
    }

    private NodeInvocationCommand command(String componentCode, int repairAttempts) {
        return NodeInvocationCommand.builder()
                .runId("run-1")
                .agentId("agent-1")
                .componentCode(componentCode)
                .contractVersion("v1")
                .promptVersion("v1")
                .modelCode("fake")
                .maxRepairAttempts(repairAttempts)
                .inputView("{\"userInput\":\"hello\"}")
                .build();
    }
}
