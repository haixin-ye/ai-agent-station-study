package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.AgentDispatchResultVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentTaskVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentsRequestVO;
import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentCommitVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;

import java.util.List;

public class AgentDispatchRuntime {

    private final ParentChildRunRegistry registry;

    public AgentDispatchRuntime(ParentChildRunRegistry registry) {
        this.registry = registry == null ? new ParentChildRunRegistry() : registry;
    }

    public AgentDispatchResultVO dispatch(String parentRunId, DelegateAgentsRequestVO request) {
        if (parentRunId == null || parentRunId.isBlank()) {
            throw new IllegalArgumentException("parentRunId is required.");
        }
        if (request == null || request.getTasks() == null || request.getTasks().isEmpty()) {
            throw new IllegalArgumentException("delegate request tasks are required.");
        }
        String batchId = registry.nextDispatchBatchId(parentRunId);
        List<String> childRunIds = request.getTasks().stream()
                .map(task -> registerChild(parentRunId, batchId, request.getWaitMode(), task))
                .toList();
        return AgentDispatchResultVO.builder()
                .parentRunId(parentRunId)
                .waitMode(request.getWaitMode())
                .childRunIds(childRunIds)
                .parentReady(registry.isWaitSatisfied(parentRunId))
                .build();
    }

    public void recordCommit(String childRunId, SubAgentCommitVO commit) {
        registry.markCommitted(childRunId, commit);
    }

    public void recordFailure(String childRunId, String failureMessage) {
        registry.markFailed(childRunId, failureMessage);
    }

    private String registerChild(String parentRunId, String batchId, String waitMode, DelegateAgentTaskVO task) {
        String childRunId = parentRunId + "-child-" + batchId + "-" + task.getTaskId();
        registry.register(ParentChildRunRelationVO.builder()
                .parentRunId(parentRunId)
                .childRunId(childRunId)
                .taskId(task.getTaskId())
                .childName(task.getName())
                .dispatchBatchId(batchId)
                .waitMode(waitMode)
                .status(ChildAgentRunStatusEnumVO.PENDING)
                .build());
        return childRunId;
    }
}
