package yhx.com.test.domain.agent.tool;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.tool.PermissionModeEnumVO;
import yhx.com.domain.agent.model.valobj.tool.CapabilitySpecVO;
import yhx.com.domain.agent.service.tool.CapabilityRegistry;

import java.util.List;

public class CapabilityRegistryTest {

    @Test
    public void missing_capability_fails_closed() {
        CapabilityRegistry registry = new CapabilityRegistry(List.of());

        Assert.assertTrue(registry.findCapability("missing").isEmpty());
    }

    @Test
    public void disabled_capability_fails_closed() {
        CapabilityRegistry registry = new CapabilityRegistry(List.of(CapabilitySpecVO.builder()
                .capabilityCode("publish")
                .enabled(false)
                .build()));

        Assert.assertTrue(registry.findCapability("publish").isEmpty());
    }

    @Test
    public void enabled_capability_resolves_mcp_tool() {
        CapabilityRegistry registry = new CapabilityRegistry(List.of(CapabilitySpecVO.builder()
                .capabilityCode("publish")
                .mcpServerCode("csdn")
                .toolName("post_article")
                .permissionMode(PermissionModeEnumVO.ALLOW)
                .enabled(true)
                .build()));

        Assert.assertEquals("post_article", registry.requireCapability("publish").getToolName());
    }
}
