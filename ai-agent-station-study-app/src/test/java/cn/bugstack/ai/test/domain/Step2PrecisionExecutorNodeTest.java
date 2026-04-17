package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.model.entity.CurrentRoundTaskVO;
import cn.bugstack.ai.domain.agent.model.valobj.AiClientToolMcpVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
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

        Assert.assertTrue(prompt.contains("execute_current_round_task"));
        Assert.assertTrue(prompt.contains("filesystem"));
        Assert.assertTrue(prompt.contains("Actually complete the current round task"));
        Assert.assertTrue(prompt.contains("SanitizedGoal"));
        Assert.assertTrue(prompt.contains("ExecutionIntent"));
        Assert.assertFalse(prompt.contains("ExecutionProcess:"));
        Assert.assertFalse(prompt.trim().startsWith("{"));
    }

    @Test
    public void testBuildExecutionPromptIncludesCurrentRoundContextWhenAvailable() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("write file", 3);
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(2)
                .currentStepId("step-write")
                .roundTask("write report")
                .toolRequired(true)
                .suggestedTools(List.of("filesystem"))
                .build());

        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .taskGoal("write report")
                .toolRequired(true)
                .toolName("filesystem")
                .build();

        String prompt = Step2PrecisionExecutorNode.buildExecutionPrompt(context, plan, "write file", null);

        Assert.assertTrue(prompt.contains("currentRound"));
        Assert.assertTrue(prompt.contains("step-write"));
        Assert.assertTrue(prompt.contains("write report"));
    }

    @Test
    public void testBuildExecutionUnderstandingIncludesCurrentRoundContextWhenAvailable() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("write file", 3);
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(2)
                .currentStepId("step-write")
                .roundTask("write report")
                .toolRequired(true)
                .suggestedTools(List.of("filesystem"))
                .build());

        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .taskGoal("write report")
                .toolRequired(true)
                .toolName("filesystem")
                .expectedOutput("file path")
                .build();

        String understanding = Step2PrecisionExecutorNode.buildExecutionUnderstanding(context, plan, "write file");

        Assert.assertTrue(understanding.contains("currentRound"));
        Assert.assertTrue(understanding.contains("step-write"));
        Assert.assertTrue(understanding.contains("expectedOutput"));
    }

    @Test
    public void testBuildExecutionUnderstandingPrefersCurrentRoundAsPrimaryView() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("write file", 3);
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(2)
                .currentStepId("step-write")
                .roundTask("write report to desktop")
                .toolRequired(true)
                .suggestedTools(List.of("filesystem"))
                .plannerNotes("write to desktop directory")
                .expectedEvidence("file path and write receipt")
                .build());

        StepExecutionPlanVO legacyPlan = StepExecutionPlanVO.builder()
                .taskGoal("search weather first")
                .toolRequired(true)
                .toolName("baidu-search")
                .toolPurpose("search")
                .expectedOutput("search summary")
                .build();

        String understanding = Step2PrecisionExecutorNode.buildExecutionUnderstanding(context, legacyPlan, "write file");

        Assert.assertTrue(understanding.contains("write report to desktop"));
        Assert.assertTrue(understanding.contains("filesystem"));
        Assert.assertTrue(understanding.contains("write to desktop directory"));
        Assert.assertTrue(understanding.contains("file path and write receipt"));
        Assert.assertFalse(understanding.contains("search weather first"));
    }

    @Test
    public void testDetectToolIntentOnlyJson() {
        String output = """
                json
                {
                  "tool": "filesystem",
                  "action": "write",
                  "arguments": {
                    "path": "E:\\\\javaProject\\\\ai-agent-station-study\\\\result.txt",
                    "content": "1+1=2"
                  }
                }
                """;

        Assert.assertTrue(Step2PrecisionExecutorNode.looksLikeToolIntentOnly(output));
        Assert.assertFalse(Step2PrecisionExecutorNode.looksLikeToolIntentOnly("ExecutionResult: done"));
    }
}
