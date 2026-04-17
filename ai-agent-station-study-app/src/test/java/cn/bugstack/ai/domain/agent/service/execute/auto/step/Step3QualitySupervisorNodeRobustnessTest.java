package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.ExecutionOutcomeVO;
import cn.bugstack.ai.domain.agent.model.entity.RoundExecutionSummaryVO;
import cn.bugstack.ai.domain.agent.model.entity.ToolExecutionRecordVO;
import cn.bugstack.ai.domain.agent.model.entity.SupervisionDecisionVO;
import cn.bugstack.ai.domain.agent.model.entity.CurrentRoundTaskVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import org.junit.Assert;
import org.junit.Test;

public class Step3QualitySupervisorNodeRobustnessTest {

    @Test
    public void should_not_pass_when_decision_is_replan() {
        String supervision = """
                Assessment: good
                Pass: PASS
                Decision: REPLAN
                """;

        Assert.assertFalse(Step3QualitySupervisorNode.containsPass(supervision));
    }

    @Test
    public void should_pass_when_overall_pass_in_json() {
        String supervision = """
                {"roundDecision":"ROUND_PASS","overallDecision":"OVERALL_PASS","nextAction":"FINISH","assessment":"ok"}
                """;
        Assert.assertTrue(Step3QualitySupervisorNode.containsPass(supervision));
    }

    @Test
    public void should_parse_json_decision_first() {
        String supervision = """
                {"roundDecision":"ROUND_PASS","overallDecision":"OVERALL_CONTINUE","nextAction":"NEXT_ROUND_REPLAN","assessment":"ok","issues":"","suggestions":"","score":8}
                """;
        SupervisionDecisionVO decision = Step3QualitySupervisorNode.resolveDecision(supervision, null);
        Assert.assertEquals(SupervisionDecisionVO.ROUND_PASS, decision.getRoundDecision());
        Assert.assertEquals(SupervisionDecisionVO.OVERALL_CONTINUE, decision.getOverallDecision());
        Assert.assertEquals("NEXT_ROUND_REPLAN", decision.getNextAction());
    }

    @Test
    public void should_infer_overall_pass_when_json_forgets_it_but_decision_is_pass() {
        String supervision = """
                {"decision":"PASS","roundDecision":"ROUND_PASS","nextAction":"FINISH","assessment":"ok"}
                """;
        SupervisionDecisionVO decision = Step3QualitySupervisorNode.resolveDecision(supervision, null);
        Assert.assertEquals(SupervisionDecisionVO.PASS, decision.getDecision());
        Assert.assertEquals(SupervisionDecisionVO.ROUND_PASS, decision.getRoundDecision());
        Assert.assertEquals(SupervisionDecisionVO.OVERALL_PASS, decision.getOverallDecision());
    }

    @Test
    public void should_not_treat_round_pass_as_finish_when_overall_continue() {
        String supervision = """
                {"roundDecision":"ROUND_PASS","overallDecision":"OVERALL_CONTINUE","nextAction":"ADVANCE_NEXT_STEP","assessment":"ok"}
                """;
        Assert.assertFalse(Step3QualitySupervisorNode.containsPass(supervision));
    }

    @Test
    public void should_force_replan_when_execution_outcome_not_success() {
        ExecutionOutcomeVO outcome = ExecutionOutcomeVO.builder()
                .status(ExecutionOutcomeVO.BLOCKED)
                .errorCode("POLICY_VALIDATION_FAILED")
                .errorMessage("tool not allowed")
                .build();
        SupervisionDecisionVO decision = Step3QualitySupervisorNode.resolveDecision("Decision: PASS", outcome);
        Assert.assertEquals(SupervisionDecisionVO.ROUND_RETRY, decision.getRoundDecision());
        Assert.assertEquals(SupervisionDecisionVO.OVERALL_CONTINUE, decision.getOverallDecision());
    }

    @Test
    public void should_allow_relaxed_tool_evidence_when_receipt_is_missing_but_tool_log_exists() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("write file", 3);
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(1)
                .currentStepId("step-file")
                .roundTask("write txt file")
                .toolRequired(true)
                .build());
        context.setRoundExecutionSummary(RoundExecutionSummaryVO.builder()
                .toolRequired(true)
                .toolInvoked(true)
                .toolSuccess(true)
                .evidenceAvailable(true)
                .evidenceSummary("filesystem callback success")
                .build());
        context.getToolExecutionLog().add(ToolExecutionRecordVO.builder()
                .roundIndex(1)
                .stepId("step-file")
                .toolName("filesystem")
                .success(true)
                .responsePayload("{\"status\":\"success\",\"source\":\"relaxed-evidence\"}")
                .build());

        ExecutionOutcomeVO outcome = ExecutionOutcomeVO.builder()
                .status(ExecutionOutcomeVO.FAILED)
                .errorCode("MISSING_TOOL_RECEIPT")
                .errorMessage("tool required but no structured tool receipt")
                .rawResult("""
                        PlanRead: create a txt file
                        ToolDecision: use filesystem
                        ExecutionTarget:
                        路径: E:/javaProject/ai-agent-station-study
                        文件名: result.txt
                        内容: 1+1=2
                        ExecutionProcess: invoke filesystem write
                        ExecutionResult: 文件创建并写入成功
                        Evidence: file exists and content matches
                        QualityCheck: pass
                        """)
                .build();

        SupervisionDecisionVO decision = Step3QualitySupervisorNode.resolveDecision(
                """
                {"roundDecision":"ROUND_PASS","overallDecision":"OVERALL_PASS","nextAction":"FINISH","assessment":"ok"}
                """,
                outcome,
                context);

        Assert.assertEquals(SupervisionDecisionVO.ROUND_PASS, decision.getRoundDecision());
        Assert.assertEquals(SupervisionDecisionVO.OVERALL_PASS, decision.getOverallDecision());
    }

    @Test
    public void should_force_retry_when_tool_required_but_execution_summary_has_no_evidence() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("list directory", 3);
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(1)
                .currentStepId("step-list")
                .roundTask("list files")
                .toolRequired(true)
                .build());
        context.setRoundExecutionSummary(RoundExecutionSummaryVO.builder()
                .toolRequired(true)
                .toolInvoked(true)
                .toolSuccess(true)
                .evidenceAvailable(false)
                .blockingReason("tool executed but no credible evidence was retained")
                .build());

        SupervisionDecisionVO decision = Step3QualitySupervisorNode.resolveDecision(
                "{\"roundDecision\":\"ROUND_PASS\",\"overallDecision\":\"OVERALL_PASS\",\"nextAction\":\"FINISH\"}",
                ExecutionOutcomeVO.builder().status(ExecutionOutcomeVO.SUCCESS).build(),
                context);

        Assert.assertEquals(SupervisionDecisionVO.ROUND_RETRY, decision.getRoundDecision());
        Assert.assertEquals(SupervisionDecisionVO.OVERALL_CONTINUE, decision.getOverallDecision());
        Assert.assertEquals("INSUFFICIENT_TOOL_EVIDENCE", decision.getIssues());
    }
}
