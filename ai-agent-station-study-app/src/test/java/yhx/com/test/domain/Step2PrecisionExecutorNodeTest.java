package yhx.com.test.domain;

import yhx.com.domain.agent.model.entity.CurrentRoundTaskVO;
import yhx.com.domain.agent.model.entity.StepExecutionPlanVO;
import yhx.com.domain.agent.model.valobj.AiClientToolMcpVO;
import yhx.com.domain.agent.service.execute.auto.step.Step2PrecisionExecutorNode;
import yhx.com.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
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
        Assert.assertTrue(prompt.contains("contractMeta"));
        Assert.assertTrue(prompt.contains("\"nodeId\":\"node2\""));
        Assert.assertTrue(prompt.contains("\"contractVersion\":\"v1\""));
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

    @Test
    public void testBuildExecutionPrompt_hidesRawGoalOutsideCurrentRoundContract() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        dynamicContext.initSession("write a RAG article, save it locally, and publish it to CSDN", 3);
        dynamicContext.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(1)
                .currentStepId("step-1")
                .roundTask("create a local folder and save the generated markdown article")
                .expectedEvidence("local file path and write receipt")
                .toolRequired(true)
                .suggestedTools(List.of("filesystem"))
                .build());

        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .taskGoal("create a local folder and save the generated markdown article")
                .toolRequired(true)
                .toolName("filesystem")
                .build();

        String prompt = Step2PrecisionExecutorNode.buildExecutionPrompt(dynamicContext, plan, dynamicContext.getSanitizedUserGoal(),
                AiClientToolMcpVO.ToolPolicy.builder().build());

        Assert.assertTrue(prompt.contains("\"contractVisibility\":\"ROUND_ONLY\""));
        Assert.assertTrue(prompt.contains("create a local folder and save the generated markdown article"));
        Assert.assertFalse(prompt.contains("publish it to CSDN"));
        Assert.assertFalse(prompt.contains("\"rawUserInput\""));
        Assert.assertFalse(prompt.contains("\"sanitizedGoal\""));
    }
}
