package yhx.com.test.domain.agent.harness;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.agent.AgentCapabilityResolutionCommandVO;
import yhx.com.domain.agent.model.valobj.agent.AgentCapabilityResolutionResultVO;
import yhx.com.domain.agent.model.valobj.agent.AgentProfileVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentTaskVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentsRequestVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentProfileTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentCapabilityCodeEnumVO;
import yhx.com.domain.agent.service.agent.AgentCapabilityResolver;
import yhx.com.domain.agent.service.agent.AgentProfileRegistry;
import yhx.com.domain.agent.service.agent.DelegateAgentsRequestValidator;

import java.util.List;
import java.util.Map;
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

    @Test
    public void generic_sub_agent_receives_mcp_tool_by_profile_default() {
        AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
                .requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT);

        AgentCapabilityResolutionResultVO result = new AgentCapabilityResolver().resolve(
                AgentCapabilityResolutionCommandVO.builder()
                        .profile(profile)
                        .requestedCapabilityCodes(Set.of("COMMIT"))
                        .workspaceScopePresent(false)
                        .build());

        Assert.assertTrue(result.getEffectiveCapabilityCodes().contains("COMMIT"));
        Assert.assertTrue(result.getEffectiveCapabilityCodes().contains(AgentCapabilityCodeEnumVO.MCP_TOOL.code()));
        Assert.assertTrue(result.getDeniedCapabilityCodes().isEmpty());
    }

    @Test
    public void delegated_task_may_omit_mcp_capability_when_profile_supplies_it_by_default() {
        AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
                .requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT);

        new DelegateAgentsRequestValidator().validate(
                DelegateAgentsRequestVO.builder()
                        .waitMode("WAIT_ALL")
                        .tasks(List.of(DelegateAgentTaskVO.builder()
                                .taskId("task-1")
                                .name("mcp-worker")
                                .objective("Use a configured MCP tool.")
                                .requiredOutput("Return the tool result.")
                                .requestedCapabilities(List.of(AgentCapabilityCodeEnumVO.COMMIT.code()))
                                .parentContext(Map.of())
                                .build()))
                        .build(),
                profile);
    }
}
