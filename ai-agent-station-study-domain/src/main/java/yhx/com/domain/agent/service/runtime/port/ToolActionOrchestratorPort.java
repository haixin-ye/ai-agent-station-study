package yhx.com.domain.agent.service.runtime.port;

import yhx.com.domain.agent.model.valobj.runtime.ToolActionCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.ToolActionResultVO;

public interface ToolActionOrchestratorPort {

    ToolActionResultVO handleToolAction(ToolActionCommandVO command);
}
