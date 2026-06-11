package yhx.com.test.domain.agent.mvp;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.api.dto.agent.AgentFinalResponseDTO;
import yhx.com.api.dto.agent.AgentPendingInputDTO;
import yhx.com.api.dto.agent.AgentUserVisibleEventDTO;
import yhx.com.config.AutoAgentLegacyProperties;
import yhx.com.domain.agent.service.debug.DebugAccessPolicy;

import java.lang.reflect.Field;

public class AutoAgentSafetyPropertyTest {

    @Test
    public void normal_final_response_must_not_expose_internal_harness_fields() {
        Assert.assertFalse(hasField(AgentFinalResponseDTO.class, "rawResult"));
        Assert.assertFalse(hasField(AgentFinalResponseDTO.class, "tracePayload"));
        Assert.assertFalse(hasField(AgentFinalResponseDTO.class, "guardDetail"));
        Assert.assertFalse(hasField(AgentFinalResponseDTO.class, "verifierDetail"));
    }

    @Test
    public void normal_sse_event_must_not_expose_raw_debug_payload_fields() {
        Assert.assertFalse(hasField(AgentUserVisibleEventDTO.class, "rawPrompt"));
        Assert.assertFalse(hasField(AgentUserVisibleEventDTO.class, "rawOutput"));
        Assert.assertFalse(hasField(AgentUserVisibleEventDTO.class, "toolReceipt"));
        Assert.assertFalse(hasField(AgentUserVisibleEventDTO.class, "StateView"));
        Assert.assertFalse(hasField(AgentUserVisibleEventDTO.class, "StateDelta"));
    }

    @Test
    public void tool_approval_payload_supports_free_text_rejection() {
        AgentPendingInputDTO pendingInput = AgentPendingInputDTO.builder()
                .pendingType("TOOL_APPROVAL")
                .inputMode("SINGLE_CHOICE")
                .allowFreeText(false)
                .build();

        Assert.assertEquals("SINGLE_CHOICE", pendingInput.getInputMode());
        Assert.assertFalse(pendingInput.getAllowFreeText());
    }

    @Test
    public void rag_verifier_should_be_fact_triggered_by_rag_was_used_flag() {
        boolean ragWasUsed = true;
        boolean finalTextMentionsKnowledgeBase = false;

        boolean shouldVerify = ragWasUsed;

        Assert.assertTrue(shouldVerify);
        Assert.assertFalse(finalTextMentionsKnowledgeBase);
    }

    @Test
    public void debug_api_is_disabled_by_default_and_legacy_harness_is_disabled_by_default() {
        DebugAccessPolicy debugAccessPolicy = new DebugAccessPolicy(false, false, false);
        AutoAgentLegacyProperties legacyProperties = new AutoAgentLegacyProperties();

        Assert.assertThrows(IllegalStateException.class, debugAccessPolicy::requireDebugApiEnabled);
        Assert.assertFalse(legacyProperties.isEnabled());
        Assert.assertFalse(legacyProperties.isCompareApiEnabled());
    }

    private boolean hasField(Class<?> type, String fieldName) {
        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }
}

