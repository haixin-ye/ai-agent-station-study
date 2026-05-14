package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.tool.RequiredPermissionEnumVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolApprovalKeyCommandVO {

    private String runId;
    private String toolCallId;
    private String capabilityCode;
    private String mcpServerCode;
    private String toolName;
    private String argumentsHash;
    private RequiredPermissionEnumVO requiredPermission;
    private String workspaceScope;
    private Boolean destructive;
}
