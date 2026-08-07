package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.AgentCapabilityResolutionCommandVO;
import yhx.com.domain.agent.model.valobj.agent.AgentCapabilityResolutionResultVO;
import yhx.com.domain.agent.model.valobj.agent.AgentProfileVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentCapabilityCodeEnumVO;

import java.util.LinkedHashSet;
import java.util.Set;

public class AgentCapabilityResolver {

    public AgentCapabilityResolutionResultVO resolve(AgentCapabilityResolutionCommandVO command) {
        AgentProfileVO profile = command == null ? null : command.getProfile();
        boolean workspaceScopePresent = command != null && Boolean.TRUE.equals(command.getWorkspaceScopePresent());

        Set<String> requested = new LinkedHashSet<>();
        if (profile != null && profile.getDefaultCapabilityCodes() != null) {
            requested.addAll(profile.getDefaultCapabilityCodes());
        }
        if (command != null && command.getRequestedCapabilityCodes() != null) {
            requested.addAll(command.getRequestedCapabilityCodes());
        }

        Set<String> effective = new LinkedHashSet<>();
        Set<String> denied = new LinkedHashSet<>();
        for (String capabilityCode : requested) {
            if (capabilityCode == null || capabilityCode.isBlank()) {
                continue;
            }
            if (profile == null || !profile.allowsCapability(capabilityCode)) {
                denied.add(capabilityCode);
                continue;
            }
            if (isFileCapability(capabilityCode) && !workspaceScopePresent) {
                denied.add(capabilityCode);
                continue;
            }
            effective.add(capabilityCode);
        }
        return AgentCapabilityResolutionResultVO.builder()
                .effectiveCapabilityCodes(effective)
                .deniedCapabilityCodes(denied)
                .build();
    }

    private boolean isFileCapability(String capabilityCode) {
        return AgentCapabilityCodeEnumVO.FILE_READ.code().equals(capabilityCode)
                || AgentCapabilityCodeEnumVO.FILE_WRITE.code().equals(capabilityCode)
                || capabilityCode != null && capabilityCode.startsWith("file_system_");
    }
}
