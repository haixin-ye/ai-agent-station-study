package yhx.com.test.domain.agent.observability;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.*;
import yhx.com.domain.agent.model.entity.persistence.*;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.TraceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.observability.AgentObservabilitySnapshotVO;
import yhx.com.domain.agent.service.api.AgentDebugFacade;
import yhx.com.domain.agent.service.debug.DebugAccessPolicy;
import yhx.com.domain.agent.service.debug.DebugPayloadPreviewPolicy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AgentDebugFacadeStudioTest {

    @Test
    public void studio_groups_full_main_node_state_and_attempts_by_loop() {
        IEventTraceRepository traces = mock(IEventTraceRepository.class);
        IEvidenceRepository evidence = mock(IEvidenceRepository.class);
        IToolRepository tools = mock(IToolRepository.class);
        IPayloadRepository payloads = mock(IPayloadRepository.class);
        IRunRepository runs = mock(IRunRepository.class);
        IRunContextRepository contexts = mock(IRunContextRepository.class);
        DebugAccessPolicy access = mock(DebugAccessPolicy.class);

        when(runs.findRun("run-1")).thenReturn(Optional.of(AgentRunEntity.builder()
                .runId("run-1").sessionId("session-1").userId("user-1").agentId("agent-1")
                .status(RunStatusEnumVO.RUNNING).phase(RuntimePhaseEnumVO.CALLING_MAIN_NODE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build()));
        when(contexts.findContext("run-1")).thenReturn(Optional.of(AgentRunContextEntity.builder()
                .runId("run-1").schemaVersion(2).contextVersion(4L).mainAgentStage(MainAgentStageEnumVO.EXECUTING)
                .baseContextRef("base").taskLedgerRef("ledger").runtimeControlRef("control").build()));
        when(contexts.listLoops("run-1")).thenReturn(List.of(AgentRunLoopEntity.builder()
                .runId("run-1").loopIndex(2).status("COMPLETED").recordRef("loop-2").recordVersion(1L).build()));
        when(traces.listDebugTrace("run-1", 500)).thenReturn(List.of(
                AgentRunTraceEntity.builder().traceId("t1").runId("run-1").seq(11L)
                        .traceType(TraceTypeEnumVO.NODE_INPUT).payloadRef("node-in").build(),
                AgentRunTraceEntity.builder().traceId("t2").runId("run-1").seq(12L)
                        .traceType(TraceTypeEnumVO.NODE_OUTPUT).payloadRef("node-out").build()));
        when(evidence.listRunEvidence("run-1")).thenReturn(List.of());
        when(tools.listRunToolCalls("run-1", 100)).thenReturn(List.of());

        stubPayload(payloads, "base", "{\"runId\":\"run-1\",\"sessionId\":\"session-1\"}");
        stubPayload(payloads, "ledger", "{\"version\":4,\"goal\":\"debug\"}");
        stubPayload(payloads, "control", "{\"currentLoop\":2}");
        stubPayload(payloads, "loop-2", "{\"runId\":\"run-1\",\"loopIndex\":2,\"status\":\"COMPLETED\",\"mainOutput\":{\"action\":\"CALL_TOOL\",\"stateDelta\":{\"toolRequest\":{\"toolName\":\"search\"}}}}");
        stubPayload(payloads, "node-in", "{\"event\":\"node_input_full\",\"code\":\"MAIN_AGENT\",\"loopIndex\":2,\"attemptNo\":1,\"prompt\":\"FULL PROMPT\",\"inputView\":{\"memoryPack\":[{\"memoryId\":\"m-1\"}],\"ragPack\":[{\"source\":\"doc-1\"}]}}");
        stubPayload(payloads, "node-out", "{\"event\":\"node_output_full\",\"code\":\"MAIN_AGENT\",\"loopIndex\":2,\"attemptNo\":1,\"success\":true,\"rawOutput\":\"{\\\"action\\\":\\\"CALL_TOOL\\\"}\"}");

        AgentDebugFacade facade = new AgentDebugFacade(traces, evidence, tools, payloads, access,
                new DebugPayloadPreviewPolicy(100_000, true), runs, contexts);

        AgentObservabilitySnapshotVO studio = facade.loadStudio("run-1");

        Assert.assertEquals("run-1", studio.getHeader().get("runId"));
        Assert.assertEquals(1, studio.getLoops().size());
        Assert.assertEquals("CALL_TOOL", studio.getLoops().get(0).getAction());
        Assert.assertEquals("FULL PROMPT", studio.getLoops().get(0).getAttempts().get(0).get("prompt"));
        Assert.assertEquals(2, studio.getLoops().get(0).getStateViewSources().size());
        Assert.assertEquals(Long.valueOf(12L), studio.getLastSeq());
    }

    private void stubPayload(IPayloadRepository repository, String id, String content) {
        when(repository.findPayload(id)).thenReturn(Optional.of(AgentPayloadEntity.builder()
                .payloadId(id).payloadType(PayloadTypeEnumVO.JSON).content(content).preview(content).build()));
    }
}
