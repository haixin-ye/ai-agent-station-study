# AutoAgent Main-Loop Harness Progress

## 2026-05-14 Phase 8 Tool MCP Permission Approval

- Branch: `feature/auto-agent-main-loop-harness`
- Checkpoint commit: `b0d9919 agent: add tool mcp runtime approval flow`
- Status: implemented and verified
- Scope: capability registry, MCP client/tool registries, permission enforcement, tool approval lifecycle, argument materialization, request builder, Spring AI MCP invoker/discovery adapters, ToolRuntime, receipt capture, ToolVerifier, tool evidence, tool transcript recorder, ToolActionOrchestrator, app bean wiring, targeted tests.
- Verification:
  - `mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile` passed.
  - `mvn -q -pl ai-agent-station-study-infrastructure -am -DskipTests compile` passed.
  - `mvn -q -pl ai-agent-station-study-app -am -DskipTests compile` passed.
  - `mvn -q -pl ai-agent-station-study-app -am "-Dtest=CapabilityRegistryTest,PermissionEnforcerTest,ToolApprovalServiceTest,ToolArgumentMaterializerTest,ToolRuntimeTest,ToolVerifierTest,ToolActionOrchestratorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed: 28 tests, 0 failures.
- Consistency checks:
  - Tool services do not reference `ChatClient` or `MainAgentNode`.
  - `createPendingInput` appears only in `ToolApprovalService`.
  - Tool approval uses `SINGLE_CHOICE`, `allowFreeText=false`; `FREE_TEXT` is rejected and cannot authorize execution.
- Next: commit Phase 8 checkpoint, then continue Phase 9 final response guard and repair.

## 2026-05-15 Phase 9 Final Response Guard Repair

- Branch: `feature/auto-agent-main-loop-harness`
- Status: implemented and verified
- Scope: final response guard input/result flow, Java MVP guard chain, final delivery service, final repair service, fixed safe fallback, final response persistence boundary, app bean wiring, and targeted tests.
- Verification:
  - `mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile` passed.
  - `mvn -q -pl ai-agent-station-study-app -am -DskipTests compile` passed.
  - `mvn -q -pl ai-agent-station-study-app -am "-Dtest=FinalResponseGuardTest,FinalDeliveryServiceTest,FinalRepairServiceTest,FinalResponsePersistenceBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed: 22 tests, 0 failures.
- Consistency checks:
  - Normal assistant message creation appears in `FinalResponsePersistenceService`; the other service-layer `appendMessage` usage is the runtime user-message append path.
  - Internal terms in final response code are limited to guard blocked terms, repair instruction, enum/import names, and debug trace persistence.
- Checkpoint commit: `d1f38be agent: add final response guard delivery`
- Next: continue Phase 10 API/SSE/debug/mock.

## 2026-05-15 Phase 10 API SSE Debug Mock

- Branch: `feature/auto-agent-main-loop-harness`
- Status: implemented and verified
- Checkpoint commit: `b9d9f6e agent: expose api sse debug mock endpoints`
- Scope: safe frontend DTOs, chat/run/message/final/event/pending-input/artifact controllers, debug endpoints, mock scenario endpoints, trigger-layer `SseEmitterRegistry`, domain API facades, debug data access policy, debug payload preview policy, and focused Phase 10 tests.
- Boundary decision: `SseEmitterRegistry` lives in trigger because `SseEmitter` is a Spring MVC web type. Domain exposes framework-free event replay bridges.
- Current SSE behavior: normal/debug streams replay persisted events on connect and use separate stream keys; mock streams emit scenario events immediately. Runtime append-to-live-emitter bridging remains a later enhancement.
- Verification:
  - `mvn -q -pl ai-agent-station-study-api -am -DskipTests compile` passed.
  - `mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile` passed.
  - `mvn -q -pl ai-agent-station-study-trigger -am -DskipTests compile` passed.
  - `mvn -q -pl ai-agent-station-study-app -am -DskipTests compile` passed.
  - `mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentMockScenarioApiTest,AgentDebugApiBoundaryTest,AgentSseEventApiTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed: 6 tests, 0 failures.
- Consistency checks:
  - Normal API scan for `rawOutput|rawPrompt|toolReceipt|StateView|StateDelta|verifier|guardDetail|tracePayload` returned no matches.
  - SSE scan confirms normal stream uses `normal:{runId}` and debug stream uses `debug:{runId}`.
- Next: continue Phase 11 old harness isolation cleanup.

## 2026-05-15 Phase 11 Old Harness Isolation Cleanup

- Branch: `feature/auto-agent-main-loop-harness`
- Status: implemented and verified
- Checkpoint commit: `961a421 agent: isolate legacy auto harness`
- Scope: old Node1-4 caller audit, legacy default-off configuration, runtime enabled flag, old normal frontend field cleanup, and migration isolation tests.
- Audit result: no old Node1-4 harness references remain in normal Java source. Remaining matches are historical docs/logs or Phase 11 plan text.
- Boundary decision: no legacy compare API was added because no normal old harness caller remains.
- Verification:
  - `mvn -q -pl ai-agent-station-study-app -am -DskipTests compile` passed.
  - `mvn -q -pl ai-agent-station-study-app -am "-Dtest=OldHarnessRoutingIsolationTest,OldTraceOutputIsolationTest,LegacySwitchTest,NormalApiNoOldTraceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed: 12 tests, 0 failures.
- Consistency checks:
  - Old Node1-4 scan across trigger/api/app main source and canonical spec returned no matches.
  - Normal frontend/API scan for `stepPlan|todoList|understanding|rawResult` returned no matches after removing the old `execution_understanding` static UI mapping.
- Next: continue Phase 12 MVP verification review.

## 2026-05-17 Phase 12 MVP Verification Review

- Branch: `feature/auto-agent-main-loop-harness`
- Status: implemented and verified
- Checkpoint commit: `1ba269f test: verify auto agent mvp`
- Scope: MVP scenario fixtures, scenario/safety property tests, targeted verification matrix, normal/debug/legacy boundary scans, verification report, and known gaps backlog.
- MVP decision: PASS with accepted non-blocking gaps documented in `docs/superpowers/reviews/2026-05-12-auto-agent-known-gaps-backlog.md`.
- Verification:
  - `mvn -q -pl ai-agent-station-study-app -am "-Dtest=AutoAgentMvpScenarioTest,AutoAgentSafetyPropertyTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed: 8 tests, 0 failures.
  - Contract/prompt/context matrix passed: 50 tests, 0 failures.
  - Runtime/action handler matrix passed: 58 tests, 0 failures.
  - RAG/tool/final matrix passed: 71 tests, 0 failures.
  - API/SSE/debug/legacy/MVP matrix passed: 26 tests, 0 failures.
  - `mvn -q -pl ai-agent-station-study-app -am -DskipTests compile` passed.
- Consistency checks:
  - Normal API leak scan for raw/internal fields returned no matches.
  - Old Node1-4 normal path scan returned no matches.
  - Old frontend/API field scan for `stepPlan|todoList|understanding|rawResult` returned no matches.
- Notes:
  - Direct answer and artifact clarification scenarios run through Runtime.
  - Other scenario fixtures are covered by focused module tests; richer monolithic scenario runner is recorded as GAP-001.
- Next: prepare final development summary.

## 2026-05-17 Phase 13 Frontend Runtime Chat MVP

- Branch: `feature/auto-agent-main-loop-harness`
- Status: first frontend slice implemented and locally checked
- Scope: added a new static runtime chat page at `docs/dev-ops/nginx/html/agent_runtime.html`.
- Design direction: Claude-style clean conversation surface plus Codex-style concise run progress. Cursor/Linear-style dense panels are avoided in normal mode.
- Behavior:
  - Left sidebar contains sessions and mock scenarios.
  - Center area contains normal chat, user-visible progress events, pending-input cards, artifact cards, and the composer.
  - Right debug drawer is hidden by default and opens only through the explicit Debug button.
  - Mock mode can run without backend availability by falling back to local mock events.
  - Real mode is wired to `/agent/chat`, `/agent/runs/{runId}/events/stream`, `/agent/runs/{runId}/user-input`, and `/agent/artifacts/{artifactId}`.
- Verification:
  - Extracted inline JavaScript from `agent_runtime.html` and ran `node --check`; command exited successfully.
  - Static scan for old normal-output fields `stepPlan|todoList|understanding|rawResult|StateView|StateDelta|trace payload|思考链路|节点调用|节点` returned no matches.
- Notes:
  - This is a frontend MVP page, not a replacement of the old `index_cool.html`.
  - Real `/agent/chat` still depends on complete Spring runtime bean wiring; mock mode is ready for frontend review.

## 2026-05-18 Phase 13 Frontend Review Fixes

- Branch: `feature/auto-agent-main-loop-harness`
- Status: implemented and locally checked
- Scope: fixed frontend/spec mismatches found during review.
- Changes:
  - Canonical spec now uses `content` for `POST /agent/chat` and removes MVP `MULTI_CHOICE` pending input support.
  - Pending input modes now allow only `CONFIRM`, `SINGLE_CHOICE`, `FREE_TEXT`, and `SINGLE_CHOICE_OR_FREE_TEXT`.
  - Frontend mock scenarios changed choice-or-text cases from `SINGLE_CHOICE` to `SINGLE_CHOICE_OR_FREE_TEXT`.
  - Frontend SSE client now deduplicates events by `eventId` or `runId + seq` and appends `lastSeq` on reconnect.
  - Frontend now fetches real pending input details from `/agent/runs/{runId}/pending-input` when an SSE event carries a `pendingId`.
- Verification:
  - Extracted inline JavaScript from `agent_runtime.html` and ran `node --check`; command exited successfully.
  - Canonical spec and frontend scan for `MULTI_CHOICE` returned no matches.
  - Static scan for old normal-output fields `stepPlan|todoList|understanding|rawResult|StateView|StateDelta|trace payload|思考链路|节点调用|节点` returned no matches.
