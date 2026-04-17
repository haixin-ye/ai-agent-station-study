package cn.bugstack.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.ai.domain.agent.model.entity.ExecutionOutcomeVO;
import cn.bugstack.ai.domain.agent.model.entity.CurrentRoundTaskVO;
import cn.bugstack.ai.domain.agent.model.entity.RoundExecutionSummaryVO;
import cn.bugstack.ai.domain.agent.model.entity.StepExecutionPlanVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Step2ExecutionOutcomeContractTest {

    @Test
    public void should_mark_success_for_normal_result() {
        ExecutionOutcomeVO outcome = Step2PrecisionExecutorNode.buildExecutionOutcome("execution result: completed");
        Assert.assertEquals(ExecutionOutcomeVO.SUCCESS, outcome.getStatus());
    }

    @Test
    public void should_mark_failed_for_blocked_or_failed_keywords() {
        ExecutionOutcomeVO outcome = Step2PrecisionExecutorNode.buildExecutionOutcome("execution blocked by policy");
        Assert.assertEquals(ExecutionOutcomeVO.FAILED, outcome.getStatus());
    }

    @Test
    public void should_mark_failed_when_tool_required_but_no_tool_evidence() {
        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .toolRequired(true)
                .toolName("filesystem")
                .build();
        String output = "The round finished but no structured tool receipt was provided.";
        ExecutionOutcomeVO outcome = Step2PrecisionExecutorNode.buildExecutionOutcome(output, plan);
        Assert.assertEquals(ExecutionOutcomeVO.FAILED, outcome.getStatus());
        Assert.assertEquals("MISSING_TOOL_RECEIPT", outcome.getErrorCode());
    }

    @Test
    public void should_mark_success_when_tool_required_and_tool_receipt_present() {
        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .toolRequired(true)
                .toolName("filesystem")
                .build();
        String output = """
                ExecutionProcess: invoke filesystem write
                ToolReceipt: {"status":"success","message":"File written","data":{"path":"C:/Users/Administrator/Desktop/beijing.txt"}}
                ExecutionResult: done
                """;
        ExecutionOutcomeVO outcome = Step2PrecisionExecutorNode.buildExecutionOutcome(output, plan);
        Assert.assertEquals(ExecutionOutcomeVO.SUCCESS, outcome.getStatus());
    }

    @Test
    public void should_mark_success_when_tool_required_and_execution_summary_has_real_evidence() {
        DefaultAutoAgentExecuteStrategyFactory.DynamicContext context =
                new DefaultAutoAgentExecuteStrategyFactory.DynamicContext();
        context.initSession("list files", 3);
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
                .evidenceAvailable(true)
                .evidenceSummary("[FILE] a.txt")
                .build());

        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .toolRequired(true)
                .toolName("filesystem")
                .build();

        ExecutionOutcomeVO outcome = Step2PrecisionExecutorNode.buildExecutionOutcome(context, "listed files", plan);
        Assert.assertEquals(ExecutionOutcomeVO.SUCCESS, outcome.getStatus());
    }

    @Test
    public void should_mark_failed_when_tool_required_and_only_coherent_tool_narrative_present() {
        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .toolRequired(true)
                .toolName("filesystem")
                .build();
        String output = """
                PlanRead: create a txt file in the project folder with content 1+1=2
                ToolDecision: use filesystem
                ExecutionTarget:
                Path: E:/javaProject/ai-agent-station-study
                FileName: result.txt
                Content: 1+1=2
                ExecutionProcess: invoke filesystem write
                ExecutionResult: file created and written successfully
                Evidence: file created and content matches
                QualityCheck: passed
                """;
        ExecutionOutcomeVO outcome = Step2PrecisionExecutorNode.buildExecutionOutcome(output, plan);
        Assert.assertEquals(ExecutionOutcomeVO.FAILED, outcome.getStatus());
        Assert.assertEquals("MISSING_TOOL_RECEIPT", outcome.getErrorCode());
    }

    @Test
    public void should_mark_success_when_filesystem_postcondition_is_verified() throws Exception {
        Path tempDir = Files.createTempDirectory("step2-filesystem-test");
        Path targetFile = tempDir.resolve("result.txt");
        Files.writeString(targetFile, "1+1=2", StandardCharsets.UTF_8);

        StepExecutionPlanVO plan = StepExecutionPlanVO.builder()
                .toolRequired(true)
                .toolName("filesystem")
                .build();
        String output = """
                ExecutionTarget:
                Path: %s
                FileName: result.txt
                Content: 1+1=2
                ExecutionProcess: invoke filesystem write
                ExecutionResult: done
                """.formatted(tempDir);

        ExecutionOutcomeVO outcome = Step2PrecisionExecutorNode.buildExecutionOutcome(output, plan);
        Assert.assertEquals(ExecutionOutcomeVO.SUCCESS, outcome.getStatus());
    }
}
