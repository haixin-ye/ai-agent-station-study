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

public class AskUserActionHandlerTest {

    @Test
    public void ask_user_routes_through_user_interaction_manager() {
        ActionHandlerTestSupport.FullRepository repository = new ActionHandlerTestSupport.FullRepository();
        MainActionDispatcher dispatcher = dispatcher(repository);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), askAction(true));

        Assert.assertEquals(MainActionHandlerStatusEnumVO.WAITING_USER, result.getStatus());
        Assert.assertEquals(1, repository.pendingInputs.size());
    }

    @Test
    public void ask_user_does_not_persist_pending_input_directly() {
        ActionHandlerTestSupport.FullRepository repository = new ActionHandlerTestSupport.FullRepository();
        MainActionDispatcher dispatcher = dispatcher(repository);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), askAction(true));

        Assert.assertEquals(repository.pendingInputs.keySet().iterator().next(), result.getPendingInputId());
    }

    @Test
    public void ask_user_single_choice_requires_options() {
        ActionHandlerTestSupport.FullRepository repository = new ActionHandlerTestSupport.FullRepository();
        MainActionDispatcher dispatcher = dispatcher(repository);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), askAction(false));

        Assert.assertEquals(MainActionHandlerStatusEnumVO.FAILED, result.getStatus());
    }

    private MainActionDispatcher dispatcher(ActionHandlerTestSupport.FullRepository repository) {
        return ActionHandlerTestSupport.dispatcher(repository,
                new ActionHandlerTestSupport.FakeFinalDeliveryPort(),
                new ActionHandlerTestSupport.FakeRagRuntimePort(),
                new ActionHandlerTestSupport.FakeToolActionOrchestratorPort(),
                new ActionHandlerTestSupport.FakePlanStatePort());
    }

    private MainAgentActionVO askAction(boolean withOptions) {
        Map<String, Object> request = withOptions
                ? Map.of("question", "选哪个", "inputMode", "SINGLE_CHOICE", "allowFreeText", false,
                "options", List.of(Map.of("id", "a", "label", "A", "value", Map.of("choice", "A"))))
                : Map.of("question", "选哪个", "inputMode", "SINGLE_CHOICE", "allowFreeText", false);
        return MainAgentActionVO.builder().action("ASK_USER").stateDelta(Map.of("askUserRequest", request)).build();
    }
}
