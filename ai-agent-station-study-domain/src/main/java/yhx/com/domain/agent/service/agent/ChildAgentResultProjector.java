package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentCommitVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.LoopRuntimeOutcomeVO;
import yhx.com.domain.agent.model.valobj.runtime.RunLoopRecordVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChildAgentResultProjector {

    private static final String CHILD_RESULTS = "childAgentResults";

    public void project(RuntimeExecutionContext context, ParentChildRunRelationVO relation) {
        validate(context, relation);
        RunLoopRecordVO dispatchRecord = findDispatchRecord(context, relation);
        LoopRuntimeOutcomeVO outcome = dispatchRecord.getRuntimeOutcome();
        if (outcome == null) {
            outcome = LoopRuntimeOutcomeVO.builder()
                    .status("WAITING_CHILDREN")
                    .summary("Waiting for delegated child agents.")
                    .details(new LinkedHashMap<>())
                    .evidenceRefs(new ArrayList<>())
                    .build();
            dispatchRecord.setRuntimeOutcome(outcome);
        }
        Map<String, Object> details = new LinkedHashMap<>(outcome.getDetails() == null ? Map.of() : outcome.getDetails());
        Map<String, Object> childResults = mutableMap(details.get(CHILD_RESULTS));
        childResults.put(relation.getChildRunId(), childResult(relation));
        details.put(CHILD_RESULTS, childResults);
        outcome.setDetails(details);
        outcome.setEvidenceRefs(mergeEvidenceRefs(outcome.getEvidenceRefs(), relation));
        outcome.setSummary("Delegated child results recorded: " + childResults.size() + ".");
        dispatchRecord.setRecordVersion(dispatchRecord.getRecordVersion() == null ? 1L : dispatchRecord.getRecordVersion() + 1L);
        context.setCurrentLoopRecord(dispatchRecord);
    }

    private RunLoopRecordVO findDispatchRecord(RuntimeExecutionContext context, ParentChildRunRelationVO relation) {
        List<RunLoopRecordVO> timeline = context.getRunContextState().getLoopTimeline();
        for (int index = timeline.size() - 1; index >= 0; index--) {
            RunLoopRecordVO record = timeline.get(index);
            if (record != null && record.getMainOutput() != null
                    && MainAgentActionTypeEnumVO.DELEGATE_AGENTS.code().equals(record.getMainOutput().getAction())
                    && dispatchedTask(record, relation.getTaskId())) {
                return record;
            }
        }
        throw new IllegalStateException("Delegated child result has no matching RunLoopRecord: " + relation.getChildRunId());
    }

    private boolean dispatchedTask(RunLoopRecordVO record, String taskId) {
        if (record.getMainOutput() == null || record.getMainOutput().getStateDelta() == null) {
            return false;
        }
        Object requestValue = record.getMainOutput().getStateDelta().get("delegateAgentsRequest");
        if (!(requestValue instanceof Map<?, ?> request)) {
            return false;
        }
        Object tasksValue = request.get("tasks");
        if (!(tasksValue instanceof Iterable<?> tasks)) {
            return false;
        }
        for (Object taskValue : tasks) {
            if (taskValue instanceof Map<?, ?> task && taskId.equals(String.valueOf(task.get("taskId")))) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> childResult(ParentChildRunRelationVO relation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", relation.getTaskId());
        result.put("childRunId", relation.getChildRunId());
        result.put("childName", relation.getChildName());
        result.put("status", relation.getStatus().code());
        result.put("fullContextSnapshotRef", relation.getFullContextSnapshotRef());
        if (relation.getCommit() != null) {
            SubAgentCommitVO commit = relation.getCommit();
            result.put("commitStatus", commit.getStatus());
            result.put("result", commit.getResult());
            result.put("detail", commit.getDetail());
            result.put("evidenceRefs", defaultList(commit.getEvidenceRefs()));
            result.put("inspectedResources", defaultList(commit.getInspectedResources()));
            result.put("assumptions", defaultList(commit.getAssumptions()));
            result.put("blockers", defaultList(commit.getBlockers()));
            result.put("suggestedParentNextStep", commit.getSuggestedParentNextStep());
            result.put("safeForUserVisibleUse", commit.getSafeForUserVisibleUse());
        }
        if (relation.getStatus() == ChildAgentRunStatusEnumVO.FAILED) {
            result.put("failureMessage", relation.getFailureMessage());
        }
        return result;
    }

    private List<String> mergeEvidenceRefs(List<String> existing, ParentChildRunRelationVO relation) {
        Set<String> refs = new LinkedHashSet<>(defaultList(existing));
        if (relation.getCommit() != null) {
            refs.addAll(defaultList(relation.getCommit().getEvidenceRefs()));
        }
        return new ArrayList<>(refs);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableMap(Object value) {
        return value instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
    }

    private void validate(RuntimeExecutionContext context, ParentChildRunRelationVO relation) {
        if (context == null || context.getRunContextState() == null
                || context.getRunContextState().getLoopTimeline() == null) {
            throw new IllegalArgumentException("Canonical RunContextState is required.");
        }
        if (relation == null || relation.getStatus() == null || !relation.getStatus().terminal()) {
            throw new IllegalArgumentException("A terminal child relation is required.");
        }
        if (relation.getParentRunId() == null || !relation.getParentRunId().equals(context.getRunId())
                || relation.getChildRunId() == null || relation.getTaskId() == null) {
            throw new IllegalArgumentException("Parent-child relation identity is invalid.");
        }
    }

    private <T> List<T> defaultList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
