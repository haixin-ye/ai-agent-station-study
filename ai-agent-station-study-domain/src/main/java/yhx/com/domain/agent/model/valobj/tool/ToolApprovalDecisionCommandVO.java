package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolApprovalDecisionCommandVO {

    private String runId;
    private String sessionId;
    private String toolCallId;
    private String approvalKey;
    private String argumentsHash;
    private CapabilitySpecVO capability;
    private McpToolSpecVO toolSpec;
    private PermissionDecisionVO permissionDecision;
    private ToolIntentVO toolIntent;
    private RuntimeExecutionContext runtimeContext;
}
