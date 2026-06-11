package yhx.com.domain.agent.model.valobj.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCapabilityResolutionResultVO {

    private Set<String> effectiveCapabilityCodes;
    private Set<String> deniedCapabilityCodes;
}
