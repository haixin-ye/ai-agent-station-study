package yhx.com.test.domain.agent.invocation;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IRunDiagnosticRepository;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionCallVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionSpecVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;
import yhx.com.domain.agent.service.prompt.PromptAssembler;
import yhx.com.domain.agent.service.runtime.RunDiagnosticRecorder;
import yhx.com.test.domain.agent.invocation.support.FakeNodeClientPort;
import yhx.com.test.domain.agent.invocation.support.InMemoryPromptContentProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @Test
    public void function_call_mode_passes_specs_to_client_and_validates_mapped_main_action() {
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueueFunctionCall(NodeFunctionCallVO.builder()
                        .name("main_final_answer")
                        .arguments(java.util.Map.of("content", "ok"))
                        .build());

        NodeInvocationCommand command = command(AgentComponentCodeEnumVO.MAIN_AGENT.name(), 0);
        command.setInvocationMode(NodeInvocationModeEnumVO.FUNCTION_CALL);
        command.setFunctionSpecs(java.util.List.of(NodeFunctionSpecVO.builder()
                .name("main_final_answer")
                .description("Return final answer candidate.")
                .parameterSchema(java.util.Map.of(
                        "type", "object",
                        "required", java.util.List.of("content")
                ))
                .build()));

        NodeInvocationResult result = pipeline(client).invoke(command);

        Assert.assertEquals(NodeInvocationStatusEnumVO.SUCCESS, result.getStatus());
        Assert.assertTrue(result.getTypedOutput() instanceof MainAgentActionVO);
        Assert.assertEquals(NodeInvocationModeEnumVO.FUNCTION_CALL, client.requests().get(0).getInvocationMode());
        Assert.assertEquals("main_final_answer", client.requests().get(0).getFunctionSpecs().get(0).getName());
        Assert.assertEquals("{\"action\":\"FINAL\",\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"ok\"}}}", result.getRawOutput());
    }

    @Test
    public void diagnostics_record_previews_instead_of_full_prompt_or_raw_output() {
        FakeNodeClientPort client = new FakeNodeClientPort()
                .enqueue("{\"action\":\"FINAL\",\"stateDelta\":{\"finalAnswerCandidate\":{\"content\":\"" + "y".repeat(3000) + "\"}}}");
        DiagnosticRepository repository = new DiagnosticRepository();
        NodeInvocationCommand command = command(AgentComponentCodeEnumVO.MAIN_AGENT.name(), 0);
        command.setInputView(Map.of("largeStateView", "x".repeat(10000)));

        NodeInvocationResult result = pipeline(client, new RunDiagnosticRecorder(repository)).invoke(command);

        Assert.assertEquals(NodeInvocationStatusEnumVO.SUCCESS, result.getStatus());
        Map<String, Object> nodeCall = repository.find("NODE_CALL");
        Assert.assertFalse(nodeCall.containsKey("prompt"));
        Assert.assertTrue(nodeCall.containsKey("promptChars"));
        Assert.assertTrue(String.valueOf(nodeCall.get("promptPreview")).length() <= 2100);

        Map<String, Object> nodeSuccess = repository.find("NODE_SUCCESS");
        Assert.assertFalse(nodeSuccess.containsKey("rawOutput"));
        Assert.assertTrue(nodeSuccess.containsKey("rawOutputChars"));
        Assert.assertTrue(String.valueOf(nodeSuccess.get("rawOutputPreview")).length() <= 2100);
    }

    private NodeInvocationPipeline pipeline(FakeNodeClientPort client) {
        return new NodeInvocationPipeline(new PromptAssembler(new InMemoryPromptContentProvider()), client);
    }

    private NodeInvocationPipeline pipeline(FakeNodeClientPort client, RunDiagnosticRecorder recorder) {
        return new NodeInvocationPipeline(new PromptAssembler(new InMemoryPromptContentProvider()), client, recorder);
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

    private static class DiagnosticRepository implements IRunDiagnosticRepository {

        private final List<Map<String, Object>> entries = new ArrayList<>();

        @Override
        public void append(String runId, Map<String, Object> entry) {
            entries.add(entry);
        }

        Map<String, Object> find(String event) {
            return entries.stream()
                    .filter(entry -> event.equals(entry.get("event")))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
