package yhx.com.test.domain.agent.runtime.handler;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.service.agent.AgentDispatchRuntime;
import yhx.com.domain.agent.service.agent.ParentChildRunRegistry;
import yhx.com.domain.agent.service.runtime.handler.DelegateAgentsActionHandler;
import yhx.com.test.domain.agent.runtime.handler.support.ActionHandlerTestSupport;

import java.util.List;
import java.util.Map;

public class DelegateAgentsActionHandlerTest {

    @Test
    public void delegate_agents_handler_pauses_parent_for_children() {
        ParentChildRunRegistry registry = new ParentChildRunRegistry();
        DelegateAgentsActionHandler handler = new DelegateAgentsActionHandler(
                new AgentDispatchRuntime(registry),
                null,
                null);

        MainActionHandlerResult result = handler.handle(ActionHandlerTestSupport.context(), action());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.WAITING_CHILDREN, result.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.WAITING_CHILDREN, result.getNextPhase());
        Assert.assertEquals(List.of("run-001-child-t1", "run-001-child-t2"),
                result.getActionEffect().getResultSnapshot().get("childRunIds"));
        Assert.assertEquals(2, registry.listChildren("run-001").size());
    }

    private MainAgentActionVO action() {
        return MainAgentActionVO.builder()
                .action("DELEGATE_AGENTS")
                .stateDelta(Map.of("delegateAgentsRequest", Map.of(
                        "waitMode", "WAIT_ALL",
                        "tasks", List.of(
                                Map.of("taskId", "t1", "name", "reader", "objective", "Read A."),
                                Map.of("taskId", "t2", "name", "reviewer", "objective", "Review B.")
                        ))))
                .build();
    }
}
