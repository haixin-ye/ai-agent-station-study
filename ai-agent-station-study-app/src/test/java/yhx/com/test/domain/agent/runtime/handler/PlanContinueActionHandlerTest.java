package yhx.com.test.domain.agent.runtime.handler;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.test.domain.agent.runtime.handler.support.ActionHandlerTestSupport;

import java.util.List;
import java.util.Map;

public class PlanContinueActionHandlerTest {

    @Test
    public void plan_is_saved_as_internal_state() {
        ActionHandlerTestSupport.FakePlanStatePort planPort = new ActionHandlerTestSupport.FakePlanStatePort();
        MainActionDispatcher dispatcher = dispatcher(planPort);

        dispatcher.dispatch(ActionHandlerTestSupport.context(), planAction());

        Assert.assertTrue(planPort.plans.containsKey("run-001"));
    }

    @Test
    public void plan_is_not_final_answer() {
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();
        MainActionDispatcher dispatcher = ActionHandlerTestSupport.dispatcher(new ActionHandlerTestSupport.FullRepository(),
                finalPort,
                new ActionHandlerTestSupport.FakeRagRuntimePort(),
                new ActionHandlerTestSupport.FakeToolActionOrchestratorPort(),
                new ActionHandlerTestSupport.FakePlanStatePort());

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), planAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertTrue(finalPort.calls.isEmpty());
    }

    @Test
    public void plan_with_per_update_only_continues_without_legacy_plan_draft() {
        ActionHandlerTestSupport.FakePlanStatePort planPort = new ActionHandlerTestSupport.FakePlanStatePort();
        MainActionDispatcher dispatcher = dispatcher(planPort);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(),
                MainAgentActionVO.builder()
                        .action("PLAN")
                        .perUpdate(Map.of(
                                "mode", "PER",
                                "goal", "inspect project",
                                "lastDecision", "Record the first plan."
                        ))
                        .stateDelta(Map.of())
                        .build());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertTrue(planPort.plans.isEmpty());
    }

    @Test
    public void continue_requires_loop_budget() {
        MainActionDispatcher dispatcher = dispatcher(new ActionHandlerTestSupport.FakePlanStatePort());

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), continueAction(true));

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
    }

    @Test
    public void continue_without_state_change_fails_safely() {
        MainActionDispatcher dispatcher = dispatcher(new ActionHandlerTestSupport.FakePlanStatePort());

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), continueAction(false));

        Assert.assertEquals(MainActionHandlerStatusEnumVO.FAILED, result.getStatus());
    }

    private MainActionDispatcher dispatcher(ActionHandlerTestSupport.FakePlanStatePort planPort) {
        return ActionHandlerTestSupport.dispatcher(new ActionHandlerTestSupport.FullRepository(),
                new ActionHandlerTestSupport.FakeFinalDeliveryPort(),
                new ActionHandlerTestSupport.FakeRagRuntimePort(),
                new ActionHandlerTestSupport.FakeToolActionOrchestratorPort(),
                planPort);
    }

    private MainAgentActionVO planAction() {
        return MainAgentActionVO.builder()
                .action("PLAN")
                .stateDelta(Map.of("planDraft", Map.of("goal", "finish task",
                        "steps", List.of(Map.of("stepId", "s1", "title", "do it", "status", "PENDING")))))
                .build();
    }

    private MainAgentActionVO continueAction(boolean withHint) {
        return MainAgentActionVO.builder()
                .action("CONTINUE")
                .stateDelta(withHint ? Map.of("nextActionHint", "continue with context") : Map.of())
                .build();
    }
}
