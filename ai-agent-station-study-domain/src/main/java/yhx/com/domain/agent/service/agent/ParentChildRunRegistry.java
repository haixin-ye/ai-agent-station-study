package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentContinuationVO;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRegistrySnapshotVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentCommitVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ParentChildRunRegistry {

    private final Map<String, ParentChildRunRelationVO> byChildRunId = new LinkedHashMap<>();
    private final Map<String, List<String>> childrenByParentRunId = new LinkedHashMap<>();
    private final Map<String, GenericSubAgentContinuationVO> continuationsByChildRunId = new LinkedHashMap<>();
    private final List<String> resumeRequestedParentRunIds = new ArrayList<>();
    private final ParentChildRunRegistryStore store;

    public ParentChildRunRegistry() {
        this(null);
    }

    public ParentChildRunRegistry(ParentChildRunRegistryStore store) {
        this.store = store;
    }

    public synchronized void register(ParentChildRunRelationVO relation) {
        if (relation == null || relation.getParentRunId() == null || relation.getChildRunId() == null) {
            throw new IllegalArgumentException("Parent-child relation requires parentRunId and childRunId.");
        }
        if (byChildRunId.containsKey(relation.getChildRunId())) {
            throw new IllegalArgumentException("Child run already exists: " + relation.getChildRunId());
        }
        byChildRunId.put(relation.getChildRunId(), relation);
        List<String> childIds = childrenByParentRunId.computeIfAbsent(relation.getParentRunId(), ignored -> new ArrayList<>());
        if (!childIds.contains(relation.getChildRunId())) {
            childIds.add(relation.getChildRunId());
        }
        persistParent(relation.getParentRunId());
    }

    public synchronized List<ParentChildRunRelationVO> listChildren(String parentRunId) {
        List<String> childIds = childrenByParentRunId.getOrDefault(parentRunId, List.of());
        return childIds.stream().map(byChildRunId::get).toList();
    }

    public synchronized Optional<ParentChildRunRelationVO> findByChildRunId(String childRunId) {
        return Optional.ofNullable(byChildRunId.get(childRunId));
    }

    public synchronized void markRunning(String childRunId) {
        ParentChildRunRelationVO relation = requireChild(childRunId);
        relation.setStatus(ChildAgentRunStatusEnumVO.RUNNING);
        persistParent(relation.getParentRunId());
    }

    public synchronized void markCommitted(String childRunId, SubAgentCommitVO commit) {
        ParentChildRunRelationVO relation = requireChild(childRunId);
        relation.setStatus(ChildAgentRunStatusEnumVO.COMMITTED);
        relation.setCommit(commit);
        continuationsByChildRunId.remove(childRunId);
        persistParent(relation.getParentRunId());
    }

    public synchronized void markFailed(String childRunId, String failureMessage) {
        ParentChildRunRelationVO relation = requireChild(childRunId);
        relation.setStatus(ChildAgentRunStatusEnumVO.FAILED);
        relation.setFailureMessage(failureMessage);
        continuationsByChildRunId.remove(childRunId);
        persistParent(relation.getParentRunId());
    }

    public synchronized void markWaitingUser(String childRunId, String pendingInputId) {
        ParentChildRunRelationVO relation = requireChild(childRunId);
        relation.setStatus(ChildAgentRunStatusEnumVO.WAITING_USER);
        relation.setPendingInputId(pendingInputId);
        persistParent(relation.getParentRunId());
    }

    public synchronized void saveContinuation(GenericSubAgentContinuationVO continuation) {
        if (continuation == null || continuation.getChildRunId() == null || continuation.getChildRunId().isBlank()) {
            throw new IllegalArgumentException("Generic subagent continuation requires childRunId.");
        }
        continuationsByChildRunId.put(continuation.getChildRunId(), continuation);
        persistParent(continuation.getParentRunId());
    }

    public synchronized Optional<GenericSubAgentContinuationVO> findContinuation(String childRunId) {
        return Optional.ofNullable(continuationsByChildRunId.get(childRunId));
    }

    public synchronized boolean isWaitSatisfied(String parentRunId) {
        List<ParentChildRunRelationVO> children = listChildren(parentRunId);
        return !children.isEmpty() && children.stream()
                .allMatch(child -> child.getStatus() != null && child.getStatus().terminal());
    }

    public synchronized boolean markParentResumeRequested(String parentRunId) {
        if (parentRunId == null || parentRunId.isBlank() || resumeRequestedParentRunIds.contains(parentRunId)) {
            return false;
        }
        resumeRequestedParentRunIds.add(parentRunId);
        return true;
    }

    public synchronized String nextDispatchBatchId(String parentRunId) {
        if (parentRunId == null || parentRunId.isBlank()) {
            throw new IllegalArgumentException("parentRunId is required.");
        }
        int next = childrenByParentRunId.getOrDefault(parentRunId, List.of()).stream()
                .map(byChildRunId::get)
                .filter(relation -> relation != null && relation.getDispatchBatchId() != null)
                .map(ParentChildRunRegistry::batchNumber)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        return "b" + next;
    }

    public synchronized void restoreParent(String parentRunId) {
        if (store == null || parentRunId == null || parentRunId.isBlank()) {
            return;
        }
        store.loadParent(parentRunId).ifPresent(this::restore);
    }

    private ParentChildRunRelationVO requireChild(String childRunId) {
        ParentChildRunRelationVO relation = byChildRunId.get(childRunId);
        if (relation == null) {
            throw new IllegalArgumentException("Child run is missing: " + childRunId);
        }
        return relation;
    }

    private void persistParent(String parentRunId) {
        if (store == null || parentRunId == null || parentRunId.isBlank()) {
            return;
        }
        store.saveParent(parentRunId, listChildren(parentRunId), continuations(parentRunId));
    }

    private List<GenericSubAgentContinuationVO> continuations(String parentRunId) {
        return continuationsByChildRunId.values().stream()
                .filter(continuation -> parentRunId.equals(continuation.getParentRunId()))
                .toList();
    }

    private void restore(ParentChildRunRegistrySnapshotVO snapshot) {
        if (snapshot == null || snapshot.getParentRunId() == null || snapshot.getParentRunId().isBlank()) {
            return;
        }
        List<String> previousChildIds = childrenByParentRunId.remove(snapshot.getParentRunId());
        if (previousChildIds != null) {
            previousChildIds.forEach(childRunId -> {
                byChildRunId.remove(childRunId);
                continuationsByChildRunId.remove(childRunId);
            });
        }
        List<ParentChildRunRelationVO> relations = snapshot.getRelations() == null ? List.of() : snapshot.getRelations();
        relations.forEach(this::restoreRelation);
        List<GenericSubAgentContinuationVO> continuations = snapshot.getContinuations() == null ? List.of() : snapshot.getContinuations();
        continuations.stream()
                .filter(continuation -> continuation != null && continuation.getChildRunId() != null)
                .forEach(continuation -> continuationsByChildRunId.put(continuation.getChildRunId(), continuation));
    }

    private void restoreRelation(ParentChildRunRelationVO relation) {
        if (relation == null || relation.getParentRunId() == null || relation.getChildRunId() == null) {
            return;
        }
        byChildRunId.put(relation.getChildRunId(), relation);
        List<String> childIds = childrenByParentRunId.computeIfAbsent(relation.getParentRunId(), ignored -> new ArrayList<>());
        if (!childIds.contains(relation.getChildRunId())) {
            childIds.add(relation.getChildRunId());
        }
    }

    private static int batchNumber(ParentChildRunRelationVO relation) {
        String value = relation == null ? null : relation.getDispatchBatchId();
        if (value == null || value.length() < 2 || value.charAt(0) != 'b') {
            return 0;
        }
        try {
            return Integer.parseInt(value.substring(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
