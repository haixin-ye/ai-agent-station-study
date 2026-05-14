# AutoAgent Main-Loop Harness Progress

## 2026-05-14 Phase 8 Tool MCP Permission Approval

- Branch: `feature/auto-agent-main-loop-harness`
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
