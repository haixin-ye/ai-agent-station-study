package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.LoopRuntimeOutcomeVO;
import yhx.com.domain.agent.model.valobj.runtime.RunContextStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RunLoopRecordVO;
import yhx.com.domain.agent.model.valobj.runtime.RunRuntimeControlVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeRecoveryCounters;
import yhx.com.domain.agent.model.valobj.runtime.TaskLedgerVO;
import yhx.com.domain.agent.service.runtime.RunTimelineManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RunTimelineManagerTest {

    @Test
    public void one_loop_keeps_decision_request_and_runtime_outcome_in_one_causal_record() {
        RunContextStateVO state = RunContextStateVO.builder()
                .schemaVersion(2)
                .contextVersion(1L)
                .mainAgentStage(MainAgentStageEnumVO.EXECUTING)
                .taskLedger(TaskLedgerVO.builder().version(3L).build())
                .loopTimeline(new ArrayList<>())
                .build();
        RunTimelineManager manager = new RunTimelineManager();

        RunLoopRecordVO record = manager.beginLoop(state, "run-1", 2);
        MainAgentActionVO action = MainAgentActionVO.builder()
                .action("CALL_TOOL")
                .stateDelta(Map.of("toolIntent", Map.of("toolName", "write_file")))
                .build();
        manager.recordDecision(record, action);
        manager.completeLoop(state, record, LoopRuntimeOutcomeVO.builder()
                        .status("SUCCEEDED")
                        .summary("File write succeeded.")
                        .resultPayloadRef("payload-result-1")
                        .evidenceRefs(List.of("evidence-1"))
                        .build(),
                List.of("step-write"), List.of("deliverable-file"), "CALL_TOOL:file:hash");

        Assert.assertEquals(1, state.getLoopTimeline().size());
        Assert.assertSame(action, record.getMainOutput());
        Assert.assertEquals("write_file", ((Map<?, ?>) record.getMainOutput().getStateDelta().get("toolIntent")).get("toolName"));
        Assert.assertEquals("SUCCEEDED", record.getRuntimeOutcome().getStatus());
        Assert.assertEquals("payload-result-1", record.getRuntimeOutcome().getResultPayloadRef());
        Assert.assertEquals(List.of("evidence-1"), record.getRuntimeOutcome().getEvidenceRefs());
        Assert.assertEquals(List.of("step-write"), record.getAffectedStepIds());
        Assert.assertEquals(List.of("deliverable-file"), record.getAffectedDeliverableIds());
        Assert.assertNotNull(record.getCompletedAt());
    }

    @Test
    public void completed_delegation_loop_advances_before_parent_main_agent_resumes() {
        RunContextStateVO state = RunContextStateVO.builder()
                .mainAgentStage(MainAgentStageEnumVO.EXECUTING)
                .taskLedger(TaskLedgerVO.builder().version(1L).build())
                .loopTimeline(new ArrayList<>())
                .build();
        RunTimelineManager manager = new RunTimelineManager();
        RunLoopRecordVO completed = manager.beginLoop(state, "run-1", 0);
        manager.recordDecision(completed, MainAgentActionVO.builder().action("DELEGATE_AGENTS").build());
        manager.completeLoop(state, completed,
                LoopRuntimeOutcomeVO.builder().status("WAITING_CHILDREN").build(),
                List.of(), List.of(), null);
        RuntimeExecutionContext context = RuntimeExecutionContext.builder()
                .runId("run-1")
                .loopIndex(0)
                .recoveryCounters(RuntimeRecoveryCounters.initial())
                .runContextState(state)
                .currentLoopRecord(completed)
                .build();

        RunLoopRecordVO persistedRecord = manager.advanceAfterCompletedLoop(context);

        Assert.assertSame(completed, persistedRecord);
        Assert.assertEquals(Integer.valueOf(1), context.getLoopIndex());
        Assert.assertEquals(Integer.valueOf(1), context.getRecoveryCounters().getLoopCount());
        Assert.assertNull(context.getCurrentLoopRecord());
        Assert.assertEquals(1, state.getLoopTimeline().size());
        Assert.assertEquals(Integer.valueOf(0), state.getLoopTimeline().get(0).getLoopIndex());
        Assert.assertEquals(Integer.valueOf(1), state.getRuntimeControl().getCurrentLoopIndex());
        Assert.assertEquals(Integer.valueOf(1), state.getRuntimeControl().getRecoveryCounters().getLoopCount());
    }

    @Test
    public void restored_cursor_advances_past_latest_completed_record_when_persisted_control_is_stale() {
        RunTimelineManager manager = new RunTimelineManager();
        RunLoopRecordVO completed = RunLoopRecordVO.builder()
                .runId("run-recovery")
                .loopIndex(2)
                .mainAgentStage(MainAgentStageEnumVO.EXECUTING)
                .status("SUCCEEDED")
                .recordVersion(3L)
                .startedAt(java.time.LocalDateTime.now().minusSeconds(1))
                .completedAt(java.time.LocalDateTime.now())
                .build();
        RunContextStateVO state = RunContextStateVO.builder()
                .mainAgentStage(MainAgentStageEnumVO.EXECUTING)
                .taskLedger(TaskLedgerVO.builder().version(1L).build())
                .runtimeControl(RunRuntimeControlVO.builder()
                        .currentLoopIndex(2)
                        .maxLoop(10)
                        .recoveryCounters(RuntimeRecoveryCounters.builder().loopCount(2).build())
                        .build())
                .loopTimeline(new ArrayList<>(List.of(completed)))
                .build();
        RuntimeExecutionContext context = RuntimeExecutionContext.builder()
                .runId("run-recovery")
                .loopIndex(2)
                .maxLoop(10)
                .recoveryCounters(state.getRuntimeControl().getRecoveryCounters())
                .runContextState(state)
                .currentLoopRecord(completed)
                .build();

        boolean changed = manager.reconcileRestoredCursor(context);

        Assert.assertTrue(changed);
        Assert.assertEquals(Integer.valueOf(3), context.getLoopIndex());
        Assert.assertEquals(Integer.valueOf(3), context.getRecoveryCounters().getLoopCount());
        Assert.assertNull(context.getCurrentLoopRecord());
        Assert.assertEquals(Integer.valueOf(3), state.getRuntimeControl().getCurrentLoopIndex());
        Assert.assertEquals(Integer.valueOf(3), state.getRuntimeControl().getRecoveryCounters().getLoopCount());

        context.setCurrentLoopRecord(completed);
        manager.advanceAfterCompletedLoop(context);

        Assert.assertEquals(Integer.valueOf(3), context.getLoopIndex());
        Assert.assertEquals(Integer.valueOf(3), context.getRecoveryCounters().getLoopCount());
    }
}
