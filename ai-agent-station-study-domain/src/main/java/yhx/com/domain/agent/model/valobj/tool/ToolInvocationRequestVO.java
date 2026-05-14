package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocationRequestVO {

    private String runId;
    private String sessionId;
    private Integer loopIndex;
    private String toolCallId;
    private String toolInvocationId;
    private ToolIntentVO toolIntent;
    private CapabilitySpecVO capabilitySpec;
    private McpToolSpecVO mcpTool;
    private Map<String, Object> arguments;
    private String argumentsRef;
    private String argumentsHash;
    private String approvalId;
    private Boolean approvalRequired;
    private Boolean mustCallRealTool;
    private Long timeoutMs;
}
