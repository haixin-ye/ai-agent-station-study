package yhx.com.test.domain.agent.runtime.handler;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.FinalDeliveryStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.ActionEffectVO;
import yhx.com.domain.agent.model.valobj.runtime.RunWorkingStateVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.test.domain.agent.runtime.handler.support.ActionHandlerTestSupport;

import java.util.LinkedHashMap;
import java.util.List;
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
    public void final_action_passes_user_clarifications_to_final_delivery() {
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();
        MainActionDispatcher dispatcher = dispatcher(finalPort);
        RuntimeExecutionContext context = ActionHandlerTestSupport.context();
        context.setRuntimeFacts(new LinkedHashMap<>());
        context.getRuntimeFacts().put("userClarifications", List.of(UserClarificationVO.builder()
                .question("请问你想介绍哪个金庸角色？")
                .freeText("小龙女")
                .value("小龙女")
                .build()));

        dispatcher.dispatch(context, finalAction());

        Assert.assertEquals(1, finalPort.calls.size());
        Assert.assertEquals(1, finalPort.calls.get(0).getUserClarifications().size());
        Assert.assertEquals("小龙女", finalPort.calls.get(0).getUserClarifications().get(0).getFreeText());
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

    @Test
    public void final_action_passes_only_verified_successful_tool_refs_to_delivery() {
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();
        MainActionDispatcher dispatcher = dispatcher(finalPort);
        RuntimeExecutionContext context = ActionHandlerTestSupport.context();
        MaterializedEvidenceVO passed = MaterializedEvidenceVO.builder()
                .evidenceId("evidence-passed")
                .sourceRef("tool-call-passed")
                .metadata(Map.of("verificationStatus", "PASSED"))
                .build();
        MaterializedEvidenceVO failed = MaterializedEvidenceVO.builder()
                .evidenceId("evidence-failed")
                .sourceRef("tool-call-failed")
                .metadata(Map.of("verificationStatus", "FAILED"))
                .build();
        context.setWorkingState(RunWorkingStateVO.builder()
                .baseStateView(MainAgentStateViewVO.builder().build())
                .actionHistory(List.of(
                        ActionEffectVO.builder().action("CALL_TOOL").status("TOOL_SUCCEEDED")
                                .createdEvidence(List.of(passed)).build(),
                        ActionEffectVO.builder().action("CALL_TOOL").status("TOOL_FAILED")
                                .createdEvidence(List.of(failed)).build()))
                .evidencePack(List.of(passed, failed))
                .build());

        dispatcher.dispatch(context, finalAction());

        Assert.assertEquals(List.of("tool-call-passed"), finalPort.calls.get(0).getVerifiedToolCallRefs());
    }

    @Test
    public void final_action_recovers_verified_tool_refs_from_persisted_state_view_evidence() {
        ActionHandlerTestSupport.FakeFinalDeliveryPort finalPort = new ActionHandlerTestSupport.FakeFinalDeliveryPort();
        MainActionDispatcher dispatcher = dispatcher(finalPort);
        RuntimeExecutionContext context = ActionHandlerTestSupport.context();
        context.setLastStateView(MainAgentStateViewVO.builder()
                .evidencePack(List.of(MaterializedEvidenceVO.builder()
                        .evidenceId("evidence-restored")
                        .sourceRef("tool-call-restored")
                        .metadata(Map.of("verificationStatus", "PASSED"))
                        .build()))
                .build());

        dispatcher.dispatch(context, finalAction());

        Assert.assertEquals(List.of("tool-call-restored"), finalPort.calls.get(0).getVerifiedToolCallRefs());
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
