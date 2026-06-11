package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionExecutionContextVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionHandlerResultVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;

import java.util.Map;

public class BlockedSubAgentActionHandler implements SubAgentActionHandler {

    private final String actionType;
    private final ParentChildRunRegistry registry;

    public BlockedSubAgentActionHandler(String actionType, ParentChildRunRegistry registry) {
        this.actionType = actionType;
        this.registry = registry == null ? new ParentChildRunRegistry() : registry;
    }

    @Override
    public String actionType() {
        return actionType;
    }

    @Override
    public SubAgentActionHandlerResultVO handle(SubAgentActionExecutionContextVO context, SubAgentActionVO action) {
        ParentChildRunRelationVO relation = context == null ? null : context.getRelation();
        String failureMessage = "Generic subagent action routing is not implemented for " + safeAction(actionType) + ".";
        registry.markFailed(relation.getChildRunId(), failureMessage);
        return SubAgentActionHandlerResultVO.builder()
                .action(actionType)
                .terminal(true)
                .status(ChildAgentRunStatusEnumVO.FAILED)
                .failureMessage(failureMessage)
                .message(failureMessage)
                .resultSnapshot(Map.of(
                        "action", safeAction(actionType),
                        "status", ChildAgentRunStatusEnumVO.FAILED.code(),
                        "failureMessage", failureMessage))
                .build();
    }

    private String safeAction(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
