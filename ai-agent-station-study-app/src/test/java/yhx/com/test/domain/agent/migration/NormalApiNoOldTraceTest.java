package yhx.com.test.domain.agent.migration;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.api.dto.agent.AgentFinalResponseDTO;
import yhx.com.api.dto.agent.AgentRunDTO;
import yhx.com.api.dto.agent.AgentUserVisibleEventDTO;

public class NormalApiNoOldTraceTest {

    @Test
    public void normal_message_api_has_no_step_plan_or_understanding_fields() {
        Assert.assertFalse(MigrationTestSupport.hasField(AgentRunDTO.class, "stepPlan"));
        Assert.assertFalse(MigrationTestSupport.hasField(AgentRunDTO.class, "todoList"));
        Assert.assertFalse(MigrationTestSupport.hasField(AgentRunDTO.class, "understanding"));
    }

    @Test
    public void normal_sse_has_no_old_node_trace_payload() {
        Assert.assertFalse(MigrationTestSupport.hasField(AgentUserVisibleEventDTO.class, "tracePayload"));
        Assert.assertFalse(MigrationTestSupport.hasField(AgentUserVisibleEventDTO.class, "rawOutput"));
        Assert.assertFalse(MigrationTestSupport.hasField(AgentUserVisibleEventDTO.class, "rawResult"));
    }

    @Test
    public void final_response_does_not_expose_guard_or_verifier_details() {
        Assert.assertFalse(MigrationTestSupport.hasField(AgentFinalResponseDTO.class, "guardDetail"));
        Assert.assertFalse(MigrationTestSupport.hasField(AgentFinalResponseDTO.class, "verifierDetail"));
        Assert.assertFalse(MigrationTestSupport.hasField(AgentFinalResponseDTO.class, "rawResult"));
    }
}

