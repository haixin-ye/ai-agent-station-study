package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityCandidateVO {

    private String capabilityCode;
    private String capabilityType;
    private String mcpServerCode;
    private String toolName;
    private String description;
    private List<String> requiredArguments;
    private Map<String, Object> inputSchema;
    private String schemaHash;
    private Boolean schemaTruncated;
    private String requiredPermission;
    private String approvalPolicy;
    private String riskLevel;
    private String availability;
    private String summary;
    private Boolean enabled;
}
