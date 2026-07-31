package yhx.com.test.trigger.agent;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import yhx.com.domain.agent.model.valobj.observability.AgentObservabilitySnapshotVO;
import yhx.com.domain.agent.service.api.AgentDebugFacade;
import yhx.com.domain.agent.service.api.AgentQueryFacade;
import yhx.com.trigger.http.AgentDebugController;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AgentObservabilityStudioApiTest {

    @Test
    public void studio_endpoint_returns_structured_snapshot() {
        AgentDebugFacade debugFacade = mock(AgentDebugFacade.class);
        when(debugFacade.loadStudio("run-1")).thenReturn(AgentObservabilitySnapshotVO.builder()
                .header(Map.of("runId", "run-1"))
                .build());
        AgentDebugController controller = new AgentDebugController();
        ReflectionTestUtils.setField(controller, "agentDebugFacade", debugFacade);
        ReflectionTestUtils.setField(controller, "agentQueryFacade", mock(AgentQueryFacade.class));

        Assert.assertNotNull(controller.loadStudio("run-1"));
        Assert.assertEquals("run-1", controller.loadStudio("run-1").getData().getHeader().get("runId"));
    }
}
