package yhx.com.test.domain.agent.runtime;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainAgentNotebookVO;
import yhx.com.domain.agent.model.valobj.runtime.NotebookStepVO;
import yhx.com.domain.agent.model.valobj.runtime.RunWorkingStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeContinuationRestoreResultVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeRecoveryCounters;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeWorklogItemVO;
import yhx.com.domain.agent.service.interaction.MainAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.RuntimeContinuationSnapshotService;
import yhx.com.domain.agent.service.interaction.ToolApprovalPendingInputHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuntimeContinuationSnapshotServiceTest {

    private final RuntimeContinuationSnapshotService service = new RuntimeContinuationSnapshotService();

    @Test
    public void versioned_snapshot_survives_json_round_trip_and_restores_complete_runtime_state() {
        RuntimeExecutionContext original = contextWithAccumulatedState();

        ContinuationCheckpointVO checkpoint = service.createCheckpoint(
                original,
                MainAgentPendingInputHandler.HANDLER_CODE,
                RuntimePhaseEnumVO.BUILDING_STATE_VIEW,
                MainAgentPendingInputHandler.HANDLER_CODE,
                "STRING_OR_OPTION",
                Map.of("questionKind", "clarification"));
        ContinuationCheckpointVO durableCopy = JSON.parseObject(JSON.toJSONString(checkpoint), ContinuationCheckpointVO.class);
        RuntimeExecutionContext restored = RuntimeExecutionContext.builder()
                .runId("run-snapshot")
                .sessionId("sess-snapshot")
                .runtimeFacts(new HashMap<>())
                .build();

        RuntimeContinuationRestoreResultVO result = service.restore(durableCopy, restored);

        Assert.assertTrue(result.isRestored());
        Assert.assertFalse(result.isLegacyFallback());
        Assert.assertEquals(Integer.valueOf(1), durableCopy.getSnapshotVersion());
        Assert.assertEquals(Integer.valueOf(6), restored.getLoopIndex());
        Assert.assertEquals(Integer.valueOf(14), restored.getMaxLoop());
        Assert.assertEquals(Integer.valueOf(5), restored.getRecoveryCounters().getLoopCount());
        Assert.assertEquals(Integer.valueOf(2), restored.getRecoveryCounters().getToolRetryCount());
        Assert.assertEquals(Integer.valueOf(3), restored.getRecoveryCounters().getRagRetryCount());
        Assert.assertEquals(Integer.valueOf(1), restored.getRecoveryCounters().getContractRepairCount());
        Assert.assertEquals(Integer.valueOf(1), restored.getRecoveryCounters().getFinalRepairCount());
        Assert.assertEquals(Integer.valueOf(4), restored.getRecoveryCounters().getContextCompressionCount());
        Assert.assertEquals("write story", restored.getWorkingState().getNotebook().getGoal());
        Assert.assertEquals("work-2", restored.getWorkingState().getWorklog().get(0).getWorkId());
        Assert.assertEquals(Long.valueOf(9L), restored.getWorkingState().getNextSequence());
        Assert.assertNotNull(restored.getLastStateView());
        Assert.assertEquals("memory-1", restored.getLastContextSelections().get(0).getSourceId());
        Assert.assertEquals("ASK_USER", restored.getLastAction().getAction());
        Assert.assertEquals("kept", restored.getRuntimeFacts().get("toolDenied"));
        Assert.assertFalse(restored.getRuntimeFacts().containsKey("unsafeClient"));
    }

    @Test
    public void unsupported_snapshot_version_is_rejected_without_partial_restore() {
        ContinuationCheckpointVO checkpoint = service.createCheckpoint(
                contextWithAccumulatedState(),
                MainAgentPendingInputHandler.HANDLER_CODE,
                RuntimePhaseEnumVO.BUILDING_STATE_VIEW,
                MainAgentPendingInputHandler.HANDLER_CODE,
                null,
                Map.of());
        checkpoint.setSnapshotVersion(99);
        RuntimeExecutionContext restored = RuntimeExecutionContext.builder()
                .runId("run-snapshot")
                .runtimeFacts(new HashMap<>())
                .build();

        RuntimeContinuationRestoreResultVO result = service.restore(checkpoint, restored);

        Assert.assertFalse(result.isRestored());
        Assert.assertTrue(result.getMessage().contains("Unsupported"));
        Assert.assertNull(restored.getWorkingState());
    }

    @Test
    public void handler_cannot_resume_at_an_unapproved_runtime_phase() {
        ContinuationCheckpointVO checkpoint = service.createCheckpoint(
                contextWithAccumulatedState(),
                ToolApprovalPendingInputHandler.HANDLER_CODE,
                RuntimePhaseEnumVO.PREPARING_TOOL,
                "ToolApprovalService",
                "OPTION",
                Map.of("approvalKey", "approval-key"));
        checkpoint.setResumePhase(RuntimePhaseEnumVO.REPAIRING_FINAL);

        RuntimeContinuationRestoreResultVO result = service.restore(checkpoint,
                RuntimeExecutionContext.builder().runId("run-snapshot").runtimeFacts(new HashMap<>()).build());

        Assert.assertFalse(result.isRestored());
        Assert.assertTrue(result.getMessage().contains("not allowed"));
    }

    @Test
    public void versioned_snapshot_missing_runtime_budget_is_rejected_without_partial_restore() {
        ContinuationCheckpointVO checkpoint = service.createCheckpoint(
                contextWithAccumulatedState(),
                MainAgentPendingInputHandler.HANDLER_CODE,
                RuntimePhaseEnumVO.BUILDING_STATE_VIEW,
                MainAgentPendingInputHandler.HANDLER_CODE,
                null,
                Map.of());
        checkpoint.getRuntimeSnapshot().setRecoveryCounters(null);
        RuntimeExecutionContext restored = RuntimeExecutionContext.builder()
                .runId("run-snapshot")
                .loopIndex(99)
                .runtimeFacts(new HashMap<>())
                .build();

        RuntimeContinuationRestoreResultVO result = service.restore(checkpoint, restored);

        Assert.assertFalse(result.isRestored());
        Assert.assertTrue(result.getMessage().contains("recoveryCounters"));
        Assert.assertEquals(Integer.valueOf(99), restored.getLoopIndex());
    }

    private RuntimeExecutionContext contextWithAccumulatedState() {
        RunWorkingStateVO workingState = RunWorkingStateVO.builder()
                .notebook(MainAgentNotebookVO.builder()
                        .mode("PER")
                        .goal("write story")
                        .steps(List.of(NotebookStepVO.builder().stepId("step-1").status("COMPLETED").build()))
                        .build())
                .worklog(List.of(RuntimeWorklogItemVO.builder()
                        .workId("work-2")
                        .sequence(8L)
                        .actionType("CALL_TOOL")
                        .status("TOOL_SUCCEEDED")
                        .build()))
                .nextSequence(9L)
                .build();
        Map<String, Object> facts = new HashMap<>();
        facts.put("toolDenied", "kept");
        facts.put("unsafeClient", new Object());
        return RuntimeExecutionContext.builder()
                .runId("run-snapshot")
                .sessionId("sess-snapshot")
                .loopIndex(6)
                .maxLoop(14)
                .recoveryCounters(RuntimeRecoveryCounters.builder()
                        .loopCount(5)
                        .contractRepairCount(1)
                        .finalRepairCount(1)
                        .toolRetryCount(2)
                        .ragRetryCount(3)
                        .contextCompressionCount(4)
                        .build())
                .lastStateView(MainAgentStateViewVO.builder().build())
                .workingState(workingState)
                .lastContextSelections(List.of(ContextSelectionVO.builder()
                        .sourceType("MEMORY")
                        .sourceId("memory-1")
                        .build()))
                .lastAction(MainAgentActionVO.builder().action("ASK_USER").build())
                .runtimeFacts(facts)
                .build();
    }
}
