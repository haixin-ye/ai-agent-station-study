package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentFullContextVO;

public interface GenericSubAgentNodePort {

    SubAgentActionVO invoke(SubAgentFullContextVO fullContext);
}
