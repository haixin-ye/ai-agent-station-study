package yhx.com.test.domain.agent.harness;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.agent.AgentCapabilityResolutionCommandVO;
import yhx.com.domain.agent.model.valobj.agent.AgentCapabilityResolutionResultVO;
import yhx.com.domain.agent.model.valobj.agent.AgentProfileVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentProfileTypeEnumVO;
import yhx.com.domain.agent.service.agent.AgentCapabilityResolver;
import yhx.com.domain.agent.service.agent.AgentProfileRegistry;

import java.util.Set;

public class AgentCapabilityResolverTest {

    @Test
    public void generic_sub_agent_drops_file_capabilities_without_workspace_scope() {
        AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
                .requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT);

        AgentCapabilityResolutionResultVO result = new AgentCapabilityResolver().resolve(
                AgentCapabilityResolutionCommandVO.builder()
                        .profile(profile)
                        .requestedCapabilityCodes(Set.of("RAG", "FILE_READ", "FILE_WRITE"))
                        .workspaceScopePresent(false)
                        .build());

        Assert.assertTrue(result.getEffectiveCapabilityCodes().contains("RAG"));
        Assert.assertFalse(result.getEffectiveCapabilityCodes().contains("FILE_READ"));
        Assert.assertFalse(result.getEffectiveCapabilityCodes().contains("FILE_WRITE"));
        Assert.assertTrue(result.getDeniedCapabilityCodes().contains("FILE_READ"));
        Assert.assertTrue(result.getDeniedCapabilityCodes().contains("FILE_WRITE"));
    }

    @Test
    public void generic_sub_agent_keeps_file_capabilities_with_workspace_scope() {
        AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
                .requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT);

        AgentCapabilityResolutionResultVO result = new AgentCapabilityResolver().resolve(
                AgentCapabilityResolutionCommandVO.builder()
                        .profile(profile)
                        .requestedCapabilityCodes(Set.of("FILE_READ"))
                        .workspaceScopePresent(true)
                        .build());

        Assert.assertTrue(result.getEffectiveCapabilityCodes().contains("FILE_READ"));
    }
}
