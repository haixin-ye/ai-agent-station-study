package yhx.com.domain.agent.model.valobj.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationModeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionSpecVO;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptAssemblyCommand {

    private String runId;
    private String agentId;
    private String componentCode;
    private String contractVersion;
    private String promptVersion;
    private Object inputView;
    private Map<String, Object> metadata;
    private NodeInvocationModeEnumVO invocationMode;
    private List<NodeFunctionSpecVO> functionSpecs;
}
