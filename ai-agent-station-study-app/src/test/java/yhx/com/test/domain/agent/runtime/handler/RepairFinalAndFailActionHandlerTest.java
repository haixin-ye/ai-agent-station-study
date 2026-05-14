package yhx.com.test.domain.agent.runtime.handler;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.test.domain.agent.runtime.handler.support.ActionHandlerTestSupport;

import java.util.LinkedHashMap;
import java.util.Map;

public class RepairFinalAndFailActionHandlerTest {

    @Test
    public void repair_final_only_valid_in_repairing_final() {
        MainActionDispatcher dispatcher = dispatcher(new ActionHandlerTestSupport.FakeFinalDeliveryPort());

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), repairAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.COMPLETED, result.getStatus());
    }

    @Test
    public void repair_final_uses_final_delivery_port() {
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();
        MainActionDispatcher dispatcher = dispatcher(finalPort);

        dispatcher.dispatch(repairContext(), repairAction());

        Assert.assertEquals(1, finalPort.calls.size());
    }

    @Test
    public void fail_action_routes_user_message_through_final_delivery() {
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();
        MainActionDispatcher dispatcher = dispatcher(finalPort);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), failAction("请稍后重试"));

        Assert.assertEquals(MainActionHandlerStatusEnumVO.FAILED, result.getStatus());
        Assert.assertEquals(1, finalPort.calls.size());
    }

    @Test
    public void fail_action_hides_technical_fields_from_user_text() {
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();
        MainActionDispatcher dispatcher = dispatcher(finalPort);

        dispatcher.dispatch(ActionHandlerTestSupport.context(), failAction("请稍后重试"));

        Assert.assertFalse(finalPort.calls.get(0).getFinalAnswerCandidate().getContent().contains("stackTrace"));
    }

    private MainActionDispatcher dispatcher(ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort) {
        return ActionHandlerTestSupport.dispatcher(new ActionHandlerTestSupport.FullRepository(),
                finalPort,
                new ActionHandlerTestSupport.FakeRagRuntimePort(),
                new ActionHandlerTestSupport.FakeToolActionOrchestratorPort(),
                new ActionHandlerTestSupport.FakePlanStatePort());
    }

    private RuntimeExecutionContext repairContext() {
        RuntimeExecutionContext context = ActionHandlerTestSupport.context();
        context.setCurrentPhase(RuntimePhaseEnumVO.REPAIRING_FINAL);
        context.setRuntimeFacts(new LinkedHashMap<>());
        return context;
    }

    private MainAgentActionVO repairAction() {
        return MainAgentActionVO.builder()
                .action("REPAIR_FINAL")
                .stateDelta(Map.of("finalAnswerCandidate", Map.of("content", "fixed")))
                .build();
    }

    private MainAgentActionVO failAction(String message) {
        return MainAgentActionVO.builder()
                .action("FAIL")
                .stateDelta(Map.of("failure", Map.of("failureCode", "SAFE_FAIL", "message", message, "developerMessage", "stackTrace")))
                .build();
    }
}
