package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.service.runtime.RuntimeRoutePolicy;

import java.util.HashMap;
import java.util.Map;

public class RuntimeRoutePolicyTest {

    private final RuntimeRoutePolicy policy = new RuntimeRoutePolicy();

    @Test
    public void rag_action_returns_to_state_view_before_main_node() {
        RuntimePhaseEnumVO next = policy.nextLoopPhase(contextWithStateView(), continueStep("RETRIEVE_RAG", RuntimePhaseEnumVO.CALLING_MAIN_NODE));

        Assert.assertEquals(RuntimePhaseEnumVO.BUILDING_STATE_VIEW, next);
    }

    @Test
    public void tool_action_returns_to_state_view_before_main_node() {
        RuntimePhaseEnumVO next = policy.nextLoopPhase(contextWithStateView(), continueStep("CALL_TOOL", RuntimePhaseEnumVO.CALLING_MAIN_NODE));

        Assert.assertEquals(RuntimePhaseEnumVO.BUILDING_STATE_VIEW, next);
    }

    @Test
    public void artifact_action_returns_to_state_view_before_main_node() {
        RuntimePhaseEnumVO next = policy.nextLoopPhase(contextWithStateView(), continueStep("CREATE_ARTIFACT", RuntimePhaseEnumVO.CALLING_MAIN_NODE));

        Assert.assertEquals(RuntimePhaseEnumVO.BUILDING_STATE_VIEW, next);
    }

    @Test
    public void existing_state_view_prevents_unforced_context_replan() {
        RuntimePhaseEnumVO next = policy.nextLoopPhase(contextWithStateView(), continueStep("CONTINUE", RuntimePhaseEnumVO.PREPARING_CONTEXT));

        Assert.assertEquals(RuntimePhaseEnumVO.BUILDING_STATE_VIEW, next);
    }

    @Test
    public void force_context_replan_keeps_preparing_context() {
        RuntimeExecutionContext context = contextWithStateView();
        context.getRuntimeFacts().put("forceContextReplan", true);

        RuntimePhaseEnumVO next = policy.nextLoopPhase(context, continueStep("CONTINUE", RuntimePhaseEnumVO.PREPARING_CONTEXT));

        Assert.assertEquals(RuntimePhaseEnumVO.PREPARING_CONTEXT, next);
    }

    @Test
    public void plan_can_continue_directly_to_main_node() {
        RuntimePhaseEnumVO next = policy.nextLoopPhase(contextWithStateView(), continueStep("PLAN", RuntimePhaseEnumVO.CALLING_MAIN_NODE));

        Assert.assertEquals(RuntimePhaseEnumVO.CALLING_MAIN_NODE, next);
    }

    private RuntimeExecutionContext contextWithStateView() {
        return RuntimeExecutionContext.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .lastStateView(MainAgentStateViewVO.builder().build())
                .runtimeFacts(new HashMap<>())
                .build();
    }

    private RuntimeStepResult continueStep(String action, RuntimePhaseEnumVO requestedPhase) {
        return RuntimeStepResult.builder()
                .status(RuntimeStepStatusEnumVO.CONTINUE)
                .nextPhase(requestedPhase)
                .action(MainAgentActionVO.builder()
                        .action(action)
                        .stateDelta(Map.of())
                        .build())
                .build();
    }
}
