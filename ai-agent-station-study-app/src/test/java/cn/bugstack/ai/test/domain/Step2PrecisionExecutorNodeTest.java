package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientToolMcpVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.Step2PrecisionExecutorNode;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class Step2PrecisionExecutorNodeTest {

    @Test
    public void testBuildExecutionPromptIncludesPlanAndPolicy() {
        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .taskGoal("read project structure")
                .toolRequired(true)
                .toolName("filesystem")
                .toolArgsHint("{\"path\":\"E:\\\\javaProject\\\\ai-agent-station-study\",\"pattern\":\"Step\"}")
                .build();

        AiClientToolMcpVO.ToolPolicy policy = AiClientToolMcpVO.ToolPolicy.builder()
                .requiredArgs(List.of("path", "pattern"))
                .argTypes(Map.of("path", "string", "pattern", "string"))
                .defaultArgs(Map.of("path", "E:\\javaProject\\ai-agent-station-study"))
                .build();

        String prompt = Step2PrecisionExecutorNode.buildExecutionPrompt(plan, "analyze Java 17 updates", policy);

        Assert.assertTrue(prompt.contains("ai-agent-station-study"));
        Assert.assertTrue(prompt.contains("plan.toolName"));
        Assert.assertTrue(prompt.contains("filesystem"));
    }
}
