package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionExecutionContextVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionHandlerResultVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.SubAgentActionTypeEnumVO;

import java.util.Map;

public class FailSubAgentActionHandler implements SubAgentActionHandler {

    private final ParentChildRunRegistry registry;

    public FailSubAgentActionHandler(ParentChildRunRegistry registry) {
        this.registry = registry == null ? new ParentChildRunRegistry() : registry;
    }

    @Override
    public String actionType() {
        return SubAgentActionTypeEnumVO.FAIL.code();
    }

    @Override
    public SubAgentActionHandlerResultVO handle(SubAgentActionExecutionContextVO context, SubAgentActionVO action) {
        ParentChildRunRelationVO relation = context == null ? null : context.getRelation();
        String failureMessage = failureMessage(action);
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

    private String failureMessage(SubAgentActionVO action) {
        if (action == null || action.getActionInput() == null || action.getActionInput().isEmpty()) {
            return "Generic subagent returned FAIL.";
        }
        Object message = action.getActionInput().get("message");
        if (message == null) {
            message = action.getActionInput().get("reason");
        }
        return message == null ? "Generic subagent returned FAIL." : String.valueOf(message);
    }
}
