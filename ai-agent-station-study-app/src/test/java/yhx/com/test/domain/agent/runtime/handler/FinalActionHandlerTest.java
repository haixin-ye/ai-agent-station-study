package yhx.com.test.domain.agent.runtime.handler;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.enums.runtime.FinalDeliveryStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.LoopRuntimeOutcomeVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RunLoopRecordVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.test.domain.agent.runtime.handler.support.ActionHandlerTestSupport;

import java.util.List;
import java.util.Map;

public class FinalActionHandlerTest {

    @Test
    public void final_action_uses_final_delivery_port_in_delivering_stage() {
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();

        MainActionHandlerResult result = dispatcher(finalPort).dispatch(deliveringContext(), finalAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.COMPLETED, result.getStatus());
        Assert.assertEquals(1, finalPort.calls.size());
    }

    @Test
    public void final_action_is_rejected_before_delivering_stage() {
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();

        MainActionHandlerResult result = dispatcher(finalPort).dispatch(ActionHandlerTestSupport.context(), finalAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.FAILED, result.getStatus());
        Assert.assertTrue(finalPort.calls.isEmpty());
    }

    @Test
    public void final_delivery_repair_result_routes_to_repairing_final() {
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();
        finalPort.status = FinalDeliveryStatusEnumVO.NEEDS_REPAIR;

        MainActionHandlerResult result = dispatcher(finalPort).dispatch(deliveringContext(), finalAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
        Assert.assertEquals(RuntimePhaseEnumVO.REPAIRING_FINAL, result.getNextPhase());
    }

    @Test
    public void final_action_projects_verified_tool_refs_from_timeline() {
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();
        RuntimeExecutionContext context = deliveringContext();
        context.getRunContextState().setLoopTimeline(List.of(RunLoopRecordVO.builder()
                .mainOutput(MainAgentActionVO.builder().action("CALL_TOOL").build())
                .runtimeOutcome(LoopRuntimeOutcomeVO.builder()
                        .evidenceRefs(List.of("tool-call-passed"))
                        .details(Map.of("effectStatus", "TOOL_SUCCEEDED"))
                        .build())
                .build()));

        dispatcher(finalPort).dispatch(context, finalAction());

        Assert.assertEquals(List.of("tool-call-passed"), finalPort.calls.get(0).getVerifiedToolCallRefs());
    }

    private RuntimeExecutionContext deliveringContext() {
        RuntimeExecutionContext context = ActionHandlerTestSupport.context();
        context.getRunContextState().setMainAgentStage(MainAgentStageEnumVO.DELIVERING);
        return context;
    }

    private MainActionDispatcher dispatcher(ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort) {
        return ActionHandlerTestSupport.dispatcher(new ActionHandlerTestSupport.FullRepository(), finalPort,
                new ActionHandlerTestSupport.FakeRagRuntimePort(),
                new ActionHandlerTestSupport.FakeToolActionOrchestratorPort());
    }

    private MainAgentActionVO finalAction() {
        return MainAgentActionVO.builder()
                .taskUpdate(Map.of("lastDecision", "deliver the completed task"))
                .action("FINAL")
                .stateDelta(Map.of("finalAnswerCandidate", Map.of("content", "done")))
                .build();
    }
}
