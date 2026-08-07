package yhx.com.domain.agent.model.valobj.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentProfileTypeEnumVO;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentProfileVO {

    private AgentProfileTypeEnumVO profileType;
    private Set<String> allowedActionCodes;
    /** Capabilities granted when a run is created, before task-specific requests are added. */
    private Set<String> defaultCapabilityCodes;
    private Set<String> maximumCapabilityCodes;
    private Integer maxLoopCount;
    private Integer maxContextChars;
    private Integer maxSingleToolResultChars;

    public boolean allowsAction(String actionCode) {
        return actionCode != null && allowedActionCodes != null && allowedActionCodes.contains(actionCode);
    }

    public boolean allowsCapability(String capabilityCode) {
        if (capabilityCode == null || maximumCapabilityCodes == null) {
            return false;
        }
        if (maximumCapabilityCodes.contains(capabilityCode)) {
            return true;
        }
        if (capabilityCode.startsWith("file_system_")) {
            return maximumCapabilityCodes.contains("MCP_TOOL")
                    || maximumCapabilityCodes.contains("FILE_READ")
                    || maximumCapabilityCodes.contains("FILE_WRITE");
        }
        if (capabilityCode.contains("_") || capabilityCode.contains(".")) {
            return maximumCapabilityCodes.contains("MCP_TOOL");
        }
        return false;
    }
}
