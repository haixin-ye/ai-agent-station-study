package yhx.com.test.domain.agent.runtime.handler;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.TaskDeliverableVO;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.test.domain.agent.runtime.handler.support.ActionHandlerTestSupport;

import java.util.List;
import java.util.Map;

public class ReadyToDeliverActionHandlerTest {

    @Test
    public void incomplete_ledger_returns_observable_loop_outcome() {
        var context = ActionHandlerTestSupport.context();
        context.getRunContextState().getTaskLedger().setDeliverables(List.of(
                TaskDeliverableVO.builder().deliverableId("report").status("IN_PROGRESS").build()));

        MainActionHandlerResult result = dispatcher().dispatch(context, readyAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals("DELIVERY_NOT_READY", result.getActionEffect().getStatus());
        Assert.assertEquals(MainAgentStageEnumVO.EXECUTING, context.getRunContextState().getMainAgentStage());
    }

    @Test
    public void complete_ledger_enters_delivery_stage() {
        var context = ActionHandlerTestSupport.context();
        context.getRunContextState().getTaskLedger().setDeliverables(List.of(
                TaskDeliverableVO.builder().deliverableId("report").status("COMPLETED").build()));

        MainActionHandlerResult result = dispatcher().dispatch(context, readyAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals(MainAgentStageEnumVO.DELIVERING, context.getRunContextState().getMainAgentStage());
    }

    private MainActionDispatcher dispatcher() {
        return ActionHandlerTestSupport.dispatcher(
                new ActionHandlerTestSupport.FullRepository(),
                new ActionHandlerTestSupport.FakeFinalDeliveryPort(),
                new ActionHandlerTestSupport.FakeRagRuntimePort(),
                new ActionHandlerTestSupport.FakeToolActionOrchestratorPort());
    }

    private MainAgentActionVO readyAction() {
        return MainAgentActionVO.builder()
                .taskUpdate(Map.of("lastDecision", "Request delivery readiness validation."))
                .action("READY_TO_DELIVER")
                .stateDelta(Map.of("deliveryRequest", Map.of("reason", "The planned work appears complete.")))
                .build();
    }
}
