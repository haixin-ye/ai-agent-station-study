package yhx.com.domain.agent.service.runtime;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.model.valobj.runtime.TaskDeliverableVO;
import yhx.com.domain.agent.model.valobj.runtime.TaskLedgerVO;
import yhx.com.domain.agent.model.valobj.runtime.TaskPlanRevisionVO;
import yhx.com.domain.agent.model.valobj.runtime.TaskStepVO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskLedgerMergeService {

    public TaskLedgerVO merge(TaskLedgerVO current, Map<String, Object> update, Integer loopIndex) {
        TaskLedgerVO ledger = current == null ? emptyLedger() : copy(current);
        if (update == null || update.isEmpty()) {
            return ledger;
        }
        setString(update, "goal", ledger::setGoal);
        setString(update, "currentStepId", ledger::setCurrentStepId);
        setString(update, "lastDecision", ledger::setLastDecision);
        ledger.setDeliverables(mergeDeliverables(ledger.getDeliverables(), list(update, "deliverableUpdates")));
        ledger.setSteps(mergeSteps(ledger.getSteps(), list(update, "stepUpdates")));
        ledger.setFacts(mergeMap(ledger.getFacts(), map(update, "facts")));
        if (update.containsKey("blockers")) {
            ledger.setBlockers(stringList(update.get("blockers")));
        }
        Map<String, Object> revision = map(update, "planRevision");
        if (revision != null && !revision.isEmpty()) {
            List<TaskPlanRevisionVO> revisions = new ArrayList<>(defaultList(ledger.getPlanRevisions()));
            revisions.add(TaskPlanRevisionVO.builder()
                    .revisionNo((long) revisions.size() + 1L)
                    .reason(string(revision.get("reason")))
                    .retainedStepIds(stringList(revision.get("retainedStepIds")))
                    .addedStepIds(stringList(revision.get("addedStepIds")))
                    .cancelledStepIds(stringList(revision.get("cancelledStepIds")))
                    .loopIndex(loopIndex)
                    .createdAt(LocalDateTime.now())
                    .build());
            ledger.setPlanRevisions(revisions);
        }
        ledger.setVersion((ledger.getVersion() == null ? 0L : ledger.getVersion()) + 1L);
        return ledger;
    }

    private List<TaskDeliverableVO> mergeDeliverables(List<TaskDeliverableVO> existing, List<Map<String, Object>> updates) {
        Map<String, TaskDeliverableVO> merged = new LinkedHashMap<>();
        defaultList(existing).forEach(item -> merged.put(item.getDeliverableId(), item));
        for (Map<String, Object> update : updates) {
            String id = string(update.get("deliverableId"));
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("taskUpdate.deliverableUpdates[].deliverableId is required.");
            }
            TaskDeliverableVO value = merged.getOrDefault(id, TaskDeliverableVO.builder().deliverableId(id).build());
            setString(update, "description", value::setDescription);
            setString(update, "status", value::setStatus);
            if (update.containsKey("acceptanceCriteria")) value.setAcceptanceCriteria(stringList(update.get("acceptanceCriteria")));
            if (update.containsKey("relatedStepIds")) value.setRelatedStepIds(stringList(update.get("relatedStepIds")));
            if (update.containsKey("evidenceRefs")) value.setEvidenceRefs(stringList(update.get("evidenceRefs")));
            if (update.containsKey("payloadRefs")) value.setPayloadRefs(stringList(update.get("payloadRefs")));
            merged.put(id, value);
        }
        return new ArrayList<>(merged.values());
    }

    private List<TaskStepVO> mergeSteps(List<TaskStepVO> existing, List<Map<String, Object>> updates) {
        Map<String, TaskStepVO> merged = new LinkedHashMap<>();
        defaultList(existing).forEach(item -> merged.put(item.getStepId(), item));
        for (Map<String, Object> update : updates) {
            String id = string(update.get("stepId"));
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("taskUpdate.stepUpdates[].stepId is required.");
            }
            TaskStepVO value = merged.getOrDefault(id, TaskStepVO.builder().stepId(id).build());
            setString(update, "description", value::setDescription);
            setString(update, "status", value::setStatus);
            if (update.containsKey("dependsOn")) value.setDependsOn(stringList(update.get("dependsOn")));
            if (update.containsKey("affectedDeliverableIds")) value.setAffectedDeliverableIds(stringList(update.get("affectedDeliverableIds")));
            if (update.containsKey("resultRefs")) value.setResultRefs(stringList(update.get("resultRefs")));
            merged.put(id, value);
        }
        return new ArrayList<>(merged.values());
    }

    private TaskLedgerVO emptyLedger() {
        return TaskLedgerVO.builder().version(0L).deliverables(new ArrayList<>()).steps(new ArrayList<>())
                .planRevisions(new ArrayList<>()).facts(new LinkedHashMap<>()).blockers(new ArrayList<>()).build();
    }

    private TaskLedgerVO copy(TaskLedgerVO value) {
        return JSON.parseObject(JSON.toJSONString(value), TaskLedgerVO.class);
    }

    private Map<String, Object> mergeMap(Map<String, Object> existing, Map<String, Object> update) {
        Map<String, Object> merged = new LinkedHashMap<>(existing == null ? Map.of() : existing);
        if (update != null) merged.putAll(update);
        return merged;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        iterable.forEach(item -> result.add(String.valueOf(item)));
        return result;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void setString(Map<String, Object> source, String key, java.util.function.Consumer<String> setter) {
        if (source.containsKey(key)) setter.accept(string(source.get(key)));
    }

    private <T> List<T> defaultList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
