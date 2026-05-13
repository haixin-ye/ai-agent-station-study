package yhx.com.test.domain;

import yhx.com.domain.agent.model.entity.CurrentRoundTaskVO;
import yhx.com.domain.agent.model.entity.ExecutionOutcomeVO;
import yhx.com.domain.agent.model.entity.StepExecutionPlanVO;
import yhx.com.domain.agent.model.entity.SupervisionDecisionVO;
import yhx.com.domain.agent.service.execute.auto.step.Step3QualitySupervisorNode;
import yhx.com.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

public class Step3QualitySupervisorNodeTest {

    @Test
    public void test_buildSupervisionPrompt_containsContractEnvelope() throws Exception {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        dynamicContext.initSession("summarize this requirement", 3);
        dynamicContext.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(1)
                .currentStepId("step-1")
                .roundTask("summarize this requirement")
                .toolRequired(false)
                .build());

        Method method = Step3QualitySupervisorNode.class.getDeclaredMethod(
                "buildSupervisionPrompt",
                DefaultAutoAgentExecuteStrategyFactory.DynamicContext.class,
                ExecutionOutcomeVO.class,
                String.class
        );
        method.setAccessible(true);

        String prompt = (String) method.invoke(
                null,
                dynamicContext,
                ExecutionOutcomeVO.builder().status(ExecutionOutcomeVO.SUCCESS).rawResult("done").build(),
                ""
        );

        Assert.assertTrue(prompt.contains("contractMeta"));
        Assert.assertTrue(prompt.contains("\"nodeId\":\"node3\""));
        Assert.assertTrue(prompt.contains("\"contractVersion\":\"v1\""));
    }

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
