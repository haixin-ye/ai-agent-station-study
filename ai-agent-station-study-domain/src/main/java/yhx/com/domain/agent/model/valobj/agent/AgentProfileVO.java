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
    private Set<String> maximumCapabilityCodes;
    private Integer maxLoopCount;
    private Integer maxContextChars;
    private Integer maxSingleToolResultChars;

    public boolean allowsAction(String actionCode) {
        return actionCode != null && allowedActionCodes != null && allowedActionCodes.contains(actionCode);
    }

    public boolean allowsCapability(String capabilityCode) {
        return capabilityCode != null && maximumCapabilityCodes != null && maximumCapabilityCodes.contains(capabilityCode);
    }
}
