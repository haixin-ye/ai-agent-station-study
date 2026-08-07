package yhx.com.test.domain.agent.runtime.handler;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RagRuntimeStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.LoopRuntimeOutcomeVO;
import yhx.com.domain.agent.model.valobj.runtime.RunLoopRecordVO;
import yhx.com.domain.agent.service.runtime.MainActionDispatcher;
import yhx.com.test.domain.agent.runtime.handler.support.ActionHandlerTestSupport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class RetrieveRagActionHandlerTest {

    @Test
    public void retrieve_rag_sets_rag_was_used_before_port_call() {
        ActionHandlerTestSupport.FullRepository repository = repositoryWithRun();
        ActionHandlerTestSupport.FakeRagRuntimePort ragPort = new ActionHandlerTestSupport.FakeRagRuntimePort();
        MainActionDispatcher dispatcher = dispatcher(repository, ragPort);

        dispatcher.dispatch(ActionHandlerTestSupport.context(), ragAction());

        Assert.assertTrue(repository.runs.get("run-001").getRagWasUsed());
        Assert.assertEquals(1, ragPort.calls.size());
    }

    @Test
    public void retrieve_rag_success_continues_loop() {
        ActionHandlerTestSupport.FullRepository repository = repositoryWithRun();
        MainActionDispatcher dispatcher = dispatcher(repository, new ActionHandlerTestSupport.FakeRagRuntimePort());

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), ragAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
    }

    @Test
    public void retrieve_rag_no_hit_can_continue_with_evidence_or_recovery() {
        ActionHandlerTestSupport.FullRepository repository = repositoryWithRun();
        ActionHandlerTestSupport.FakeRagRuntimePort ragPort = new ActionHandlerTestSupport.FakeRagRuntimePort();
        ragPort.status = RagRuntimeStatusEnumVO.NO_HIT;
        MainActionDispatcher dispatcher = dispatcher(repository, ragPort);

        MainActionHandlerResult result = dispatcher.dispatch(ActionHandlerTestSupport.context(), ragAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.CONTINUE_LOOP, result.getStatus());
    }

    @Test
    public void repeated_same_rag_no_hit_query_asks_user_without_calling_rag_again() {
        ActionHandlerTestSupport.FullRepository repository = repositoryWithRun();
        ActionHandlerTestSupport.FakeRagRuntimePort ragPort = new ActionHandlerTestSupport.FakeRagRuntimePort();
        MainActionDispatcher dispatcher = dispatcher(repository, ragPort);
        yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext context = ActionHandlerTestSupport.context();
        context.getRunContextState().setLoopTimeline(List.of(RunLoopRecordVO.builder()
                .mainOutput(yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO.builder()
                        .action("RETRIEVE_RAG")
                        .stateDelta(Map.of("ragRequest", Map.of("query", "RAG 是什么")))
                        .build())
                .runtimeOutcome(LoopRuntimeOutcomeVO.builder()
                        .details(Map.of("effectStatus", "NO_HIT"))
                        .build())
                .build()));

        MainActionHandlerResult result = dispatcher.dispatch(context, ragAction());

        Assert.assertEquals(MainActionHandlerStatusEnumVO.WAITING_USER, result.getStatus());
        Assert.assertNotNull(result.getAskUserRequest());
        Assert.assertEquals(0, ragPort.calls.size());
    }

    private MainActionDispatcher dispatcher(ActionHandlerTestSupport.FullRepository repository, ActionHandlerTestSupport.FakeRagRuntimePort ragPort) {
        return ActionHandlerTestSupport.dispatcher(repository,
                new ActionHandlerTestSupport.FakeFinalDeliveryPort(),
                ragPort,
                new ActionHandlerTestSupport.FakeToolActionOrchestratorPort());
    }

    private ActionHandlerTestSupport.FullRepository repositoryWithRun() {
        ActionHandlerTestSupport.FullRepository repository = new ActionHandlerTestSupport.FullRepository();
        repository.createRun(AgentRunEntity.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .userId("user-001")
                .agentId("agent-001")
                .status(RunStatusEnumVO.RUNNING)
                .phase(RuntimePhaseEnumVO.HANDLING_ACTION)
                .ragWasUsed(false)
                .createdAt(LocalDateTime.now())
                .build());
        return repository;
    }

    private MainAgentActionVO ragAction() {
        return MainAgentActionVO.builder()
                .taskUpdate(Map.of("lastDecision", "Retrieve private knowledge before answering."))
                .action("RETRIEVE_RAG")
                .stateDelta(Map.of("ragRequest", Map.of("query", "RAG 是什么")))
                .build();
    }
}
