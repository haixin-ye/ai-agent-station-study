package yhx.com.domain.agent.model.valobj.invocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationModeEnumVO;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeClientRequest {

    private String runId;
    private String componentCode;
    private String modelCode;
    private String prompt;
    private Double temperature;
    private Integer maxOutputTokens;
    private Map<String, Object> metadata;
    private NodeInvocationModeEnumVO invocationMode;
    private List<NodeFunctionSpecVO> functionSpecs;
}
