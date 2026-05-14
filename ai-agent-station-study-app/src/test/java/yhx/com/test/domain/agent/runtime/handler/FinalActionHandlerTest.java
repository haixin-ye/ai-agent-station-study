package yhx.com.test.domain.agent.runtime.handler;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.FinalDeliveryStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.test.domain.agent.runtime.handler.support.ActionHandlerTestSupport;

import java.util.Map;

public class FinalActionHandlerTest {

    @Test
    public void final_action_uses_final_delivery_port() {
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();
        MainActionDispatcher dispatcher = dispatcher(finalPort);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), finalAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.COMPLETED, result.getStatus());
        Assert.assertEquals(1, finalPort.calls.size());
    }

    @Test
    public void final_action_does_not_append_message_directly() {
        ActionHandlerTestSupport.FullRepository repository = new ActionHandlerTestSupport.FullRepository();
        MainActionDispatcher dispatcher = ActionHandlerTestSupport.dispatcher(repository,
                new ActionHandlerTestSupport.FakeFinalDeliveryPort(),
                new ActionHandlerTestSupport.FakeRagRuntimePort(),
                new ActionHandlerTestSupport.FakeToolActionOrchestratorPort(),
                new ActionHandlerTestSupport.FakePlanStatePort());

        dispatcher.dispatch(ActionHandlerTestSupport.context(), finalAction());

        Assert.assertTrue(repository.messages.isEmpty());
    }

    @Test
    public void final_delivery_repair_result_routes_to_repairing_final() {
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();
        finalPort.status = FinalDeliveryStatusEnumVO.NEEDS_REPAIR;
        MainActionDispatcher dispatcher = dispatcher(finalPort);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), finalAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.REPAIRING_FINAL, result.getNextPhase());
    }

    private MainActionDispatcher dispatcher(ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort) {
        return ActionHandlerTestSupport.dispatcher(new ActionHandlerTestSupport.FullRepository(), finalPort,
                new ActionHandlerTestSupport.FakeRagRuntimePort(),
                new ActionHandlerTestSupport.FakeToolActionOrchestratorPort(),
                new ActionHandlerTestSupport.FakePlanStatePort());
    }

    private MainAgentActionVO finalAction() {
        return MainAgentActionVO.builder()
                .action("FINAL")
                .stateDelta(Map.of("finalAnswerCandidate", Map.of("content", "done")))
                .build();
    }
}
