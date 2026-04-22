package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.service.execute.auto.step.Step1AnalyzerNode;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Set;

public class Step1AnalyzerNodeTest {

    @Test
    public void test_buildPlanningPrompt_includesSessionHistoryContext() throws Exception {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        dynamicContext.initSession("继续处理上次需求", 3);
        dynamicContext.setValue("sessionHistoryPrompt", """
                Session History:
                [Round 1]
                User: 上次的问题
                Assistant: 上次的回答
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
                "继续处理上次需求",
                "继续处理上次需求",
                "",
                "回答当前问题",
                "",
                "",
                "{}",
                Set.of("filesystem"),
                dynamicContext
        );

        Assert.assertTrue(prompt.contains("\"sessionHistory\""));
        Assert.assertTrue(prompt.contains("Session History:"));
        Assert.assertTrue(prompt.contains("上次的问题"));
        Assert.assertTrue(prompt.contains("上次的回答"));
    }
}
