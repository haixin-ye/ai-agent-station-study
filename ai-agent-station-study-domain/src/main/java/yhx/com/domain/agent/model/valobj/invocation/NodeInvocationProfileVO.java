package yhx.com.domain.agent.model.valobj.invocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.invocation.NodeInvocationModeEnumVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeInvocationProfileVO {

    private String componentCode;
    private String modelCode;
    private String promptVersion;
    private String contractVersion;
    private Double temperature;
    private Integer maxOutputTokens;
    private Integer maxRepairAttempts;
    private NodeInvocationModeEnumVO invocationMode;
    private List<NodeFunctionSpecVO> functionSpecs;
}
