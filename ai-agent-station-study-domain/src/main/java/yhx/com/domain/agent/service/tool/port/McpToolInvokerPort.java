package yhx.com.domain.agent.service.tool.port;

import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeCommandVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolInvokeResultVO;

public interface McpToolInvokerPort {

    McpToolInvokeResultVO invoke(McpToolInvokeCommandVO command);
}
