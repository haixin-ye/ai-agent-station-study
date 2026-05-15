package yhx.com.test.domain.agent.migration;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.config.AutoAgentLegacyProperties;
import yhx.com.config.AutoAgentRuntimeProperties;

public class LegacySwitchTest {

    @Test
    public void legacy_harness_disabled_by_default() {
        AutoAgentLegacyProperties properties = new AutoAgentLegacyProperties();

        Assert.assertFalse(properties.isEnabled());
    }

    @Test
    public void legacy_compare_api_requires_dev_switch() {
        AutoAgentLegacyProperties properties = new AutoAgentLegacyProperties();

        Assert.assertFalse(properties.isCompareApiEnabled());
    }

    @Test
    public void runtime_enabled_by_default() {
        AutoAgentRuntimeProperties properties = new AutoAgentRuntimeProperties();

        Assert.assertTrue(properties.isEnabled());
    }
}

