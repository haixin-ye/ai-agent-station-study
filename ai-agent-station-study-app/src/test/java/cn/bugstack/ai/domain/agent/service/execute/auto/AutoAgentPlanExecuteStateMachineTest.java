package cn.bugstack.ai.domain.agent.service.execute.auto;

import cn.bugstack.ai.domain.agent.model.valobj.enums.NextRoundDirectiveTypeEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import org.junit.Assert;
import org.junit.Test;

public class AutoAgentPlanExecuteStateMachineTest {

    @Test
    public void shouldInitializeSessionWithStructuredState() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context = new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();

        context.initSession("build a plan", 3);

        Assert.assertNotNull(context.getSessionGoal());
        Assert.assertNotNull(context.getMasterPlan());
        Assert.assertNotNull(context.getCurrentRound());
        Assert.assertNotNull(context.getTaskBoard());
        Assert.assertNotNull(context.getRoundArchive());
        Assert.assertNotNull(context.getToolExecutionLog());
        Assert.assertNotNull(context.getAcceptedResults());
        Assert.assertNotNull(context.getOverallStatus());
        Assert.assertEquals("build a plan", context.getSessionGoal().getRawUserInput());
        Assert.assertEquals(Integer.valueOf(3), context.getSessionGoal().getMaxRounds());
        Assert.assertEquals("step-1", context.getCurrentRound().getCurrentStepId());
        Assert.assertEquals(NextRoundDirectiveTypeEnumVO.REPLAN_SAME_STEP, context.getNextRoundDirective().getDirectiveType());
    }
}
