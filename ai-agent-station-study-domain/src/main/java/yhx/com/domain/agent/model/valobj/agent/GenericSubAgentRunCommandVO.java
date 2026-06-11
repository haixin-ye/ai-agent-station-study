package yhx.com.domain.agent.model.valobj.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericSubAgentRunCommandVO {

    private ParentChildRunRelationVO relation;
    private DelegateAgentTaskVO task;
    private AgentProfileVO profile;
    private Set<String> effectiveCapabilityCodes;
    private Map<String, Object> initialContext;
    private String sessionId;
    private String userId;
}
