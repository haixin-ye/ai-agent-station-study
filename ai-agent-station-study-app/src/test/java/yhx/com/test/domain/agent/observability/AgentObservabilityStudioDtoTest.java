package yhx.com.test.domain.agent.observability;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.api.dto.agent.AgentObservabilityLoopDTO;
import yhx.com.api.dto.agent.AgentObservabilityStudioDTO;

import java.util.List;
import java.util.Map;

public class AgentObservabilityStudioDtoTest {

    @Test
    public void studio_contract_keeps_identity_in_header_and_context_in_sections() {
        AgentObservabilityStudioDTO studio = AgentObservabilityStudioDTO.builder()
                .header(Map.of("runId", "run-1", "sessionId", "session-1"))
                .context(Map.of("stateView", Map.of("taskLedger", Map.of("version", 2))))
                .loops(List.of(AgentObservabilityLoopDTO.builder()
                        .loopIndex(1)
                        .action("CALL_TOOL")
                        .stateView(Map.of("sources", List.of(Map.of("id", "memory-1"))))
                        .build()))
                .build();

        Assert.assertEquals("run-1", studio.getHeader().get("runId"));
        Assert.assertEquals("CALL_TOOL", studio.getLoops().get(0).getAction());
        Assert.assertEquals("memory-1", ((Map<?, ?>) ((List<?>) studio.getLoops().get(0)
                .getStateView().get("sources")).get(0)).get("id"));
    }
}
