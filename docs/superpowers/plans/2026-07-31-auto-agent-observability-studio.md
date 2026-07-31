# AutoAgent Developer Observability Studio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a dev-only, full-screen AutoAgent observability studio that appends a live execution graph and exposes structured MainNode state, prompt, attempts, actions, tool/child-agent results, checkpoints, and per-loop data without flattening JSON into unreadable text.

**Architecture:** Keep Runtime as the source of truth. Extend the existing developer trace path to persist complete MainNode invocation snapshots in the existing payload/trace tables, add a read-only `debug/studio` aggregation facade over run/context/loop/trace/evidence/tool data, and let a standalone static page refresh that snapshot after existing debug SSE events. The normal chat page only opens the studio and does not duplicate its data model.

**Tech Stack:** Java 17, Spring Boot 3.4, existing DDD ports/facades/repositories, FastJSON payload serialization, JUnit 4/Mockito tests, vanilla HTML/CSS/ES2020 with inline SVG and EventSource.

---

### Task 1: Define the structured studio snapshot contract

**Files:**
- Create: `ai-agent-station-study-api/src/main/java/yhx/com/api/dto/agent/AgentObservabilityStudioDTO.java`
- Create: `ai-agent-station-study-api/src/main/java/yhx/com/api/dto/agent/AgentObservabilityLoopDTO.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/observability/AgentObservabilityStudioDtoTest.java`

- [ ] **Step 1: Write the failing contract test**

```java
@Test
public void studio_contract_keeps_identity_in_header_and_context_in_sections() {
    AgentObservabilityStudioDTO studio = AgentObservabilityStudioDTO.builder()
            .header(Map.of("runId", "run-1", "sessionId", "session-1"))
            .context(Map.of("stateView", Map.of("taskLedger", Map.of("version", 2))))
            .loops(List.of(AgentObservabilityLoopDTO.builder()
                    .loopIndex(1).action("CALL_TOOL")
                    .stateView(Map.of("sources", List.of(Map.of("id", "memory-1"))))
                    .build()))
            .build();

    Assert.assertEquals("run-1", studio.getHeader().get("runId"));
    Assert.assertEquals("CALL_TOOL", studio.getLoops().get(0).getAction());
    Assert.assertEquals("memory-1", ((Map<?, ?>) ((List<?>) studio.getLoops().get(0)
            .getStateView().get("sources")).get(0)).get("id"));
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:
`mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentObservabilityStudioDtoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: compilation failure because the two DTO classes do not exist.

- [ ] **Step 3: Add the DTOs with explicit responsibilities**

`AgentObservabilityStudioDTO` contains `header`, `status`, `currentPhase`, `context`, `loops`, `traces`, `payloads`, `evidence`, `toolCalls`, `pendingInput`, `finalAnswer`, and `lastSeq`. `header` is the only place for `runId`, `sessionId`, `userId`, and `agentId`; loop/detail objects may refer to loop and payload IDs but must not repeat those four identity values. `AgentObservabilityLoopDTO` contains loop index/status/stage/timestamps, structured `stateView`, `stateViewSources`, `promptRefs`, `attempts`, `action`, `actionInput`, `actionOutput`, `runtimeOutcome`, `toolResults`, `childAgentResults`, `checkpoint`, and `error` as typed `Map<String,Object>` fields. Add Lombok builder/data/no-args/all-args annotations and `Serializable` where the API module convention uses it.

- [ ] **Step 4: Run the focused test and verify it passes**

Run the same Maven command. Expected: `Tests run: 1, Failures: 0`.

- [ ] **Step 5: Commit the contract**

```powershell
git add ai-agent-station-study-api/src/main/java/yhx/com/api/dto/agent/AgentObservabilityStudioDTO.java ai-agent-station-study-api/src/main/java/yhx/com/api/dto/agent/AgentObservabilityLoopDTO.java ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/observability/AgentObservabilityStudioDtoTest.java
git commit -m "agent: add observability studio contract"
```

### Task 2: Persist complete MainNode invocation observations

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/DeveloperTraceRecorder.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/invocation/NodeInvocationPipeline.java`
- Modify: `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentRuntimeConfig.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/invocation/NodeInvocationPipelineObservabilityTest.java`

- [ ] **Step 1: Write the failing observation test**

Create in-memory `IEventTraceRepository` and `IPayloadRepository` implementations, inject a `DeveloperTraceRecorder` into the pipeline, invoke a `MAIN_AGENT` command with a nested state view and a fake successful response, then assert that the saved NODE_INPUT payload contains the full `inputView`, `prompt`, `systemPrompt`, `userPrompt`, `invocationMetadata`, `attemptNo`, and `componentCode`, while the NODE_OUTPUT payload contains `rawOutput`, `parseResult`, `validationResult`, `typedOutput`, and `success`. Assert the existing bounded `RunDiagnosticRecorder` behavior remains unchanged.

- [ ] **Step 2: Run the focused test and verify it fails**

Run:
`mvn -q -pl ai-agent-station-study-app -am "-Dtest=NodeInvocationPipelineObservabilityTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: compilation failure because the pipeline has no full-observation recorder constructor/methods.

- [ ] **Step 3: Add full observation methods to `DeveloperTraceRecorder`**

Add `nodeInput(String runId, Integer loopIndex, String componentCode, Integer attemptNo, Map<String,Object> details)` and `nodeOutput(...)` methods. Each method copies the supplied fields into a linked map, adds `event` (`node_input_full` or `node_output_full`), `code`, `attemptNo`, and `loopIndex`, and calls the existing `append` with `TraceTypeEnumVO.NODE_INPUT` or `NODE_OUTPUT`. Keep the existing compact phase/action/error methods unchanged. Serialize the full maps through the existing payload repository so the UI can resolve them by `payloadRef`.

- [ ] **Step 4: Call the recorder at both sides of every node attempt**

Add an optional `DeveloperTraceRecorder` field and constructor overload to `NodeInvocationPipeline`, preserving every existing constructor signature. After prompt assembly, call `nodeInput` with the original input view, assembled prompt, system/user prompt, invocation metadata, invocation mode, function specs, model/contract/prompt versions, and repair flag. After client/parse/validation mapping (including client-error paths), call `nodeOutput` with raw output, parse result, validation result, typed output, failure type/message, success, and status. Use the actual `attemptNo` and current loop index from invocation metadata when present. Do not replace or enlarge the existing bounded diagnostic records.

- [ ] **Step 5: Wire the recorder into Spring**

Change the `nodeInvocationPipeline` bean in `AutoAgentRuntimeConfig` to accept `DeveloperTraceRecorder` and invoke the new constructor overload. Existing tests that construct the pipeline without a recorder continue to use the old overload.

- [ ] **Step 6: Run focused regression tests**

Run:
`mvn -q -pl ai-agent-station-study-app -am "-Dtest=NodeInvocationPipelineTest,NodeInvocationPipelineObservabilityTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: all pipeline tests pass, including the existing preview-size assertions.

- [ ] **Step 7: Commit the observation capture**

```powershell
git add ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/DeveloperTraceRecorder.java ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/invocation/NodeInvocationPipeline.java ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentRuntimeConfig.java ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/invocation/NodeInvocationPipelineObservabilityTest.java
git commit -m "agent: capture complete node observations"
```

### Task 3: Aggregate the read-only studio snapshot

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/api/AgentDebugFacade.java`
- Modify: `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentApiConfig.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/AgentDebugController.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/support/AgentApiMapper.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/observability/AgentDebugFacadeStudioTest.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/trigger/agent/AgentObservabilityStudioApiTest.java`

- [ ] **Step 1: Write the failing facade test**

Mock `IRunRepository`, `IRunContextRepository`, `IEventTraceRepository`, `IPayloadRepository`, evidence/tool repositories, and access policy. Return one run, one context with base/ledger/control refs, two loop rows with serialized `RunLoopRecordVO`, and full node traces. Assert `AgentDebugFacade.loadStudio("run-1")` returns one fixed header, two loops in ascending index order, context state plus source refs, trace payloads keyed by ref, and a `lastSeq` matching the highest trace sequence. Assert a missing context still returns the run/header and an empty context section instead of failing the entire response.

- [ ] **Step 2: Run the focused tests and verify they fail**

Run:
`mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentDebugFacadeStudioTest,AgentObservabilityStudioApiTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: compilation failure because `loadStudio` and the controller route do not exist.

- [ ] **Step 3: Implement `AgentDebugFacade.loadStudio`**

Inject `IRunRepository` and `IRunContextRepository` through the constructor and Spring bean. Keep `requireDebugApiEnabled()` at the entry. Build the fixed header from `AgentRunEntity`; load `AgentRunContextEntity`, resolve its three payload refs, list and resolve every loop record, and map each `RunLoopRecordVO` into `AgentObservabilityLoopDTO`. Derive `action`, action input/output, runtime outcome, user interaction/checkpoint, affected IDs, and errors from the typed loop record. Collect traces through the existing repository, resolve every trace payload with the existing preview policy, and include evidence/tool calls. Include `pendingInput` and final answer through the existing query facade wiring in the controller or a facade helper. Preserve payload IDs and structured maps; never stringify the entire JSON object as the primary field. If a context payload is malformed or absent, put a `loadError` field in the affected section and continue returning other sections.

- [ ] **Step 4: Add the `GET /agent/runs/{runId}/debug/studio` route**

Call `agentDebugFacade.loadStudio(runId)` and return it through `AgentResponseSupport.success`. Reuse existing exception-to-failed-response behavior. Keep existing traces/evidence/tool/payload/export/SSE routes backward compatible.

- [ ] **Step 5: Run facade/API tests and verify they pass**

Run the same focused Maven command. Expected: all tests pass and the API response contains the fixed header, ordered loop list, structured payload map, and `lastSeq`.

- [ ] **Step 6: Commit the backend snapshot**

```powershell
git add ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/api/AgentDebugFacade.java ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentApiConfig.java ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/AgentDebugController.java ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/support/AgentApiMapper.java ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/observability/AgentDebugFacadeStudioTest.java ai-agent-station-study-app/src/test/java/yhx/com/test/trigger/agent/AgentObservabilityStudioApiTest.java
git commit -m "agent: expose structured observability snapshot"
```

### Task 4: Build the standalone animated studio page

**Files:**
- Create: `docs/dev-ops/nginx/html/agent_observability.html`
- Modify: `docs/dev-ops/nginx/html/agent_runtime.html:2790-2810`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/frontend/AgentObservabilityStudioFrontendTest.java`

- [ ] **Step 1: Write the failing static frontend test**

Read the two HTML files as UTF-8 and assert the new page contains `agent-debug-studio`, `/debug/studio`, `/debug/events/stream`, `stateViewSources`, `mainNodePrompt`, `showDetail`, `renderStructured`, and the action edge labels `tool_use`, `ask_user`, `retrieve_rag`, `delegate`, `ready_to_deliver`, `final`. Assert `agent_runtime.html` contains a button/link that opens `agent_observability.html` with the active run ID.

- [ ] **Step 2: Run the focused test and verify it fails**

Run:
`mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentObservabilityStudioFrontendTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: assertion failure because the standalone page and launcher are absent.

- [ ] **Step 3: Implement the page shell and visual graph**

Create a self-contained page with:

1. Fixed glass Run Header showing run/session/user/agent once, status, phase, loop count, and a close/back action.
2. A horizontally scrolling SVG/HTML mainline. Each snapshot refresh appends only unseen nodes keyed by `traceId`, `loopIndex`, `attemptNo`, and event; edges carry action text and animate once. Use compact cards (`上下文准备`, `State View`, `MainNode`, `tool_use`, `ask_user`, `子 Agent`, `最终交付`) and status colors.
3. Hover popovers with timestamp, loop, status, and field counts.
4. Click inspector with tabs `Overview`, `State View`, `Sources`, `Prompt`, `Input`, `Output`, `Action`, `Runtime`, `Checkpoint`, `Attempts`, `Error`, and `Raw`. Render objects by recursive field groups, arrays as rows/cards, and collapsible long text; keep Raw collapsed and secondary.
5. EventSource connection to `/agent/runs/{runId}/debug/events/stream`, replay-safe `lastSeq`, heartbeat handling, reconnect with backoff, and snapshot fetch from `/debug/studio` after each event. On first load fetch snapshot before opening SSE so historical loops appear in order.
6. Failure/empty/loading states, reduced-motion media query, keyboard-accessible cards, and no external runtime dependency.

- [ ] **Step 4: Add the chat launcher without changing chat behavior**

Add a `运行观测` button next to the existing Debug button. Its click handler reads `state.activeRunId`, builds `agent_observability.html?runId=...&api=...`, and opens a new tab. If no run exists, show the existing toast/recordDebug path rather than opening an empty panel.

- [ ] **Step 5: Run the frontend static test and verify it passes**

Run the same Maven command. Expected: all assertions pass.

- [ ] **Step 6: Commit the frontend**

```powershell
git add docs/dev-ops/nginx/html/agent_observability.html docs/dev-ops/nginx/html/agent_runtime.html ai-agent-station-study-app/src/test/java/yhx/com/test/frontend/AgentObservabilityStudioFrontendTest.java
git commit -m "agent: add animated observability studio"
```

### Task 5: Verify live behavior and data presentation

**Files:**
- Modify: `docs/superpowers/plans/2026-07-31-auto-agent-observability-studio.md` (checklist only)
- Inspect: `docs/dev-ops/nginx/html/agent_observability.html`
- Inspect: `docs/dev-ops/nginx/html/agent_runtime.html`

- [ ] **Step 1: Run all feature tests and compile**

Run:
`mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentObservabilityStudioDtoTest,NodeInvocationPipelineTest,NodeInvocationPipelineObservabilityTest,AgentDebugFacadeStudioTest,AgentObservabilityStudioApiTest,AgentObservabilityStudioFrontendTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: all selected tests pass.

Run:
`mvn -q -DskipTests compile`

Expected: Maven exits with code 0.

- [ ] **Step 2: Perform a read-only static UX audit**

Verify with `rg` that the page has no `JSON.stringify(...` in the primary inspector renderer, no repeated identity fields inside loop cards, no raw payload preformatted by default, and that each SSE event triggers a snapshot refresh. Verify the action labels are rendered on edges rather than as full-size cards.

- [ ] **Step 3: Run `git diff --check` and inspect the feature diff**

Run:
`git diff --check`

Expected: no whitespace errors. Then inspect `git diff --stat` and `git diff --name-only` to ensure only the feature files were staged; leave unrelated dirty user changes untouched.

- [ ] **Step 4: Request the explicit code review**

Dispatch the `superpowers:code-reviewer` subagent against the feature commits and working tree. Ask it to check: MainNode full-data completeness, loop ordering, trace/payload security boundary, dev-only gating, SSE reconnect/replay, frontend accessibility, and regression risk. Address concrete findings, rerun focused tests/compile, and record the review result in the final handoff.

- [ ] **Step 5: Commit review fixes and report branch state**

After fixes pass verification:

```powershell
git status --short
git log --oneline -5
```

Keep the branch `codex/auto-agent-observability-studio-v1`, keep `master` unchanged, and explicitly tell the user that the branch was created in the existing dirty working directory so their pre-existing RunContext/Timeline edits were preserved.
