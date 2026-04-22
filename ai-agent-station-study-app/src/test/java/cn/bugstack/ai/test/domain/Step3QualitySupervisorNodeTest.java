package cn.bugstack.ai.test.domain;

import cn.bugstack.ai.domain.agent.model.entity.CurrentRoundTaskVO;
import cn.bugstack.ai.domain.agent.model.entity.ExecutionOutcomeVO;
import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.model.entity.SupervisionDecisionVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.Step3QualitySupervisorNode;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import org.junit.Assert;
import org.junit.Test;

public class Step3QualitySupervisorNodeTest {

    @Test
    public void test_resolveDecision_replansWhenCurrentPlanIsLowConfidence() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        dynamicContext.initSession("summarize this requirement", 3);
        dynamicContext.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(1)
                .currentStepId("step-1")
                .roundTask("summarize this requirement")
                .toolRequired(false)
                .build());
        dynamicContext.setCurrentStepPlan(StepExecutionPlanVO.builder()
                .planId("legacy-1")
                .round(1)
                .taskGoal("summarize this requirement")
                .toolRequired(false)
                .lowConfidence(true)
                .recoveryLevel("SEMANTIC_UNCERTAIN")
                .parseMode("LEGACY")
                .build());

        SupervisionDecisionVO decision = Step3QualitySupervisorNode.resolveDecision(
                "{\"decision\":\"PASS\",\"roundDecision\":\"ROUND_PASS\",\"overallDecision\":\"OVERALL_PASS\"}",
                ExecutionOutcomeVO.builder().status(ExecutionOutcomeVO.SUCCESS).rawResult("done").build(),
                dynamicContext
        );

        Assert.assertEquals(SupervisionDecisionVO.REPLAN, decision.getDecision());
        Assert.assertEquals(SupervisionDecisionVO.ROUND_RETRY, decision.getRoundDecision());
        Assert.assertEquals(SupervisionDecisionVO.OVERALL_CONTINUE, decision.getOverallDecision());
        Assert.assertEquals("LOW_CONFIDENCE_PLAN", decision.getIssues());
    }
}
