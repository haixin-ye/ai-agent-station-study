package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionExecutionContextVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionHandlerResultVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;

public interface SubAgentActionHandler {

    String actionType();

    default SubAgentActionHandlerResultVO handle(ParentChildRunRelationVO relation, SubAgentActionVO action) {
        return handle(SubAgentActionExecutionContextVO.builder()
                .relation(relation)
                .build(), action);
    }

    SubAgentActionHandlerResultVO handle(SubAgentActionExecutionContextVO context, SubAgentActionVO action);
}
