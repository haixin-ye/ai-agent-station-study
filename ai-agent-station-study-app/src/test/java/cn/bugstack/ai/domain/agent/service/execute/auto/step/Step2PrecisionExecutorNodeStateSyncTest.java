package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.CurrentRoundTaskVO;
import cn.bugstack.ai.domain.agent.model.entity.RoundExecutionSummaryVO;
import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.model.entity.ToolExecutionRecordVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Step2PrecisionExecutorNodeStateSyncTest {

    @Test
    public void should_sync_round_archive_and_tool_log_when_receipt_exists() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("write file", 3);
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(1)
                .currentStepId("step-file")
                .roundTask("write file")
                .suggestedTools(List.of("filesystem"))
                .build());

        String executionResult = """
                ExecutionProcess: invoke filesystem
                ToolReceipt: {"status":"success","data":{"path":"C:/Users/Administrator/Desktop/report.txt"}}
                ExecutionResult: done
                """;

        Step2PrecisionExecutorNode.syncExecutionState(context, executionResult);

        Assert.assertTrue(context.getRoundArchive().get(1).getNode2ExecutionSnapshot().contains("ExecutionProcess"));
        Assert.assertEquals(1, context.getToolExecutionLog().size());
        Assert.assertEquals("filesystem", context.getToolExecutionLog().get(0).getToolName());
        Assert.assertEquals("step-file", context.getToolExecutionLog().get(0).getStepId());
    }

    @Test
    public void should_build_execution_plan_from_current_round_when_legacy_plan_missing() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("write file", 3);
        context.setSanitizedUserGoal("write file to desktop");
        context.setCurrentStepPlan(null);
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(2)
                .currentStepId("step-write")
                .roundTask("write report file")
                .suggestedTools(List.of("filesystem"))
                .plannerNotes("write file to desktop")
                .expectedEvidence("file path and write result")
                .toolRequired(true)
                .build());

        Assert.assertEquals("write report file", Step2PrecisionExecutorNode.resolveExecutionPlan(context).getTaskGoal());
        Assert.assertEquals("filesystem", Step2PrecisionExecutorNode.resolveExecutionPlan(context).getToolName());
        Assert.assertTrue(Step2PrecisionExecutorNode.resolveExecutionPlan(context).getToolRequired());
    }

    @Test
    public void should_prefer_current_round_metadata_when_syncing_execution_state() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("write file", 3);
        context.setCurrentStepPlan(StepExecutionPlanVO.builder()
                .planId("plan-write")
                .round(1)
                .taskGoal("write report")
                .toolRequired(true)
                .toolName("filesystem")
                .build());
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(1)
                .currentStepId("step-write")
                .roundTask("write report")
                .suggestedTools(List.of("baidu-search"))
                .build());

        Step2PrecisionExecutorNode.syncExecutionState(context, "ToolReceipt: {\"status\":\"success\"}");

        Assert.assertEquals("baidu-search", context.getToolExecutionLog().get(0).getToolName());
        Assert.assertEquals("step-write", context.getToolExecutionLog().get(0).getStepId());
    }

    @Test
    public void should_prefer_current_round_over_legacy_plan_when_resolving_execution_plan() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("write file", 3);
        context.setCurrentStepPlan(StepExecutionPlanVO.builder()
                .planId("legacy-plan")
                .round(1)
                .taskGoal("legacy task")
                .toolRequired(true)
                .toolName("baidu-search")
                .build());
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(2)
                .currentStepId("step-write")
                .roundTask("write report file")
                .suggestedTools(List.of("filesystem"))
                .plannerNotes("write file to desktop")
                .expectedEvidence("file path and write result")
                .toolRequired(true)
                .build());

        Assert.assertEquals("write report file", Step2PrecisionExecutorNode.resolveExecutionPlan(context).getTaskGoal());
        Assert.assertEquals("filesystem", Step2PrecisionExecutorNode.resolveExecutionPlan(context).getToolName());
        Assert.assertEquals("step-write", Step2PrecisionExecutorNode.resolveExecutionPlan(context).getPlanId());
    }

    @Test
    public void should_sync_tool_log_when_filesystem_postcondition_is_verified() throws Exception {
        Path tempDir = Files.createTempDirectory("step2-sync-test");
        Path targetFile = tempDir.resolve("result.txt");
        Files.writeString(targetFile, "1+1=2", StandardCharsets.UTF_8);

        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("write file", 3);
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(1)
                .currentStepId("step-file")
                .roundTask("write txt file")
                .suggestedTools(List.of("filesystem"))
                .build());
        context.setCurrentStepPlan(StepExecutionPlanVO.builder()
                .planId("step-file")
                .round(1)
                .taskGoal("write txt file")
                .toolRequired(true)
                .toolName("filesystem")
                .build());

        String executionResult = """
                ExecutionTarget:
                Path: %s
                FileName: result.txt
                Content: 1+1=2
                ExecutionProcess: invoke filesystem write
                ExecutionResult: done
                """.formatted(tempDir);

        Step2PrecisionExecutorNode.syncExecutionState(context, executionResult);

        Assert.assertEquals(1, context.getToolExecutionLog().size());
        Assert.assertEquals("filesystem", context.getToolExecutionLog().get(0).getToolName());
        Assert.assertTrue(context.getToolExecutionLog().get(0).getResponsePayload().contains("filesystem postcondition verified"));
        Assert.assertTrue(context.getRoundArchive().get(1).getNode2ExecutionSnapshot().contains("ExecutionTarget:"));
    }

    @Test
    public void should_build_round_execution_summary_from_real_tool_records() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("list files", 3);
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(1)
                .currentStepId("step-list")
                .roundTask("list files")
                .toolRequired(true)
                .suggestedTools(List.of("filesystem"))
                .build());
        context.getToolExecutionLog().add(ToolExecutionRecordVO.builder()
                .roundIndex(1)
                .stepId("step-list")
                .toolName("JavaSDKMCPClient_list_directory")
                .success(true)
                .responsePayload("[FILE] pom.xml")
                .build());

        RoundExecutionSummaryVO summary = Step2PrecisionExecutorNode.buildRoundExecutionSummary(
                context,
                "directory listed",
                StepExecutionPlanVO.builder().toolRequired(true).toolName("filesystem").build()
        );

        Assert.assertTrue(summary.getToolRequired());
        Assert.assertTrue(summary.getToolInvoked());
        Assert.assertTrue(summary.getToolSuccess());
        Assert.assertTrue(summary.getEvidenceAvailable());
        Assert.assertTrue(summary.getEvidenceSummary().contains("pom.xml"));
        Assert.assertFalse(summary.getInvokedTools().isEmpty());
    }

    @Test
    public void should_compact_long_tool_payloads_in_round_execution_summary() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("search docs", 3);
        context.setCurrentRound(CurrentRoundTaskVO.builder()
                .roundIndex(1)
                .currentStepId("step-search")
                .roundTask("search docs")
                .toolRequired(true)
                .suggestedTools(List.of("baidu-search"))
                .build());

        String longPayload = "RESULT-" + "A".repeat(4000);
        context.getToolExecutionLog().add(ToolExecutionRecordVO.builder()
                .roundIndex(1)
                .stepId("step-search")
                .toolName("JavaSDKMCPClient_AIsearch")
                .requestPayload("{\"query\":\"ai-agent-station-study-api\"}")
                .responsePayload(longPayload)
                .success(true)
                .build());

        RoundExecutionSummaryVO summary = Step2PrecisionExecutorNode.buildRoundExecutionSummary(
                context,
                "ToolReceipt: " + longPayload,
                StepExecutionPlanVO.builder().toolRequired(true).toolName("baidu-search").build()
        );

        Assert.assertTrue(summary.getEvidenceSummary().contains("JavaSDKMCPClient_AIsearch"));
        Assert.assertTrue(summary.getEvidenceSummary().contains("[truncated"));
        Assert.assertTrue(summary.getEvidenceSummary().length() < 950);
        Assert.assertTrue(summary.getRawExecutionResult().length() < 1250);
    }
}
