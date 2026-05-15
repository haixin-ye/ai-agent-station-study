package yhx.com.test.trigger.agent;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.service.debug.DebugAccessPolicy;
import yhx.com.domain.agent.service.debug.DebugPayloadPreviewPolicy;

public class AgentDebugApiBoundaryTest {

    @Test
    public void debug_api_disabled_by_default() {
        DebugAccessPolicy policy = new DebugAccessPolicy(false, false, false);

        Assert.assertThrows(IllegalStateException.class, policy::requireDebugApiEnabled);
    }

    @Test
    public void debug_payload_preview_omits_raw_content_when_disabled() {
        DebugPayloadPreviewPolicy policy = new DebugPayloadPreviewPolicy(4, false);

        AgentPayloadEntity result = policy.applyPreviewPolicy(AgentPayloadEntity.builder()
                .payloadId("payload-1")
                .payloadType(PayloadTypeEnumVO.TEXT)
                .content("abcdef")
                .build());

        Assert.assertEquals("abcd", result.getPreview());
        Assert.assertNull(result.getContent());
    }
}

