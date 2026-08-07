package yhx.com.test.domain.agent.harness;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.agent.AgentProfileVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentProfileTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentCapabilityCodeEnumVO;
import yhx.com.domain.agent.service.agent.AgentProfileRegistry;

import java.util.Set;

public class AgentProfileRegistryTest {

    @Test
    public void main_agent_profile_keeps_existing_actions_but_does_not_expose_code_agent() {
        AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
                .requireProfile(AgentProfileTypeEnumVO.MAIN_AGENT);

        Assert.assertTrue(profile.allowsAction("FINAL"));
        Assert.assertTrue(profile.allowsAction("CALL_TOOL"));
        Assert.assertTrue(profile.allowsAction("ASK_USER"));
        Assert.assertTrue(profile.allowsAction("DELEGATE_AGENTS"));
        Assert.assertFalse(profile.allowsAction("DELEGATE_CODE_AGENT"));
    }

    @Test
    public void generic_sub_agent_profile_commits_but_cannot_final_or_delegate() {
        AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
                .requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT);

        Assert.assertTrue(profile.allowsAction("COMMIT"));
        Assert.assertTrue(profile.allowsAction("ASK_USER"));
        Assert.assertFalse(profile.allowsAction("FINAL"));
        Assert.assertFalse(profile.allowsAction("DELEGATE_AGENTS"));
    }

    @Test
    public void generic_sub_agent_limits_are_broad_and_explicit() {
        AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
                .requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT);

        Assert.assertEquals(Integer.valueOf(25), profile.getMaxLoopCount());
        Assert.assertEquals(Integer.valueOf(200000), profile.getMaxContextChars());
        Assert.assertEquals(Integer.valueOf(200000), profile.getMaxSingleToolResultChars());
    }

    @Test
    public void generic_sub_agent_defaults_to_all_registered_mcp_tools() {
        AgentProfileVO profile = AgentProfileRegistry.defaultRegistry()
                .requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT);

        Assert.assertEquals(Set.of(AgentCapabilityCodeEnumVO.MCP_TOOL.code()), profile.getDefaultCapabilityCodes());
    }
}
