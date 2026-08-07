package yhx.com.test.domain.agent.invocation;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionCallVO;
import yhx.com.domain.agent.service.invocation.FunctionCallMapper;

import java.util.Map;

public class FunctionCallMapperTest {

    private final FunctionCallMapper mapper = FunctionCallMapper.defaultMapper();

    @Test
    public void main_final_answer_function_maps_to_main_agent_final_json() {
        String normalized = mapper.mapToRawOutput(AgentComponentCodeEnumVO.MAIN_AGENT.name(),
                NodeFunctionCallVO.builder()
                        .name("main_final_answer")
                        .arguments(Map.of(
                                "taskUpdate", Map.of("lastDecision", "Deliver the completed answer."),
                                "content", "ok"))
                        .build());

        Assert.assertTrue(normalized.contains("\"taskUpdate\""));
        Assert.assertTrue(normalized.contains("\"action\":\"FINAL\""));
        Assert.assertTrue(normalized.contains("\"finalAnswerCandidate\":{\"content\":\"ok\"}"));
    }

    @Test
    public void main_call_tool_function_preserves_tool_intent_shape() {
        String normalized = mapper.mapToRawOutput(AgentComponentCodeEnumVO.MAIN_AGENT.name(),
                NodeFunctionCallVO.builder()
                        .name("main_call_tool")
                        .arguments(Map.of(
                                "taskUpdate", Map.of("lastDecision", "Inspect the report folder."),
                                "capabilityCode", "filesystem",
                                "toolName", "list_directory",
                                "goal", "List report folder",
                                "arguments", Map.of("path", "E:/report")
                        ))
                        .build());

        Assert.assertTrue(normalized.contains("\"action\":\"CALL_TOOL\""));
        Assert.assertTrue(normalized.contains("\"toolIntent\""));
        Assert.assertTrue(normalized.contains("\"capabilityCode\":\"filesystem\""));
        Assert.assertTrue(normalized.contains("\"toolName\":\"list_directory\""));
        Assert.assertTrue(normalized.contains("\"path\":\"E:/report\""));
    }
}
