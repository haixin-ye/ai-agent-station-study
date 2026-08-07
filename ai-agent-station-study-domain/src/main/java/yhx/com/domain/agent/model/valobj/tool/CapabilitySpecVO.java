package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.tool.ApprovalPolicyEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.RequiredPermissionEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolArgumentContentModeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolResultContentModeEnumVO;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapabilitySpecVO {

    private String capabilityCode;
    private String capabilityType;
    private String mcpServerCode;
    private String toolName;
    private RequiredPermissionEnumVO requiredPermission;
    private PermissionModeEnumVO permissionMode;
    private ApprovalPolicyEnumVO approvalPolicy;
    private String riskLevel;
    private Boolean destructive;
    private ToolArgumentContentModeEnumVO defaultContentMode;
    private ToolResultContentModeEnumVO resultContentMode;
    private Boolean enabled;
    private String workspaceScope;
    private Long timeoutMs;
    private Map<String, Object> argumentDefaults;
}
