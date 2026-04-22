package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.service.execute.auto.step.Step4LogExecutionSummaryNode;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

public class Step4LogExecutionSummaryNodeTest {

    @Test
    public void test_buildSummaryInput_containsContractEnvelope() throws Exception {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        dynamicContext.initSession("deliver the final answer", 3);

        Method method = Step4LogExecutionSummaryNode.class.getDeclaredMethod(
                "buildSummaryInput",
                DefaultAutoAgentExecuteStrategyFactory.DynamicContext.class,
                boolean.class
        );
        method.setAccessible(true);

        String prompt = (String) method.invoke(null, dynamicContext, true);

        Assert.assertTrue(prompt.contains("contractMeta"));
        Assert.assertTrue(prompt.contains("\"nodeId\":\"node4\""));
        Assert.assertTrue(prompt.contains("\"contractVersion\":\"v1\""));
    }
}
