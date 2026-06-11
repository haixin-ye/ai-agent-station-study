package yhx.com.test.trigger.agent;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.service.api.AgentMockScenarioService;

public class AgentMockScenarioApiTest {

    @Test
    public void mock_scenarios_list_contains_required_scenarios() {
        AgentMockScenarioService service = new AgentMockScenarioService();

        Assert.assertEquals(11, service.listScenarios().size());
        Assert.assertTrue(service.listScenarios().stream()
                .anyMatch(scenario -> "ask_user_confirm".equals(scenario.getScenario())));
        Assert.assertTrue(service.listScenarios().stream()
                .anyMatch(scenario -> "debug_event_stream".equals(scenario.getScenario())));
    }

    @Test
    public void mock_simple_final_streams_final_event() {
        AgentMockScenarioService service = new AgentMockScenarioService();

        Assert.assertTrue(service.buildEvents("simple_final", "mock-run").stream()
                .anyMatch(event -> "FINAL_READY".equals(event.getEventType())));
    }

    @Test
    public void mock_ask_user_confirm_streams_pending_input_event() {
        AgentMockScenarioService service = new AgentMockScenarioService();

        Assert.assertTrue(service.buildEvents("ask_user_confirm", "mock-run").stream()
                .anyMatch(event -> "ASK_USER".equals(event.getEventType()) && event.getPendingId() != null));
    }
}

