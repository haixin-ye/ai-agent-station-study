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
- Status: implemented and verified, pending checkpoint commit
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
- Next: commit Phase 10 checkpoint, then continue Phase 11 old harness isolation cleanup.
