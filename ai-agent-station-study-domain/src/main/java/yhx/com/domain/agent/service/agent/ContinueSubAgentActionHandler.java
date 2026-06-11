package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.SubAgentActionExecutionContextVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionHandlerResultVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.model.valobj.enums.agent.SubAgentActionTypeEnumVO;

import java.util.Map;

public class ContinueSubAgentActionHandler implements SubAgentActionHandler {

    @Override
    public String actionType() {
        return SubAgentActionTypeEnumVO.CONTINUE.code();
    }

    @Override
    public SubAgentActionHandlerResultVO handle(SubAgentActionExecutionContextVO context, SubAgentActionVO action) {
        return SubAgentActionHandlerResultVO.builder()
                .action(actionType())
                .terminal(false)
                .message("Generic subagent CONTINUE recorded; no external action was executed in this phase.")
                .resultSnapshot(Map.of("action", actionType(), "terminal", false))
                .build();
    }
}
