package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckCommandVO {

    private String runId;
    private String toolCallId;
    private CapabilitySpecVO capability;
    private McpToolSpecVO toolSpec;
    private Map<String, Object> materializedArguments;
    private String argumentsHash;
    private String workspaceScope;
    private Boolean destructive;
    private ToolApprovalEntity existingApproval;
}
