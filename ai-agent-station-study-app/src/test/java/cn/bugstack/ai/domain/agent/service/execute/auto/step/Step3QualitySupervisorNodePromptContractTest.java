package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.CurrentRoundTaskVO;
import cn.bugstack.ai.domain.agent.model.entity.ExecutionOutcomeVO;
import cn.bugstack.ai.domain.agent.model.entity.RoundArchiveVO;
import cn.bugstack.ai.domain.agent.model.entity.RoundExecutionSummaryVO;
import cn.bugstack.ai.domain.agent.model.entity.SessionGoalVO;
import cn.bugstack.ai.domain.agent.model.entity.AcceptedResultVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class Step3QualitySupervisorNodePromptContractTest {

    @Test
    public void should_include_round_state_truth_policy_and_receipt_rule_in_supervision_prompt() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("search and write file", 3);

        SessionGoalVO sessionGoal = new SessionGoalVO();
        sessionGoal.setRawUserInput("Search Beijing weather and save it to a desktop file.");
        sessionGoal.setSanitizedGoal("Search Beijing weather and save it to a desktop file.");
        context.setSessionGoal(sessionGoal);

        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(2)
                .currentStepId("step-write")
                .roundTask("write report to desktop")
                .toolRequired(true)
                .build());
        context.setRoundExecutionSummary(RoundExecutionSummaryVO.builder()
                .toolRequired(true)
                .toolInvoked(true)
                .toolSuccess(true)
                .evidenceAvailable(true)
                .evidenceSummary("filesystem: report.txt written")
                .build());
        String oversized = "PAYLOAD-" + "Z".repeat(4000);
        context.getAcceptedResults().add(AcceptedResultVO.builder()
                .stepId("step-search")
                .resultType("ROUND_RESULT")
                .content(oversized)
                .acceptedReason(oversized)
                .build());
        context.getRoundArchive().put(1, RoundArchiveVO.builder()
                .round(1)
                .node2ExecutionSnapshot(oversized)
                .acceptedResults(List.of(AcceptedResultVO.builder().content(oversized).build()))
                .build());

        String prompt = Step3QualitySupervisorNode.buildSupervisionPrompt(
                context,
                ExecutionOutcomeVO.builder().status(ExecutionOutcomeVO.SUCCESS).rawResult(oversized).build(),
                "none"
        );

        Assert.assertTrue(prompt.contains("verify_current_round_and_overall_progress"));
        Assert.assertTrue(prompt.contains("\"verificationPolicy\""));
        Assert.assertTrue(prompt.contains("\"currentRound\""));
        Assert.assertTrue(prompt.contains("\"masterPlan\""));
        Assert.assertTrue(prompt.contains("\"taskBoard\""));
        Assert.assertTrue(prompt.contains("\"acceptedResults\""));
        Assert.assertTrue(prompt.contains("\"overallStatus\""));
        Assert.assertTrue(prompt.contains("\"roundArchive\""));
        Assert.assertTrue(prompt.contains("\"nextRoundDirective\""));
        Assert.assertTrue(prompt.contains("\"roundExecutionSummary\""));
        Assert.assertTrue(prompt.contains("single-shot QA/RAG/explanation request"));
        Assert.assertTrue(prompt.contains("roundPassDoesNotMeanOverallPass"));
        Assert.assertTrue(prompt.contains("roundExecutionSummary"));
        Assert.assertTrue(prompt.contains("real callback tool records"));
        Assert.assertTrue(prompt.contains("natural-language tool narrative alone is not sufficient"));
        Assert.assertTrue(prompt.contains("credible evidence"));
        Assert.assertTrue(prompt.contains("roundDecision to ROUND_RETRY"));
        Assert.assertTrue(prompt.contains("overallDecision to OVERALL_CONTINUE"));
        Assert.assertFalse(prompt.contains("Z".repeat(2000)));
        Assert.assertTrue(prompt.contains("[truncated"));
    }
}
