package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RunContextStateVO;
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
    public void canonical_run_context_prevents_unforced_context_replan() {
        RuntimePhaseEnumVO next = policy.nextLoopPhase(contextWithStateView(), continueStep("ASK_USER", RuntimePhaseEnumVO.PREPARING_CONTEXT));

        Assert.assertEquals(RuntimePhaseEnumVO.BUILDING_STATE_VIEW, next);
    }

    @Test
    public void force_context_replan_keeps_preparing_context() {
        RuntimeExecutionContext context = contextWithStateView();
        context.getRuntimeFacts().put("forceContextReplan", true);

        RuntimePhaseEnumVO next = policy.nextLoopPhase(context, continueStep("ASK_USER", RuntimePhaseEnumVO.PREPARING_CONTEXT));

        Assert.assertEquals(RuntimePhaseEnumVO.PREPARING_CONTEXT, next);
    }

    @Test
    public void delivery_readiness_can_continue_directly_to_main_node() {
        RuntimePhaseEnumVO next = policy.nextLoopPhase(contextWithStateView(), continueStep("READY_TO_DELIVER", RuntimePhaseEnumVO.CALLING_MAIN_NODE));

        Assert.assertEquals(RuntimePhaseEnumVO.CALLING_MAIN_NODE, next);
    }

    private RuntimeExecutionContext contextWithStateView() {
        return RuntimeExecutionContext.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .runContextState(RunContextStateVO.builder()
                        .mainAgentStage(MainAgentStageEnumVO.EXECUTING)
                        .build())
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
