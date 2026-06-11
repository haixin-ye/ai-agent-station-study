package yhx.com.test.domain.agent.runtime.handler;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.ContinuationCheckpointVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
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

    @Test
    public void ask_user_rejects_vague_category_and_free_text_pseudo_options() {
        ActionHandlerTestSupport.FullRepository repository = new ActionHandlerTestSupport.FullRepository();
        MainActionDispatcher dispatcher = dispatcher(repository);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), vagueOptionAskAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.FAILED, result.getStatus());
        Assert.assertTrue(repository.pendingInputs.isEmpty());
    }

    @Test
    public void ask_user_converts_choice_or_free_text_with_only_pseudo_options_to_free_text() {
        ActionHandlerTestSupport.FullRepository repository = new ActionHandlerTestSupport.FullRepository();
        MainActionDispatcher dispatcher = dispatcher(repository);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), vagueChoiceOrFreeTextAskAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.WAITING_USER, result.getStatus());
        Assert.assertEquals("FREE_TEXT", repository.pendingInputs.values().iterator().next().getInputMode());
    }

    @Test
    public void ask_user_checkpoint_preserves_selected_context_for_resume() {
        ActionHandlerTestSupport.FullRepository repository = new ActionHandlerTestSupport.FullRepository();
        MainActionDispatcher dispatcher = dispatcher(repository);
        RuntimeExecutionContext context = ActionHandlerTestSupport.context();
        context.setLastContextSelections(List.of(ContextSelectionVO.builder()
                .sourceType("MEMORY")
                .sourceId("memory-1")
                .build()));

        dispatcher.dispatch(context, askAction(true));

        AgentPendingInputEntity pending = repository.pendingInputs.values().iterator().next();
        ContinuationCheckpointVO checkpoint = JSON.parseObject(repository.payloads.get(pending.getContinuationRef()).getContent(),
                ContinuationCheckpointVO.class);
        Assert.assertNotNull(checkpoint.getPayload());
        Assert.assertTrue(JSON.toJSONString(checkpoint.getPayload().get("contextSelections")).contains("memory-1"));
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

    private MainAgentActionVO vagueOptionAskAction() {
        Map<String, Object> request = Map.of(
                "question", "请问你的家乡是哪里？",
                "inputMode", "SINGLE_CHOICE",
                "allowFreeText", false,
                "options", List.of(
                        Map.of("optionId", "popular-city", "label", "热门城市（如北京、西安、成都等）", "value", Map.of("type", "city-category")),
                        Map.of("optionId", "free-text", "label", "自由输入", "value", Map.of("type", "free-text"))
                ));
        return MainAgentActionVO.builder().action("ASK_USER").stateDelta(Map.of("askUserRequest", request)).build();
    }

    private MainAgentActionVO vagueChoiceOrFreeTextAskAction() {
        Map<String, Object> request = Map.of(
                "question", "请问你的家乡是哪里？",
                "inputMode", "SINGLE_CHOICE_OR_FREE_TEXT",
                "allowFreeText", true,
                "options", List.of(
                        Map.of("optionId", "popular-city", "label", "热门城市（如北京、西安、成都等）", "value", Map.of("type", "city-category")),
                        Map.of("optionId", "free-text", "label", "自由输入", "value", Map.of("type", "free-text"))
                ));
        return MainAgentActionVO.builder().action("ASK_USER").stateDelta(Map.of("askUserRequest", request)).build();
    }
}
