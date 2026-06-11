package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.AgentProfileVO;
import yhx.com.domain.agent.model.valobj.agent.SubAgentActionVO;

import java.util.Optional;
import java.util.Set;

public class SubAgentActionPolicy {

    private final AgentActionPermissionPolicy permissionPolicy;

    public SubAgentActionPolicy() {
        this(new AgentActionPermissionPolicy());
    }

    public SubAgentActionPolicy(AgentActionPermissionPolicy permissionPolicy) {
        this.permissionPolicy = permissionPolicy == null ? new AgentActionPermissionPolicy() : permissionPolicy;
    }

    public Optional<String> validate(AgentProfileVO profile, Set<String> effectiveCapabilities, SubAgentActionVO action) {
        String actionCode = action == null ? null : action.getAction();
        return permissionPolicy.validate(profile, effectiveCapabilities, actionCode)
                .map(message -> message
                        .replace("Agent action is missing.", "Generic subagent action is missing.")
                        .replace("Agent action is not allowed by profile", "Generic subagent action is not allowed by profile")
                        .replace("Agent action ", "Generic subagent action "));
    }
}
