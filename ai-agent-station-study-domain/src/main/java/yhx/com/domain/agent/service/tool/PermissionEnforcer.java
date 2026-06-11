package yhx.com.domain.agent.service.tool;

import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolApprovalStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ApprovalPolicyEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionDecisionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionModeEnumVO;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
import yhx.com.domain.agent.model.valobj.tool.PermissionCheckCommandVO;
import yhx.com.domain.agent.model.valobj.tool.PermissionDecisionVO;

import java.nio.file.Path;
import java.util.Map;

public class PermissionEnforcer {

    public PermissionDecisionVO decide(PermissionCheckCommandVO command) {
        if (command == null || command.getCapability() == null || !Boolean.TRUE.equals(command.getCapability().getEnabled())) {
            return deny("TOOL_CAPABILITY_DISABLED", "Capability is missing or disabled.");
        }
        if (command.getToolSpec() == null) {
            return deny("TOOL_NOT_FOUND", "MCP tool metadata is missing.");
        }
        CapabilitySpecVO capability = command.getCapability();
        if (capability.getPermissionMode() == PermissionModeEnumVO.DENY) {
            return deny("TOOL_PERMISSION_DENIED", "Capability permission mode is DENY.");
        }
        ToolApprovalEntity approval = command.getExistingApproval();
        if (approval != null) {
            if (approval.getStatus() == ToolApprovalStatusEnumVO.APPROVED
                    && safeEquals(approval.getArgumentsHash(), command.getArgumentsHash())) {
                return allow("Matching approval exists.");
            }
            if (approval.getStatus() == ToolApprovalStatusEnumVO.REJECTED) {
                return deny("TOOL_PERMISSION_DENIED", "User rejected the approval.");
            }
        }
        String workspaceMessage = workspaceViolation(command.getWorkspaceScope(), command.getMaterializedArguments());
        if (workspaceMessage != null) {
            return deny("TOOL_PERMISSION_DENIED", workspaceMessage);
        }
        boolean destructive = Boolean.TRUE.equals(command.getDestructive()) || Boolean.TRUE.equals(capability.getDestructive());
        if (destructive) {
            return ask("TOOL_APPROVAL_REQUIRED", "Destructive tool action requires explicit approval.");
        }
        if (capability.getPermissionMode() == PermissionModeEnumVO.ASK_USER) {
            return ask("TOOL_APPROVAL_REQUIRED", "Capability requires user approval.");
        }
        if (capability.getApprovalPolicy() == ApprovalPolicyEnumVO.ASK_USER_BEFORE_EXECUTE) {
            return ask("TOOL_APPROVAL_REQUIRED", "Approval policy requires user approval before execution.");
        }
        if (capability.getApprovalPolicy() == ApprovalPolicyEnumVO.ASK_USER_ON_RISK && isHighRisk(capability.getRiskLevel())) {
            return ask("TOOL_APPROVAL_REQUIRED", "High-risk tool action requires approval.");
        }
        return allow("Permission facts allow execution.");
    }

    private String workspaceViolation(String workspaceScope, Map<String, Object> arguments) {
        if (workspaceScope == null || workspaceScope.isBlank() || arguments == null || arguments.isEmpty()) {
            return null;
        }
        Path workspace = Path.of(workspaceScope).toAbsolutePath().normalize();
        for (String key : new String[]{"path", "filePath", "targetPath", "outputPath"}) {
            Object value = arguments.get(key);
            if (value != null) {
                Path target = Path.of(String.valueOf(value)).toAbsolutePath().normalize();
                if (!target.startsWith(workspace)) {
                    return "Workspace write target is outside workspace scope.";
                }
            }
        }
        return null;
    }

    private boolean isHighRisk(String riskLevel) {
        return riskLevel != null && ("HIGH".equalsIgnoreCase(riskLevel) || "CRITICAL".equalsIgnoreCase(riskLevel));
    }

    private PermissionDecisionVO allow(String reason) {
        return PermissionDecisionVO.builder().status(PermissionDecisionStatusEnumVO.ALLOW).reason(reason).build();
    }

    private PermissionDecisionVO ask(String failureCode, String reason) {
        return PermissionDecisionVO.builder().status(PermissionDecisionStatusEnumVO.ASK_USER).failureCode(failureCode).reason(reason).build();
    }

    private PermissionDecisionVO deny(String failureCode, String reason) {
        return PermissionDecisionVO.builder().status(PermissionDecisionStatusEnumVO.DENY).failureCode(failureCode).reason(reason).build();
    }

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
