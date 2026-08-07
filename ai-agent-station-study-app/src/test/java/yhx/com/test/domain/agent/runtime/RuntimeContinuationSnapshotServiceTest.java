package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.runtime.RunBaseContextVO;
import yhx.com.domain.agent.model.valobj.runtime.RunContextStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RunLoopRecordVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeContinuationRestoreResultVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.TaskLedgerVO;
import yhx.com.domain.agent.service.interaction.MainAgentPendingInputHandler;
import yhx.com.domain.agent.service.interaction.RuntimeContinuationSnapshotService;

import java.util.ArrayList;
import java.util.Map;

public class RuntimeContinuationSnapshotServiceTest {

    @Test
    public void checkpoint_contains_only_canonical_context_locator_and_source_payload() {
        RuntimeExecutionContext context = context();
        RuntimeContinuationSnapshotService service = new RuntimeContinuationSnapshotService();

        ContinuationCheckpointVO checkpoint = service.createCheckpoint(context,
                MainAgentPendingInputHandler.HANDLER_CODE, RuntimePhaseEnumVO.BUILDING_STATE_VIEW,
                "MAIN_AGENT", "TEXT", Map.of("question", "Destination?"));

        Assert.assertEquals(Integer.valueOf(2), checkpoint.getSnapshotVersion());
        Assert.assertEquals("run-checkpoint-v2", checkpoint.getRelatedRunId());
        Assert.assertEquals(Integer.valueOf(1), checkpoint.getRelatedLoopIndex());
        Assert.assertEquals(Long.valueOf(4L), checkpoint.getRunContextVersion());
        Assert.assertEquals(Long.valueOf(2L), checkpoint.getLoopRecordVersion());
        Assert.assertEquals("Destination?", checkpoint.getPayload().get("question"));
    }

    @Test
    public void restore_accepts_exact_context_and_loop_versions() {
        RuntimeExecutionContext context = context();
        RuntimeContinuationSnapshotService service = new RuntimeContinuationSnapshotService();
        ContinuationCheckpointVO checkpoint = service.createCheckpoint(context,
                MainAgentPendingInputHandler.HANDLER_CODE, RuntimePhaseEnumVO.BUILDING_STATE_VIEW,
                "MAIN_AGENT", "TEXT", Map.of());

        RuntimeContinuationRestoreResultVO result = service.restore(checkpoint, context);

        Assert.assertTrue(result.getRestored());
        Assert.assertFalse(result.getLegacyFallback());
        Assert.assertEquals(Integer.valueOf(1), context.getLoopIndex());
    }

    @Test
    public void restore_rejects_legacy_or_stale_checkpoint_without_dual_read() {
        RuntimeExecutionContext context = context();
        RuntimeContinuationSnapshotService service = new RuntimeContinuationSnapshotService();
        ContinuationCheckpointVO checkpoint = service.createCheckpoint(context,
                MainAgentPendingInputHandler.HANDLER_CODE, RuntimePhaseEnumVO.BUILDING_STATE_VIEW,
                "MAIN_AGENT", "TEXT", Map.of());
        checkpoint.setSnapshotVersion(1);
        Assert.assertFalse(service.restore(checkpoint, context).getRestored());

        checkpoint.setSnapshotVersion(2);
        checkpoint.setRunContextVersion(3L);
        Assert.assertFalse(service.restore(checkpoint, context).getRestored());
    }

    @Test
    public void restore_rejects_checkpoint_without_context_version_as_invalid_data() {
        RuntimeExecutionContext context = context();
        RuntimeContinuationSnapshotService service = new RuntimeContinuationSnapshotService();
        ContinuationCheckpointVO checkpoint = service.createCheckpoint(context,
                MainAgentPendingInputHandler.HANDLER_CODE, RuntimePhaseEnumVO.BUILDING_STATE_VIEW,
                "MAIN_AGENT", "TEXT", Map.of());
        checkpoint.setRunContextVersion(null);

        RuntimeContinuationRestoreResultVO result = service.restore(checkpoint, context);

        Assert.assertFalse(result.getRestored());
        Assert.assertTrue(result.getMessage().contains("does not contain a Run context version"));
    }

    private RuntimeExecutionContext context() {
        RunLoopRecordVO loop = RunLoopRecordVO.builder()
                .runId("run-checkpoint-v2")
                .loopIndex(1)
                .mainAgentStage(MainAgentStageEnumVO.EXECUTING)
                .status("WAITING_USER")
                .recordVersion(2L)
                .build();
        RunContextStateVO state = RunContextStateVO.builder()
                .schemaVersion(2)
                .contextVersion(4L)
                .mainAgentStage(MainAgentStageEnumVO.EXECUTING)
                .baseContext(RunBaseContextVO.builder().runId("run-checkpoint-v2").build())
                .taskLedger(TaskLedgerVO.builder().version(2L).build())
                .loopTimeline(new ArrayList<>(java.util.List.of(loop)))
                .build();
        return RuntimeExecutionContext.builder()
                .runId("run-checkpoint-v2")
                .loopIndex(1)
                .runContextState(state)
                .currentLoopRecord(loop)
                .build();
    }
}
