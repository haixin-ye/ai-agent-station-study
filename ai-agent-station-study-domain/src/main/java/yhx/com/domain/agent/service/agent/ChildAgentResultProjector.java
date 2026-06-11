package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentCommitVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.runtime.ActionEffectVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.RunWorkingStateManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChildAgentResultProjector {

    public static final String SOURCE_COMPONENT = "GENERIC_SUB_AGENT";
    private static final String FAILURE_CODE = "CHILD_AGENT_FAILED";

    private final RunWorkingStateManager stateManager;

    public ChildAgentResultProjector(RunWorkingStateManager stateManager) {
        this.stateManager = stateManager == null ? new RunWorkingStateManager() : stateManager;
    }

    public void project(RuntimeExecutionContext context, ParentChildRunRelationVO relation) {
        if (context == null) {
            throw new IllegalArgumentException("Runtime context is required.");
        }
        validateRelation(relation);
        ensureWorkingState(context);

        Long sequence = context.getWorkingState().getNextSequence() == null ? 1L : context.getWorkingState().getNextSequence();
        String workId = workId(relation);
        MaterializedEvidenceVO evidence = buildEvidence(context, relation, sequence, workId);
        Map<String, Object> requestSnapshot = requestSnapshot(relation);
        Map<String, Object> resultSnapshot = resultSnapshot(relation);
        Map<String, Object> metadata = metadata(relation);

        ActionEffectVO effect = ActionEffectVO.builder()
                .action(MainAgentActionTypeEnumVO.DELEGATE_AGENTS.code())
                .status(status(relation))
                .message(message(relation))
                .sourceComponent(SOURCE_COMPONENT)
                .loopIndex(context.getLoopIndex())
                .workId(workId)
                .resultRef(evidence.getEvidenceId())
                .requestSnapshot(requestSnapshot)
                .resultSnapshot(resultSnapshot)
                .createdEvidenceIds(List.of(evidence.getEvidenceId()))
                .createdEvidence(List.of(evidence))
                .failureCode(isFailed(relation) ? FAILURE_CODE : null)
                .failureMessage(isFailed(relation) ? failureMessage(relation) : null)
                .metadata(metadata)
                .build();

        stateManager.applyRuntimeEffect(context, effect);
    }

    private void validateRelation(ParentChildRunRelationVO relation) {
        if (relation == null) {
            throw new IllegalArgumentException("Parent-child relation is required.");
        }
        if (isBlank(relation.getParentRunId()) || isBlank(relation.getChildRunId()) || isBlank(relation.getTaskId())) {
            throw new IllegalArgumentException("Parent-child relation requires parentRunId, childRunId, and taskId.");
        }
        if (relation.getStatus() == null || !relation.getStatus().terminal()) {
            throw new IllegalArgumentException("Only terminal child results can be projected.");
        }
    }

    private void ensureWorkingState(RuntimeExecutionContext context) {
        if (context.getWorkingState() != null) {
            return;
        }
        MainAgentStateViewVO base = context.getLastStateView() == null
                ? MainAgentStateViewVO.builder().build()
                : context.getLastStateView();
        context.setWorkingState(stateManager.initialize(base));
    }

    private MaterializedEvidenceVO buildEvidence(RuntimeExecutionContext context,
                                                 ParentChildRunRelationVO relation,
                                                 Long sequence,
                                                 String workId) {
        String content = evidenceContent(relation);
        return MaterializedEvidenceVO.builder()
                .evidenceId(evidenceId(relation))
                .evidenceType(isFailed(relation) ? "SUB_AGENT_FAILURE" : "SUB_AGENT")
                .sourceRef(relation.getChildRunId())
                .summary(summary(relation))
                .boundedSnippet(content)
                .content(content)
                .contentFormat("text/plain")
                .truncated(false)
                .totalChars(content.length())
                .sequence(sequence)
                .sourceLoopIndex(context.getLoopIndex())
                .sourceWorkId(workId)
                .createdAt(LocalDateTime.now())
                .metadata(metadata(relation))
                .build();
    }

    private Map<String, Object> requestSnapshot(ParentChildRunRelationVO relation) {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "parentRunId", relation.getParentRunId());
        put(map, "childRunId", relation.getChildRunId());
        put(map, "taskId", relation.getTaskId());
        put(map, "childName", relation.getChildName());
        put(map, "dispatchBatchId", relation.getDispatchBatchId());
        put(map, "waitMode", relation.getWaitMode());
        return map;
    }

    private Map<String, Object> resultSnapshot(ParentChildRunRelationVO relation) {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "childRunStatus", relation.getStatus() == null ? null : relation.getStatus().code());
        put(map, "status", status(relation));
        if (relation.getCommit() != null) {
            put(map, "result", relation.getCommit().getResult());
            put(map, "detail", relation.getCommit().getDetail());
            put(map, "safeForUserVisibleUse", relation.getCommit().getSafeForUserVisibleUse());
        }
        if (isFailed(relation)) {
            put(map, "failureMessage", failureMessage(relation));
        }
        return map;
    }

    private Map<String, Object> metadata(ParentChildRunRelationVO relation) {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "parentRunId", relation.getParentRunId());
        put(map, "childRunId", relation.getChildRunId());
        put(map, "taskId", relation.getTaskId());
        put(map, "childName", relation.getChildName());
        put(map, "dispatchBatchId", relation.getDispatchBatchId());
        put(map, "waitMode", relation.getWaitMode());
        put(map, "childRunStatus", relation.getStatus() == null ? null : relation.getStatus().code());
        put(map, "fullContextSnapshotRef", relation.getFullContextSnapshotRef());
        if (relation.getCommit() != null) {
            SubAgentCommitVO commit = relation.getCommit();
            put(map, "childCommitStatus", commit.getStatus());
            put(map, "evidenceRefs", commit.getEvidenceRefs());
            put(map, "inspectedResources", commit.getInspectedResources());
            put(map, "assumptions", commit.getAssumptions());
            put(map, "blockers", commit.getBlockers());
            put(map, "suggestedParentNextStep", commit.getSuggestedParentNextStep());
            put(map, "safeForUserVisibleUse", commit.getSafeForUserVisibleUse());
        }
        return map;
    }

    private String evidenceContent(ParentChildRunRelationVO relation) {
        if (isFailed(relation)) {
            return failureMessage(relation);
        }
        SubAgentCommitVO commit = relation.getCommit();
        if (commit == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendSection(builder, "Result", commit.getResult());
        appendSection(builder, "Detail", commit.getDetail());
        appendListSection(builder, "Evidence refs", commit.getEvidenceRefs());
        appendListSection(builder, "Inspected resources", commit.getInspectedResources());
        appendListSection(builder, "Assumptions", commit.getAssumptions());
        appendListSection(builder, "Blockers", commit.getBlockers());
        appendSection(builder, "Suggested parent next step", commit.getSuggestedParentNextStep());
        appendSection(builder, "Full context snapshot ref", relation.getFullContextSnapshotRef());
        return builder.toString().trim();
    }

    private void appendSection(StringBuilder builder, String title, String value) {
        if (isBlank(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append(title).append(":\n").append(value);
    }

    private void appendListSection(StringBuilder builder, String title, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        appendSection(builder, title, String.join("\n", values));
    }

    private String status(ParentChildRunRelationVO relation) {
        if (isFailed(relation)) {
            return "FAILED";
        }
        if (relation.getCommit() != null && !isBlank(relation.getCommit().getStatus())) {
            return relation.getCommit().getStatus();
        }
        return relation.getStatus() == null ? "COMMITTED" : relation.getStatus().code();
    }

    private String message(ParentChildRunRelationVO relation) {
        if (isFailed(relation)) {
            return "Child agent failed: " + failureMessage(relation);
        }
        return "Child agent committed: " + relation.getChildName();
    }

    private String summary(ParentChildRunRelationVO relation) {
        if (isFailed(relation)) {
            return "Child agent failed: " + relation.getChildName();
        }
        return "Child agent committed result: " + relation.getChildName();
    }

    private String failureMessage(ParentChildRunRelationVO relation) {
        return isBlank(relation.getFailureMessage()) ? "Child agent failed without a message." : relation.getFailureMessage();
    }

    private boolean isFailed(ParentChildRunRelationVO relation) {
        return relation != null && ChildAgentRunStatusEnumVO.FAILED == relation.getStatus();
    }

    private String evidenceId(ParentChildRunRelationVO relation) {
        return "evidence-" + relation.getChildRunId() + (isFailed(relation) ? "-failure" : "-commit");
    }

    private String workId(ParentChildRunRelationVO relation) {
        return "work-" + relation.getChildRunId() + (isFailed(relation) ? "-failure" : "-commit");
    }

    private void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
