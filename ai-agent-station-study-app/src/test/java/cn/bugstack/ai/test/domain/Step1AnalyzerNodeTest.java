package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.model.entity.SessionMemoryEntity;
import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.Step1AnalyzerNode;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public class Step1AnalyzerNodeTest {

    @Test
    public void test_buildPlanningPrompt_includesSessionHistoryContext() throws Exception {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        dynamicContext.initSession("continue the previous request", 3);
        dynamicContext.setValue("sessionHistoryPrompt", """
                Session History:
                [Round 1]
                User: previous question
                Assistant: previous answer
                """);

        Method method = Step1AnalyzerNode.class.getDeclaredMethod(
                "buildPlanningPrompt",
                int.class,
                int.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Set.class,
                DefaultAutoAgentExecuteStrategyFactory.DynamicContext.class
        );
        method.setAccessible(true);

        String prompt = (String) method.invoke(
                null,
                1,
                3,
                "continue the previous request",
                "continue the previous request",
                "",
                "answer the current question",
                "",
                "",
                "{}",
                Set.of("filesystem"),
                dynamicContext
        );

        Assert.assertTrue(prompt.contains("\"sessionHistory\""));
        Assert.assertTrue(prompt.contains("Session History:"));
        Assert.assertTrue(prompt.contains("previous question"));
        Assert.assertTrue(prompt.contains("previous answer"));
    }

    @Test
    public void test_enrichPlanWithSessionMemory_attachesLatestAnswerAsSourceContent() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        dynamicContext.initSession("publish the previous article to CSDN", 3);
        dynamicContext.setValue("sessionHistory", List.of(
                SessionMemoryEntity.builder()
                        .sessionId("sess-1")
                        .roundNo(1)
                        .finalAnswer("This is the full article body")
                        .build()
        ));

        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .taskGoal("publish the previous article to CSDN")
                .toolRequired(true)
                .toolName("mcp-csdn")
                .build();

        Step1AnalyzerNode.enrichPlanWithSessionMemory(dynamicContext, plan);

        Assert.assertEquals("This is the full article body", plan.getSourceContent());
        Assert.assertTrue(plan.getTaskGoal().contains("sourceContent"));
    }

}
