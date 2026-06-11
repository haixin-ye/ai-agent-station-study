package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionExecutionContextVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionHandlerResultVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentCommitVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.SubAgentCommitStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.SubAgentActionTypeEnumVO;

import java.util.Map;

public class CommitSubAgentActionHandler implements SubAgentActionHandler {

    private final ParentChildRunRegistry registry;

    public CommitSubAgentActionHandler(ParentChildRunRegistry registry) {
        this.registry = registry == null ? new ParentChildRunRegistry() : registry;
    }

    @Override
    public String actionType() {
        return SubAgentActionTypeEnumVO.COMMIT.code();
    }

    @Override
    public SubAgentActionHandlerResultVO handle(SubAgentActionExecutionContextVO context, SubAgentActionVO action) {
        ParentChildRunRelationVO relation = context == null ? null : context.getRelation();
        SubAgentCommitVO commit = action == null ? null : action.getCommit();
        if (commit == null) {
            return failed(relation, "Generic subagent COMMIT action is missing commit payload.");
        }
        String validationFailure = validateCommit(relation, commit);
        if (validationFailure != null) {
            return failed(relation, validationFailure);
        }
        registry.markCommitted(relation.getChildRunId(), commit);
        return SubAgentActionHandlerResultVO.builder()
                .action(actionType())
                .terminal(true)
                .status(ChildAgentRunStatusEnumVO.COMMITTED)
                .commit(commit)
                .message("Generic subagent committed result.")
                .resultSnapshot(Map.of(
                        "action", actionType(),
                        "status", ChildAgentRunStatusEnumVO.COMMITTED.code(),
                        "taskId", commit.getTaskId(),
                        "commitStatus", commit.getStatus()))
                .build();
    }

    private SubAgentActionHandlerResultVO failed(ParentChildRunRelationVO relation, String failureMessage) {
        registry.markFailed(relation.getChildRunId(), failureMessage);
        return SubAgentActionHandlerResultVO.builder()
                .action(actionType())
                .terminal(true)
                .status(ChildAgentRunStatusEnumVO.FAILED)
                .failureMessage(failureMessage)
                .message(failureMessage)
                .resultSnapshot(Map.of(
                        "action", actionType(),
                        "status", ChildAgentRunStatusEnumVO.FAILED.code(),
                        "failureMessage", failureMessage))
                .build();
    }

    private String validateCommit(ParentChildRunRelationVO relation, SubAgentCommitVO commit) {
        if (relation == null || relation.getChildRunId() == null || relation.getChildRunId().isBlank()) {
            return "Generic subagent COMMIT requires a registered child relation.";
        }
        if (isBlank(commit.getTaskId())) {
            return "Generic subagent COMMIT requires commit.taskId.";
        }
        if (!commit.getTaskId().equals(relation.getTaskId())) {
            return "Generic subagent COMMIT taskId does not match delegated taskId: "
                    + commit.getTaskId() + " != " + relation.getTaskId() + ".";
        }
        if (isBlank(commit.getStatus()) || SubAgentCommitStatusEnumVO.ofCode(commit.getStatus()).isEmpty()) {
            return "Generic subagent COMMIT requires a valid commit.status.";
        }
        if (isBlank(commit.getResult())) {
            return "Generic subagent COMMIT requires commit.result.";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
