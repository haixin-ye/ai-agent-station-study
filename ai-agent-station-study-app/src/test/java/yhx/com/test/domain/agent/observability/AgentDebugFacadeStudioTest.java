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
        stubPayload(payloads, "loop-2", "{\"runId\":\"run-1\",\"loopIndex\":2,\"status\":\"COMPLETED\",\"mainOutput\":{\"action\":\"CALL_TOOL\",\"stateDelta\":{\"toolRequest\":{\"toolName\":\"search\"}}},\"runtimeOutcome\":{\"status\":\"CONTINUE_LOOP\",\"details\":{\"childAgentResults\":{\"child-1\":{\"taskId\":\"task-1\",\"result\":\"ok\"}}}}}");
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
        Assert.assertEquals(1, studio.getLoops().get(0).getChildAgentResults().size());
        Assert.assertEquals("child-1", studio.getLoops().get(0).getChildAgentResults().get(0).get("childRunId"));
        Assert.assertTrue(studio.getGraphNodes().stream().anyMatch(node -> "CONTEXT_PREPARE".equals(node.get("type"))));
        Assert.assertFalse(studio.getGraphNodes().stream()
                .anyMatch(node -> "CONTEXT_PLANNER".equals(node.get("type"))));
        Assert.assertTrue(studio.getGraphNodes().stream().anyMatch(node -> "MAIN_NODE".equals(node.get("type"))
                && "INFO".equals(node.get("severity"))));
        Assert.assertFalse(studio.getLoops().get(0).getError().containsKey("status"));
        Assert.assertEquals(Long.valueOf(12L), studio.getLastSeq());
    }

    @Test
    public void list_traces_after_uses_the_cursor_query_for_live_replay() {
        IEventTraceRepository traces = mock(IEventTraceRepository.class);
        IEvidenceRepository evidence = mock(IEvidenceRepository.class);
        IToolRepository tools = mock(IToolRepository.class);
        IPayloadRepository payloads = mock(IPayloadRepository.class);
        DebugAccessPolicy access = mock(DebugAccessPolicy.class);
        when(traces.listDebugTraceAfter("run-1", 17L, 20)).thenReturn(List.of(
                AgentRunTraceEntity.builder().runId("run-1").seq(18L).build()));
        AgentDebugFacade facade = new AgentDebugFacade(traces, evidence, tools, payloads, access,
                new DebugPayloadPreviewPolicy(100_000, true));

        List<AgentRunTraceEntity> result = facade.listTracesAfter("run-1", 17L, 20);

        Assert.assertEquals(1, result.size());
        Assert.assertEquals(Long.valueOf(18L), result.get(0).getSeq());
    }

    @Test
    public void studio_recovers_canonical_state_and_legacy_planner_without_loop_index() {
        IEventTraceRepository traces = mock(IEventTraceRepository.class);
        IEvidenceRepository evidence = mock(IEvidenceRepository.class);
        IToolRepository tools = mock(IToolRepository.class);
        IPayloadRepository payloads = mock(IPayloadRepository.class);
        IRunRepository runs = mock(IRunRepository.class);
        IRunContextRepository contexts = mock(IRunContextRepository.class);
        DebugAccessPolicy access = mock(DebugAccessPolicy.class);

        when(runs.findRun("run-recovery")).thenReturn(Optional.of(AgentRunEntity.builder()
                .runId("run-recovery").status(RunStatusEnumVO.COMPLETED)
                .phase(RuntimePhaseEnumVO.COMPLETED).build()));
        when(contexts.findContext("run-recovery")).thenReturn(Optional.of(AgentRunContextEntity.builder()
                .runId("run-recovery").schemaVersion(2).baseContextRef("base-recovery")
                .taskLedgerRef("ledger-recovery").runtimeControlRef("control-recovery").build()));
        when(contexts.listLoops("run-recovery")).thenReturn(List.of(AgentRunLoopEntity.builder()
                .runId("run-recovery").loopIndex(0).status("COMPLETED").recordRef("loop-recovery").build()));
        when(traces.listDebugTrace("run-recovery", 500)).thenReturn(List.of(
                AgentRunTraceEntity.builder().runId("run-recovery").seq(1L).traceType(TraceTypeEnumVO.NODE_INPUT)
                        .payloadRef("planner-recovery-in").build(),
                AgentRunTraceEntity.builder().runId("run-recovery").seq(2L).traceType(TraceTypeEnumVO.NODE_OUTPUT)
                        .payloadRef("planner-recovery-out").build(),
                AgentRunTraceEntity.builder().runId("run-recovery").seq(3L).traceType(TraceTypeEnumVO.NODE_INPUT)
                        .payloadRef("main-recovery-in").build(),
                AgentRunTraceEntity.builder().runId("run-recovery").seq(4L).traceType(TraceTypeEnumVO.NODE_OUTPUT)
                        .payloadRef("main-recovery-out").build()));
        when(evidence.listRunEvidence("run-recovery")).thenReturn(List.of());
        when(tools.listRunToolCalls("run-recovery", 100)).thenReturn(List.of());

        stubPayload(payloads, "base-recovery", "{\"runId\":\"run-recovery\",\"selectedSessionContext\":{"
                + "\"conversation\":{\"recentMessages\":[{\"messageId\":\"msg-1\"}]},"
                + "\"memoryPack\":[{\"memoryId\":\"memory-1\",\"memoryType\":\"LONG_TERM_MEMORY\"}],"
                + "\"ragPack\":[{\"candidateId\":\"rag-1\"}]}}");
        stubPayload(payloads, "ledger-recovery", "{}");
        stubPayload(payloads, "control-recovery", "{}");
        stubPayload(payloads, "loop-recovery", "{\"mainOutput\":{\"action\":\"READY_TO_DELIVER\"},\"runtimeOutcome\":{}}");
        stubPayload(payloads, "planner-recovery-in", "{\"event\":\"node_input_full\",\"code\":\"CONTEXT_PLANNER\","
                + "\"attemptNo\":1,\"inputView\":{\"recentMessages\":[{\"messageId\":\"msg-1\"}],"
                + "\"memoryCandidates\":[{\"memoryId\":\"memory-1\"}],\"ragCandidates\":[{\"candidateId\":\"rag-1\"}]}}");
        stubPayload(payloads, "planner-recovery-out", "{\"event\":\"node_output_full\",\"code\":\"CONTEXT_PLANNER\","
                + "\"attemptNo\":1,\"success\":true,\"typedOutput\":{\"status\":\"READY\"}}");
        stubPayload(payloads, "main-recovery-in", "{\"event\":\"node_input_full\",\"code\":\"MAIN_AGENT\",\"loopIndex\":0,"
                + "\"attemptNo\":1,\"inputView\":{\"runBaseContext\":{\"selectedSessionContext\":{"
                + "\"memoryPack\":[{\"memoryId\":\"memory-1\"}],\"ragPack\":[{\"candidateId\":\"rag-1\"}]}}}}");
        stubPayload(payloads, "main-recovery-out", "{\"event\":\"node_output_full\",\"code\":\"MAIN_AGENT\",\"loopIndex\":0,"
                + "\"attemptNo\":1,\"success\":true,\"rawOutput\":\"{\\\"action\\\":\\\"READY_TO_DELIVER\\\"}\"}");

        AgentDebugFacade facade = new AgentDebugFacade(traces, evidence, tools, payloads, access,
                new DebugPayloadPreviewPolicy(100_000, true), runs, contexts);

        AgentObservabilitySnapshotVO snapshot = facade.loadStudio("run-recovery");

        Assert.assertEquals(1, snapshot.getLoops().size());
        Assert.assertEquals(1, snapshot.getLoops().get(0).getSelectedContext().get("memoryPack") instanceof List
                ? ((List<?>) snapshot.getLoops().get(0).getSelectedContext().get("memoryPack")).size() : 0);
        Assert.assertFalse(snapshot.getLoops().get(0).getContextCandidates().isEmpty());
        Assert.assertFalse(snapshot.getLoops().get(0).getContextPlanner().isEmpty());
        Assert.assertTrue(snapshot.getGraphNodes().stream().anyMatch(node -> "CONTEXT_PLANNER".equals(node.get("type"))));
    }

    @Test
    public void studio_exposes_main_node_plan_history_delta_and_final_delivery() {
        IEventTraceRepository traces = mock(IEventTraceRepository.class);
        IEvidenceRepository evidence = mock(IEvidenceRepository.class);
        IToolRepository tools = mock(IToolRepository.class);
        IPayloadRepository payloads = mock(IPayloadRepository.class);
        IRunRepository runs = mock(IRunRepository.class);
        IRunContextRepository contexts = mock(IRunContextRepository.class);
        DebugAccessPolicy access = mock(DebugAccessPolicy.class);

        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 2, 20, 0);
        LocalDateTime completedAt = startedAt.plusSeconds(8);
        when(runs.findRun("run-final")).thenReturn(Optional.of(AgentRunEntity.builder()
                .runId("run-final").status(RunStatusEnumVO.COMPLETED)
                .phase(RuntimePhaseEnumVO.COMPLETED).build()));
        when(contexts.findContext("run-final")).thenReturn(Optional.of(AgentRunContextEntity.builder()
                .runId("run-final").schemaVersion(2).baseContextRef("base-final")
                .taskLedgerRef("ledger-final").runtimeControlRef("control-final").build()));
        when(contexts.listLoops("run-final")).thenReturn(List.of(AgentRunLoopEntity.builder()
                .runId("run-final").loopIndex(2).status("COMPLETED").recordRef("loop-final")
                .startedAt(startedAt).completedAt(completedAt).build()));
        when(traces.listDebugTrace("run-final", 500)).thenReturn(List.of(
                AgentRunTraceEntity.builder().runId("run-final").seq(20L).traceType(TraceTypeEnumVO.NODE_INPUT)
                        .payloadRef("main-final-in").createdAt(startedAt.plusSeconds(1)).build(),
                AgentRunTraceEntity.builder().runId("run-final").seq(21L).traceType(TraceTypeEnumVO.NODE_OUTPUT)
                        .payloadRef("main-final-out").createdAt(startedAt.plusSeconds(3)).build()));
        when(evidence.listRunEvidence("run-final")).thenReturn(List.of());
        when(tools.listRunToolCalls("run-final", 100)).thenReturn(List.of());

        stubPayload(payloads, "base-final", "{\"selectedSessionContext\":{\"memoryPack\":[{\"memoryId\":\"m-1\"}]}}");
        stubPayload(payloads, "ledger-final", "{\"version\":3,\"goal\":\"deliver answer\"}");
        stubPayload(payloads, "control-final", "{\"currentLoop\":2}");
        stubPayload(payloads, "loop-final", """
                {
                  "taskLedgerVersionBefore": 2,
                  "taskLedgerVersionAfter": 3,
                  "affectedStepIds": ["compose"],
                  "affectedDeliverableIds": ["answer"],
                  "mainOutput": {
                    "action": "FINAL",
                    "taskUpdate": {
                      "goal": "deliver answer",
                      "stepUpdates": [{"stepId":"compose","description":"Compose final answer","status":"COMPLETED"}],
                      "deliverableUpdates": [{"deliverableId":"answer","description":"User answer","status":"COMPLETED"}],
                      "lastDecision": "Everything is ready."
                    },
                    "stateDelta": {
                      "finalAnswerCandidate": {"content":"The real delivered answer.","format":"markdown"}
                    }
                  },
                  "actionRequest": {
                    "finalAnswerCandidate": {"content":"The real delivered answer.","format":"markdown"}
                  },
                  "runtimeOutcome": {
                    "status": "COMPLETED",
                    "summary": "Final response delivered.",
                    "evidenceRefs": ["e-1"],
                    "artifactRefs": []
                  }
                }
                """);
        stubPayload(payloads, "main-final-in", """
                {
                  "event": "node_input_full",
                  "code": "MAIN_AGENT",
                  "loopIndex": 2,
                  "attemptNo": 1,
                  "prompt": "FULL FINAL PROMPT",
                  "inputView": {
                    "runBaseContext": {
                      "selectedSessionContext": {
                        "conversation": {"recentMessages":[{"messageId":"msg-1","content":"hello"}]},
                        "memoryPack": [{"memoryId":"m-1"}],
                        "ragPack": []
                      }
                    },
                    "taskLedger": {
                      "version": 2,
                      "goal": "deliver answer",
                      "steps": [{"stepId":"compose","description":"Compose final answer","status":"IN_PROGRESS"}],
                      "deliverables": [{"deliverableId":"answer","description":"User answer","status":"READY"}]
                    },
                    "loopTimeline": [
                      {"loopIndex":1,"mainOutput":{"action":"READY_TO_DELIVER"},
                       "runtimeOutcome":{"status":"CONTINUE_LOOP","summary":"Ready for delivery."}}
                    ],
                    "resolvedPayloads": {},
                    "runtimeControl": {"mainAgentStage":"DELIVERING","loopIndex":2}
                  }
                }
                """);
        stubPayload(payloads, "main-final-out", """
                {"event":"node_output_full","code":"MAIN_AGENT","loopIndex":2,"attemptNo":1,
                 "success":true,"rawOutput":"{\\"action\\":\\"FINAL\\"}"}
                """);

        AgentDebugFacade facade = new AgentDebugFacade(traces, evidence, tools, payloads, access,
                new DebugPayloadPreviewPolicy(100_000, true), runs, contexts);

        AgentObservabilitySnapshotVO snapshot = facade.loadStudio("run-final");
        Map<String, Object> mainNode = graphNode(snapshot, "MAIN_NODE");
        Map<String, Object> mainDetails = asMap(mainNode.get("details"));
        Map<String, Object> finalNode = graphNode(snapshot, "FINAL_DELIVERY");
        Map<String, Object> finalDetails = asMap(finalNode.get("details"));

        Assert.assertEquals(1, ((List<?>) mainDetails.get("roundHistory")).size());
        Assert.assertEquals("deliver answer", asMap(mainDetails.get("taskLedger")).get("goal"));
        Assert.assertEquals("Everything is ready.", asMap(mainDetails.get("taskUpdate")).get("lastDecision"));
        Assert.assertEquals(2, asMap(mainDetails.get("roundDelta")).get("taskLedgerVersionBefore"));
        Assert.assertEquals(1, ((List<?>) asMap(mainDetails.get("selectedContext")).get("memoryPack")).size());
        Assert.assertEquals("The real delivered answer.",
                asMap(finalDetails.get("finalDelivery")).get("deliveredContent"));
        Assert.assertEquals("COMPLETED", asMap(finalDetails.get("finalDelivery")).get("status"));
    }

    @Test
    public void studio_assigns_legacy_trace_to_loop_by_time_window() {
        IEventTraceRepository traces = mock(IEventTraceRepository.class);
        IEvidenceRepository evidence = mock(IEvidenceRepository.class);
        IToolRepository tools = mock(IToolRepository.class);
        IPayloadRepository payloads = mock(IPayloadRepository.class);
        IRunRepository runs = mock(IRunRepository.class);
        IRunContextRepository contexts = mock(IRunContextRepository.class);
        DebugAccessPolicy access = mock(DebugAccessPolicy.class);

        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 2, 21, 0);
        when(runs.findRun("run-window")).thenReturn(Optional.of(AgentRunEntity.builder()
                .runId("run-window").status(RunStatusEnumVO.RUNNING)
                .phase(RuntimePhaseEnumVO.CALLING_MAIN_NODE).build()));
        when(contexts.findContext("run-window")).thenReturn(Optional.of(AgentRunContextEntity.builder()
                .runId("run-window").schemaVersion(2).baseContextRef("base-window")
                .taskLedgerRef("ledger-window").runtimeControlRef("control-window").build()));
        when(contexts.listLoops("run-window")).thenReturn(List.of(AgentRunLoopEntity.builder()
                .runId("run-window").loopIndex(4).status("COMPLETED").recordRef("loop-window")
                .startedAt(startedAt).completedAt(startedAt.plusSeconds(10)).build()));
        when(traces.listDebugTrace("run-window", 500)).thenReturn(List.of(
                AgentRunTraceEntity.builder().runId("run-window").seq(40L).traceType(TraceTypeEnumVO.RUNTIME_DECISION)
                        .payloadRef("legacy-window-trace").createdAt(startedAt.plusSeconds(5)).build()));
        when(evidence.listRunEvidence("run-window")).thenReturn(List.of());
        when(tools.listRunToolCalls("run-window", 100)).thenReturn(List.of());

        stubPayload(payloads, "base-window", "{}");
        stubPayload(payloads, "ledger-window", "{}");
        stubPayload(payloads, "control-window", "{}");
        stubPayload(payloads, "loop-window", "{\"mainOutput\":{\"action\":\"READY_TO_DELIVER\"}}");
        stubPayload(payloads, "legacy-window-trace",
                "{\"event\":\"node_observation\",\"code\":\"RUNTIME\",\"summary\":\"Legacy trace without loop index\"}");

        AgentDebugFacade facade = new AgentDebugFacade(traces, evidence, tools, payloads, access,
                new DebugPayloadPreviewPolicy(100_000, true), runs, contexts);

        AgentObservabilitySnapshotVO snapshot = facade.loadStudio("run-window");

        Assert.assertEquals(1, snapshot.getLoops().get(0).getTimeline().size());
        Assert.assertEquals("Legacy trace without loop index",
                snapshot.getLoops().get(0).getTimeline().get(0).get("summary"));
    }

    private Map<String, Object> graphNode(AgentObservabilitySnapshotVO snapshot, String type) {
        return snapshot.getGraphNodes().stream()
                .filter(node -> type.equals(node.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing graph node: " + type));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private void stubPayload(IPayloadRepository repository, String id, String content) {
        when(repository.findPayload(id)).thenReturn(Optional.of(AgentPayloadEntity.builder()
                .payloadId(id).payloadType(PayloadTypeEnumVO.JSON).content(content).preview(content).build()));
        when(repository.findContent(id)).thenReturn(Optional.of(content));
    }
}
