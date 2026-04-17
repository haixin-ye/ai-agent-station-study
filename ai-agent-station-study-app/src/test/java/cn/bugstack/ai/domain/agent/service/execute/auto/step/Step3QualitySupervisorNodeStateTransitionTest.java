package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.CurrentRoundTaskVO;
import cn.bugstack.ai.domain.agent.model.entity.OverallStatusVO;
import cn.bugstack.ai.domain.agent.model.entity.SupervisionDecisionVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.NextRoundDirectiveTypeEnumVO;
import cn.bugstack.ai.domain.agent.model.valobj.enums.OverallStateEnumVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import org.junit.Assert;
import org.junit.Test;

public class Step3QualitySupervisorNodeStateTransitionTest {

    @Test
    public void should_replan_same_step_when_round_retry() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("write file", 3);
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(1)
                .currentStepId("step-file")
                .roundTask("write desktop file")
                .build());

        SupervisionDecisionVO decision = SupervisionDecisionVO.builder()
                .roundDecision(SupervisionDecisionVO.ROUND_RETRY)
                .overallDecision(SupervisionDecisionVO.OVERALL_CONTINUE)
                .issues("missing tool evidence")
                .raw("Decision: REPLAN")
                .build();

        Step3QualitySupervisorNode.applyDecisionToContext(context, decision, "model said done");

        Assert.assertEquals(NextRoundDirectiveTypeEnumVO.REPLAN_SAME_STEP,
                context.getNextRoundDirective().getDirectiveType());
        Assert.assertEquals(Integer.valueOf(2), Integer.valueOf(context.getStep()));
        Assert.assertEquals("missing tool evidence",
                context.getTaskBoard().get("step-file").getLastFailureReason());
        Assert.assertFalse(context.isCompleted());
    }

    @Test
    public void should_finish_success_when_overall_pass() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("answer question", 3);
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(1)
                .currentStepId("step-answer")
                .roundTask("answer the question")
                .build());
        context.setOverallStatus(OverallStatusVO.running());

        SupervisionDecisionVO decision = SupervisionDecisionVO.builder()
                .roundDecision(SupervisionDecisionVO.ROUND_PASS)
                .overallDecision(SupervisionDecisionVO.OVERALL_PASS)
                .assessment("done")
                .raw("{\"overallDecision\":\"OVERALL_PASS\"}")
                .build();

        Step3QualitySupervisorNode.applyDecisionToContext(context, decision, "final answer");

        Assert.assertEquals(NextRoundDirectiveTypeEnumVO.FINISH_SUCCESS,
                context.getNextRoundDirective().getDirectiveType());
        Assert.assertEquals(OverallStateEnumVO.COMPLETED, context.getOverallStatus().getState());
        Assert.assertEquals(1, context.getAcceptedResults().size());
        Assert.assertEquals("done", context.getAcceptedResults().get(0).getContent());
        Assert.assertTrue(context.isCompleted());
    }

    @Test
    public void should_advance_next_step_when_round_pass_but_overall_continue() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("search and write file", 3);
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(1)
                .currentStepId("step-search")
                .roundTask("search weather")
                .build());
        context.setOverallStatus(OverallStatusVO.running());

        SupervisionDecisionVO decision = SupervisionDecisionVO.builder()
                .roundDecision(SupervisionDecisionVO.ROUND_PASS)
                .overallDecision(SupervisionDecisionVO.OVERALL_CONTINUE)
                .suggestions("advance to write step")
                .raw("{\"roundDecision\":\"ROUND_PASS\",\"overallDecision\":\"OVERALL_CONTINUE\"}")
                .build();

        Step3QualitySupervisorNode.applyDecisionToContext(context, decision, "weather summary");

        Assert.assertEquals(NextRoundDirectiveTypeEnumVO.ADVANCE_NEXT_STEP,
                context.getNextRoundDirective().getDirectiveType());
        Assert.assertEquals(Integer.valueOf(2), Integer.valueOf(context.getStep()));
        Assert.assertEquals(OverallStateEnumVO.RUNNING, context.getOverallStatus().getState());
        Assert.assertEquals(1, context.getAcceptedResults().size());
        Assert.assertFalse(context.getAcceptedResults().get(0).getContent().contains("Issues:"));
        Assert.assertFalse(context.isCompleted());
    }
}
