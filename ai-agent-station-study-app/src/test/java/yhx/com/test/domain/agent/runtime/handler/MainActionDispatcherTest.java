package yhx.com.test.domain.agent.runtime.handler;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.service.contract.ContractValidator;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.handler.DefaultMainActionDispatcher;
import yhx.com.domain.agent.service.runtime.handler.MainActionHandlerRegistry;
import yhx.com.test.domain.agent.runtime.handler.support.ActionHandlerTestSupport;

import java.util.List;
import java.util.Map;

public class MainActionDispatcherTest {

    @Test
    public void dispatcher_rejects_missing_action() {
        MainActionDispatcher dispatcher = dispatcher();

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), MainAgentActionVO.builder().stateDelta(Map.of()).build());

        Assert.assertNotNull(result.getSafeFailure());
    }

    @Test
    public void dispatcher_rejects_state_delta_scope_violation() {
        MainActionDispatcher dispatcher = dispatcher();

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), MainAgentActionVO.builder()
                .action("FINAL")
                .stateDelta(Map.of("toolIntent", Map.of("capabilityCode", "x")))
                .build());

        Assert.assertNotNull(result.getSafeFailure());
    }

    @Test
    public void dispatcher_routes_every_action_to_one_handler() {
        MainActionHandlerRegistry registry = ActionHandlerTestSupport.registry(new ActionHandlerTestSupport.FullRepository(),
                new ActionHandlerTestSupport.FakeFinalDeliveryPort(),
                new ActionHandlerTestSupport.FakeRagRuntimePort(),
                new ActionHandlerTestSupport.FakeToolActionOrchestratorPort());
        for (MainAgentActionTypeEnumVO actionType : MainAgentActionTypeEnumVO.values()) {
            Assert.assertTrue(registry.supports(actionType));
        }
        Assert.assertTrue(registry.hasExactlyOneHandlerForEveryAction());
    }

    @Test
    public void missing_handler_returns_safe_failure() {
        MainActionDispatcher dispatcher = new DefaultMainActionDispatcher(new MainActionHandlerRegistry(List.of()),
                ContractValidator.defaultValidator(), new RuntimeFailureFactory(), null);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), MainAgentActionVO.builder()
                .action("FINAL")
                .stateDelta(Map.of("finalAnswerCandidate", Map.of("content", "done")))
                .build());

        Assert.assertNotNull(result.getSafeFailure());
    }

    @Test
    public void rejected_stage_action_does_not_mutate_task_ledger() {
        var context = ActionHandlerTestSupport.context();
        context.getRunContextState().setMainAgentStage(MainAgentStageEnumVO.DELIVERING);
        MainActionDispatcher dispatcher = dispatcher();

        MainActionHandlerResult result = dispatcher.dispatch(context, MainAgentActionVO.builder()
                .taskUpdate(Map.of("goal", "must not be committed"))
                .action("CALL_TOOL")
                .stateDelta(Map.of("toolIntent", Map.of(
                        "capabilityCode", "file_system_search_files",
                        "toolName", "search_files",
                        "goal", "search",
                        "arguments", Map.of("pattern", "*", "path", "/"))))
                .build());

        Assert.assertNotNull(result.getSafeFailure());
        Assert.assertNull(context.getRunContextState().getTaskLedger().getGoal());
        Assert.assertEquals(MainAgentStageEnumVO.DELIVERING, context.getRunContextState().getMainAgentStage());
    }

    @Test
    public void accepted_action_commits_task_update_before_handler() {
        var context = ActionHandlerTestSupport.context();
        MainActionDispatcher dispatcher = dispatcher();

        MainActionHandlerResult result = dispatcher.dispatch(context, MainAgentActionVO.builder()
                .taskUpdate(Map.of("goal", "persisted goal"))
                .action("READY_TO_DELIVER")
                .stateDelta(Map.of("deliveryRequest", Map.of("reason", "ready")))
                .build());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals("persisted goal", context.getRunContextState().getTaskLedger().getGoal());
    }

    private MainActionDispatcher dispatcher() {
        return ActionHandlerTestSupport.dispatcher(new ActionHandlerTestSupport.FullRepository(),
                new ActionHandlerTestSupport.FakeFinalDeliveryPort(),
                new ActionHandlerTestSupport.FakeRagRuntimePort(),
                new ActionHandlerTestSupport.FakeToolActionOrchestratorPort());
    }
}
