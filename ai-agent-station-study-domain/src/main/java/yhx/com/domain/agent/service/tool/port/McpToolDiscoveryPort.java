package yhx.com.domain.agent.service.tool.port;

import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;

import java.util.List;

public interface McpToolDiscoveryPort {

    List<McpToolSpecVO> discover(String mcpServerCode);
}
