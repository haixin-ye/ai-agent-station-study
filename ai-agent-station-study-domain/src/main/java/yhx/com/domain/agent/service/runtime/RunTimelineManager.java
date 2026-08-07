package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.LoopRuntimeOutcomeVO;
import yhx.com.domain.agent.model.valobj.runtime.RunContextStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RunLoopRecordVO;
import yhx.com.domain.agent.model.valobj.runtime.RunRuntimeControlVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RunTimelineManager {

    public RunLoopRecordVO beginLoop(RunContextStateVO state, String runId, Integer loopIndex) {
        requireState(state);
        RunLoopRecordVO record = RunLoopRecordVO.builder()
                .runId(runId)
                .loopIndex(loopIndex)
                .mainAgentStage(state.getMainAgentStage())
                .status("STARTED")
                .recordVersion(1L)
                .taskLedgerVersionBefore(ledgerVersion(state))
                .startedAt(LocalDateTime.now())
                .build();
        timeline(state).add(record);
        return record;
    }

    public void recordDecision(RunLoopRecordVO record, MainAgentActionVO output) {
        requireRecord(record);
        record.setMainOutput(output);
        record.setStatus("DECIDED");
        incrementVersion(record);
    }

    public void markWaitingUser(RunLoopRecordVO record, Map<String, Object> userInteraction) {
        requireRecord(record);
        record.setUserInteraction(userInteraction);
        record.setStatus("WAITING_USER");
        incrementVersion(record);
    }

    public void completeLoop(RunContextStateVO state,
                             RunLoopRecordVO record,
                             LoopRuntimeOutcomeVO outcome,
                             List<String> affectedStepIds,
                             List<String> affectedDeliverableIds,
                             String repeatGuardKey) {
        requireState(state);
        requireRecord(record);
        record.setRuntimeOutcome(outcome);
        record.setAffectedStepIds(defaultList(affectedStepIds));
        record.setAffectedDeliverableIds(defaultList(affectedDeliverableIds));
        record.setRepeatGuardKey(repeatGuardKey);
        record.setTaskLedgerVersionAfter(ledgerVersion(state));
        record.setStatus(outcome == null || outcome.getStatus() == null ? "COMPLETED" : outcome.getStatus());
        record.setCompletedAt(LocalDateTime.now());
        incrementVersion(record);
    }

    public RunLoopRecordVO currentLoop(RunContextStateVO state) {
        List<RunLoopRecordVO> records = state == null ? null : state.getLoopTimeline();
        return records == null || records.isEmpty() ? null : records.get(records.size() - 1);
    }

    public RunLoopRecordVO advanceAfterCompletedLoop(RuntimeExecutionContext context) {
        if (context == null || context.getRunContextState() == null) {
            throw new IllegalArgumentException("Runtime context with canonical run state is required.");
        }
        RunLoopRecordVO completed = context.getCurrentLoopRecord();
        if (completed == null) {
            completed = currentLoop(context.getRunContextState());
        }
        if (completed == null || completed.getCompletedAt() == null) {
            throw new IllegalStateException("A completed loop record is required before advancing the loop.");
        }
        int nextLoopIndex = completed.getLoopIndex() == null ? 1 : completed.getLoopIndex() + 1;
        if (context.getLoopIndex() == null || context.getLoopIndex() < nextLoopIndex) {
            context.setLoopIndex(nextLoopIndex);
        }
        if (context.countersOrInitial().loopCountValue() < nextLoopIndex) {
            context.getRecoveryCounters().setLoopCount(nextLoopIndex);
        }
        context.setCurrentLoopRecord(null);
        syncRuntimeControl(context);
        return completed;
    }

    public boolean reconcileRestoredCursor(RuntimeExecutionContext context) {
        if (context == null || context.getRunContextState() == null) {
            throw new IllegalArgumentException("Runtime context with canonical run state is required.");
        }
        RunLoopRecordVO latest = currentLoop(context.getRunContextState());
        if (latest == null) {
            syncRuntimeControl(context);
            return false;
        }
        if (latest.getCompletedAt() == null) {
            boolean changed = !java.util.Objects.equals(context.getLoopIndex(), latest.getLoopIndex())
                    || context.getCurrentLoopRecord() != latest;
            context.setLoopIndex(latest.getLoopIndex());
            context.setCurrentLoopRecord(latest);
            syncRuntimeControl(context);
            return changed;
        }

        int nextLoopIndex = latest.getLoopIndex() == null ? 0 : latest.getLoopIndex() + 1;
        boolean changed = context.getLoopIndex() == null || context.getLoopIndex() < nextLoopIndex
                || context.getCurrentLoopRecord() != null
                || context.countersOrInitial().loopCountValue() < nextLoopIndex;
        if (context.getLoopIndex() == null || context.getLoopIndex() < nextLoopIndex) {
            context.setLoopIndex(nextLoopIndex);
        }
        context.setCurrentLoopRecord(null);
        if (context.countersOrInitial().loopCountValue() < nextLoopIndex) {
            context.getRecoveryCounters().setLoopCount(nextLoopIndex);
        }
        syncRuntimeControl(context);
        return changed;
    }

    public void syncRuntimeControl(RuntimeExecutionContext context) {
        if (context == null || context.getRunContextState() == null) {
            throw new IllegalArgumentException("Runtime context with canonical run state is required.");
        }
        RunRuntimeControlVO control = context.getRunContextState().getRuntimeControl();
        if (control == null) {
            control = new RunRuntimeControlVO();
            context.getRunContextState().setRuntimeControl(control);
        }
        control.setCurrentLoopIndex(context.getLoopIndex());
        control.setMaxLoop(context.getMaxLoop());
        control.setRecoveryCounters(context.countersOrInitial());
    }

    private List<RunLoopRecordVO> timeline(RunContextStateVO state) {
        if (state.getLoopTimeline() == null) {
            state.setLoopTimeline(new ArrayList<>());
        }
        return state.getLoopTimeline();
    }

    private Long ledgerVersion(RunContextStateVO state) {
        return state.getTaskLedger() == null || state.getTaskLedger().getVersion() == null
                ? 0L : state.getTaskLedger().getVersion();
    }

    private void incrementVersion(RunLoopRecordVO record) {
        record.setRecordVersion(record.getRecordVersion() == null ? 1L : record.getRecordVersion() + 1L);
    }

    private void requireState(RunContextStateVO state) {
        if (state == null || state.getMainAgentStage() == null) {
            throw new IllegalArgumentException("Run context state and MainAgent stage are required.");
        }
    }

    private void requireRecord(RunLoopRecordVO record) {
        if (record == null) {
            throw new IllegalArgumentException("Run loop record is required.");
        }
    }

    private <T> List<T> defaultList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
