package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
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

    public void register(ParentChildRunRelationVO relation) {
        if (relation == null || relation.getParentRunId() == null || relation.getChildRunId() == null) {
            throw new IllegalArgumentException("Parent-child relation requires parentRunId and childRunId.");
        }
        byChildRunId.put(relation.getChildRunId(), relation);
        childrenByParentRunId.computeIfAbsent(relation.getParentRunId(), ignored -> new ArrayList<>())
                .add(relation.getChildRunId());
    }

    public List<ParentChildRunRelationVO> listChildren(String parentRunId) {
        List<String> childIds = childrenByParentRunId.getOrDefault(parentRunId, List.of());
        return childIds.stream().map(byChildRunId::get).toList();
    }

    public Optional<ParentChildRunRelationVO> findByChildRunId(String childRunId) {
        return Optional.ofNullable(byChildRunId.get(childRunId));
    }

    public void markCommitted(String childRunId, SubAgentCommitVO commit) {
        ParentChildRunRelationVO relation = requireChild(childRunId);
        relation.setStatus(ChildAgentRunStatusEnumVO.COMMITTED);
        relation.setCommit(commit);
    }

    public void markFailed(String childRunId, String failureMessage) {
        ParentChildRunRelationVO relation = requireChild(childRunId);
        relation.setStatus(ChildAgentRunStatusEnumVO.FAILED);
        relation.setFailureMessage(failureMessage);
    }

    public boolean isWaitSatisfied(String parentRunId) {
        List<ParentChildRunRelationVO> children = listChildren(parentRunId);
        return !children.isEmpty() && children.stream()
                .allMatch(child -> child.getStatus() != null && child.getStatus().terminal());
    }

    private ParentChildRunRelationVO requireChild(String childRunId) {
        ParentChildRunRelationVO relation = byChildRunId.get(childRunId);
        if (relation == null) {
            throw new IllegalArgumentException("Child run is missing: " + childRunId);
        }
        return relation;
    }
}
