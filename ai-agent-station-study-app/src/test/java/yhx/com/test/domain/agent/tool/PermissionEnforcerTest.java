package yhx.com.test.domain.agent.tool;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.ToolApprovalStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ApprovalPolicyEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionDecisionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionModeEnumVO;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
import yhx.com.domain.agent.model.valobj.tool.McpToolSpecVO;
import yhx.com.domain.agent.model.valobj.tool.PermissionCheckCommandVO;
import yhx.com.domain.agent.model.valobj.tool.PermissionDecisionVO;
import yhx.com.domain.agent.service.tool.PermissionEnforcer;

import java.util.Map;

public class PermissionEnforcerTest {

    @Test
    public void external_write_requires_approval() {
        PermissionDecisionVO decision = enforcer().decide(command(capability(PermissionModeEnumVO.ASK_USER, false), Map.of()));

        Assert.assertEquals(PermissionDecisionStatusEnumVO.ASK_USER, decision.getStatus());
    }

    @Test
    public void workspace_write_outside_scope_is_denied() {
        PermissionDecisionVO decision = enforcer().decide(command(capability(PermissionModeEnumVO.ALLOW, false),
                Map.of("path", "E:\\other\\file.txt")));

        Assert.assertEquals(PermissionDecisionStatusEnumVO.DENY, decision.getStatus());
    }

    @Test
    public void destructive_action_requires_approval() {
        PermissionDecisionVO decision = enforcer().decide(command(capability(PermissionModeEnumVO.ALLOW, true), Map.of()));

        Assert.assertEquals(PermissionDecisionStatusEnumVO.ASK_USER, decision.getStatus());
    }

    @Test
    public void approved_matching_key_allows_execution() {
        PermissionCheckCommandVO command = command(capability(PermissionModeEnumVO.ALLOW, true), Map.of());
        command.setExistingApproval(ToolApprovalEntity.builder()
                .status(ToolApprovalStatusEnumVO.APPROVED)
                .argumentsHash("hash-1")
                .build());
        command.setArgumentsHash("hash-1");

        PermissionDecisionVO decision = enforcer().decide(command);

        Assert.assertEquals(PermissionDecisionStatusEnumVO.ALLOW, decision.getStatus());
    }

    private PermissionEnforcer enforcer() {
        return new PermissionEnforcer();
    }

    private PermissionCheckCommandVO command(CapabilitySpecVO capability, Map<String, Object> arguments) {
        return PermissionCheckCommandVO.builder()
                .runId("run-001")
                .toolCallId("tool-call-001")
                .capability(capability)
                .toolSpec(McpToolSpecVO.builder().mcpServerCode("server").toolName("tool").build())
                .workspaceScope("E:\\javaProject\\ai-agent-station-study")
                .materializedArguments(arguments)
                .argumentsHash("hash")
                .destructive(capability.getDestructive())
                .build();
    }

    private CapabilitySpecVO capability(PermissionModeEnumVO permissionMode, boolean destructive) {
        return CapabilitySpecVO.builder()
                .capabilityCode("cap")
                .permissionMode(permissionMode)
                .approvalPolicy(ApprovalPolicyEnumVO.NEVER)
                .destructive(destructive)
                .enabled(true)
                .build();
    }
}
