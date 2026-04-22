package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.model.entity.CurrentRoundTaskVO;
import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientToolMcpVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.Step2PrecisionExecutorNode;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
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
                .sourceContent("full source body")
                .build();

        AiClientToolMcpVO.ToolPolicy policy = AiClientToolMcpVO.ToolPolicy.builder()
                .requiredArgs(List.of("path", "pattern"))
                .argTypes(Map.of("path", "string", "pattern", "string"))
                .defaultArgs(Map.of("path", "E:\\javaProject\\ai-agent-station-study"))
                .build();

        String prompt = Step2PrecisionExecutorNode.buildExecutionPrompt(plan, "analyze Java 17 updates", policy);

        Assert.assertTrue(prompt.contains("ai-agent-station-study"));
        Assert.assertTrue(prompt.contains("filesystem"));
        Assert.assertTrue(prompt.contains("full source body"));
    }

    @Test
    public void test_validateRequiredSourceContent_returnsBlockingResultWhenMissing() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        dynamicContext.initSession("publish the previous article", 3);
        dynamicContext.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundTask("publish the previous article to CSDN")
                .toolRequired(true)
                .build());

        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .taskGoal("publish the previous article to CSDN")
                .toolRequired(true)
                .toolName("mcp-csdn")
                .build();

        String result = Step2PrecisionExecutorNode.validateRequiredSourceContent(dynamicContext, plan);

        Assert.assertTrue(result.contains("MISSING_REQUIRED_SOURCE_CONTENT"));
    }
}
