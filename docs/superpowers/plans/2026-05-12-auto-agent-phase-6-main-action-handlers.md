# AutoAgent Phase 6 Main Action Handlers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the `MainAgentAction` dispatch layer and action handlers that turn validated `MainAgentNode` actions into Runtime-owned state transitions.

**Architecture:** `MainAgentNode` emits one structured action. `Runtime` validates the action and calls `MainActionDispatcher`. Each handler validates its own `StateDelta`, applies only allowed state changes, writes user-visible events and developer traces separately, and returns the next Runtime routing result. RAG, ToolRuntime, and FinalResponseGuard are invoked through ports in this phase; their full implementations are planned in later phases.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Maven multi-module project, Lombok, Fastjson2, JUnit4, DDD package layout under `yhx.com`.

---

## 0. Execution Rules

- Start this phase only after Phase 5 Runtime and pending input boundaries compile.
- Do not change `MainAgentNode` prompt text in this phase.
- Do not let action handlers parse raw LLM output. They receive typed `MainAgentActionVO` only.
- Do not let action handlers write lifecycle fields directly except through Runtime-owned services.
- Do not implement real RAG retrieval, MCP invocation, or final guard internals here.
- Do not bypass `UserInteractionManager` for user questions.
- Do not append final assistant messages directly from handlers. Final text must go through `FinalDeliveryPort`.
- Do not commit unless the user explicitly asks.

## 1. Source Of Truth

Use the English canonical spec only:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`

Primary spec sections:

- Section 3.5: Main action routing
- Section 4.9: StateDelta write scope
- Section 4.10: StateDelta is not state
- Section 5.10-5.20: all `MainAgentAction` examples
- Section 6.4: final delivery path
- Section 7.6: artifact persistence
- Section 7.8: evidence persistence
- Section 7.9: pending input
- Section 13.9: Phase 6 action implementation order

## 2. Phase Boundary

### In Scope

- Replace `NoopMainActionDispatcher` with production `DefaultMainActionDispatcher`.
- Define one handler class per `MainAgentActionTypeEnumVO`.
- Validate action-specific `StateDelta` required fields.
- Persist artifact create/update through `ArtifactManager`.
- Route `ASK_USER` through `UserInteractionManager`.
- Route `RETRIEVE_RAG` through `RagRuntimePort`.
- Route `CALL_TOOL` through `ToolActionOrchestratorPort`.
- Route `FINAL`, `CREATE_ARTIFACT`, `UPDATE_ARTIFACT`, `REPAIR_FINAL`, and `FAIL` user-visible text through `FinalDeliveryPort`.
- Persist internal plan state through `PlanStateManager`.
- Return deterministic failure when required downstream ports are unavailable.
- Add action-handler tests with fake ports.

### Out Of Scope

- Real RAG search implementation.
- Real MCP client invocation.
- Real permission/approval engine internals.
- Real final guard rule implementation.
- API/SSE controller implementation.
- Old Node1-4 removal.

## 3. File Map

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/handler/`

Required files:

- `DefaultMainActionDispatcher.java`
- `FinalActionHandler.java`
- `CreateArtifactActionHandler.java`
- `UpdateArtifactActionHandler.java`
- `AskUserActionHandler.java`
- `RetrieveRagActionHandler.java`
- `CallToolActionHandler.java`
- `PlanActionHandler.java`
- `ContinueActionHandler.java`
- `RepairFinalActionHandler.java`
- `FailActionHandler.java`
- `MainActionHandlerSupport.java`
- `MainActionHandlerRegistry.java`

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/port/`

Required files:

- `FinalDeliveryPort.java`
- `RagRuntimePort.java`
- `ToolActionOrchestratorPort.java`
- `PlanStatePort.java`

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/`

Required files when missing:

- `FinalDeliveryCommandVO.java`
- `FinalDeliveryResultVO.java`
- `RagRuntimeCommandVO.java`
- `RagRuntimeResultVO.java`
- `ToolActionCommandVO.java`
- `ToolActionResultVO.java`
- `PlanStateVO.java`
- `PlanStepVO.java`

## 4. Handler Registry And Dispatch

### 4.1 `MainActionHandlerRegistry`

Required methods:

```java
MainActionHandler getHandler(MainAgentActionTypeEnumVO actionType);
boolean supports(MainAgentActionTypeEnumVO actionType);
```

Rules:

- Registry must contain exactly one handler for every `MainAgentActionTypeEnumVO`.
- Missing handler is a configuration error and must produce safe failure.

### 4.2 `DefaultMainActionDispatcher`

Required method:

```java
MainActionHandlerResult dispatch(RuntimeExecutionContext context, MainAgentActionVO action);
```

Procedure:

1. Validate `action` is not null.
2. Validate `action.action` is not null.
3. Validate `StateDelta` write scope with `ContractValidator`.
4. Resolve handler from registry.
5. Record developer trace for selected action.
6. Invoke handler.
7. Return handler result.

## 5. Shared Handler Support

### 5.1 `MainActionHandlerSupport`

Shared methods:

```java
void requireStateDelta(MainAgentActionVO action);
void requireFinalAnswerCandidate(MainAgentActionVO action);
void requireArtifactDraft(MainAgentActionVO action);
void requireArtifactPatch(MainAgentActionVO action);
void requireAskUserRequest(MainAgentActionVO action);
void requireRagRequest(MainAgentActionVO action);
void requireToolIntent(MainAgentActionVO action);
void requireFailure(MainAgentActionVO action);
MainActionHandlerResult safeFailure(RuntimeExecutionContext context, FailureCodeEnumVO code, String userMessage, String developerMessage);
```

Rules:

- Shared validation returns structured safe failures.
- Validation errors write developer trace.
- Validation errors do not append assistant messages directly.

## 6. Ports For Later Phases

### 6.1 `FinalDeliveryPort`

Required method:

```java
FinalDeliveryResultVO deliver(FinalDeliveryCommandVO command);
```

Phase 6 behavior:

- Tests use fake final delivery.
- Production implementation may throw `UnsupportedOperationException` until Phase 9.
- Handlers must still route all final text through this port.

`FinalDeliveryCommandVO` fields:

```java
private String runId;
private String sessionId;
private Integer loopIndex;
private MainAgentActionTypeEnumVO sourceAction;
private FinalAnswerCandidateVO finalAnswerCandidate;
private FailureVO failure;
private List<String> evidenceIds;
```

### 6.2 `RagRuntimePort`

Required method:

```java
RagRuntimeResultVO retrieve(RagRuntimeCommandVO command);
```

Phase 6 behavior:

- Handler sets `ragWasUsed` before calling this port.
- Fake implementation returns deterministic evidence id list.
- Real implementation comes in Phase 7.

### 6.3 `ToolActionOrchestratorPort`

Required method:

```java
ToolActionResultVO handleToolAction(ToolActionCommandVO command);
```

Phase 6 behavior:

- Handler validates `toolIntent`.
- Fake implementation can return `WAITING_USER`, `CONTINUE_LOOP`, or `FAILED`.
- Real permission/MCP/receipt flow comes in Phase 8.

### 6.4 `PlanStatePort`

Required methods:

```java
String savePlan(String runId, PlanStateVO plan);
PlanStateVO findPlan(String runId);
```

Phase 6 may implement in-memory fake for tests and repository-backed adapter later.

## 7. Action Handler Contracts

### 7.1 `FINAL`

Handler:

- `FinalActionHandler`

Required input:

- `stateDelta.finalAnswerCandidate`

Procedure:

1. Validate final answer candidate exists.
2. Build `FinalDeliveryCommandVO` with `sourceAction=FINAL`.
3. Call `FinalDeliveryPort.deliver`.
4. If final delivery passes, return `COMPLETED`.
5. If final delivery asks for repair, return next phase `REPAIRING_FINAL`.
6. If final delivery fails without repair, return `FAILED`.

Rules:

- Do not append assistant message directly.
- Do not mention final guard internals in user text.

### 7.2 `CREATE_ARTIFACT`

Handler:

- `CreateArtifactActionHandler`

Required input:

- `stateDelta.artifactDraft`

Procedure:

1. Validate draft type, title, and content.
2. Call `ArtifactManager.createArtifact`.
3. Record created artifact id in handler result.
4. Emit user-visible event `ARTIFACT_CREATED`.
5. Write developer trace with artifact id and payload ref only.
6. If `finalAnswerCandidate` exists, route through `FinalDeliveryPort`.
7. If no `finalAnswerCandidate`, return `CONTINUE_LOOP` with created artifact evidence.

Rules:

- Persist artifact body through payload storage.
- Do not put artifact body into user-visible event.
- Do not mark run completed unless final delivery completes.

### 7.3 `UPDATE_ARTIFACT`

Handler:

- `UpdateArtifactActionHandler`

Required input:

- `stateDelta.artifactPatch`

Procedure:

1. Validate target artifact id.
2. Validate update mode.
3. Call `ArtifactManager.updateArtifact`.
4. Record updated artifact id.
5. Emit user-visible event `ARTIFACT_UPDATED`.
6. If `finalAnswerCandidate` exists, route through `FinalDeliveryPort`.
7. If no `finalAnswerCandidate`, return `CONTINUE_LOOP`.

Allowed update modes:

```text
REPLACE_FULL, PATCH_TEXT, APPEND, CREATE_VERSION
```

### 7.4 `ASK_USER`

Handler:

- `AskUserActionHandler`

Required input:

- `stateDelta.askUserRequest`

Procedure:

1. Validate question and input mode.
2. Validate options when input mode requires options.
3. Build `ContinuationCheckpointVO` with handler `MainAgentPendingInputHandler`.
4. Call `UserInteractionManager.createPendingInput`.
5. Return `WAITING_USER`.

Rules:

- Do not persist pending input directly.
- Do not parse option labels.
- Do not call LLM for user answer interpretation.

### 7.5 `RETRIEVE_RAG`

Handler:

- `RetrieveRagActionHandler`

Required input:

- `stateDelta.ragRequest`

Procedure:

1. Validate retrieval query.
2. Set run-level `ragWasUsed=true` through `IRunRepository`.
3. Emit user-visible event `RETRIEVING_KNOWLEDGE`.
4. Call `RagRuntimePort.retrieve`.
5. Persist returned evidence refs through the port implementation or result contract.
6. Return `CONTINUE_LOOP` when retrieval succeeds or no-hit is recoverable.
7. Return `FAILED` only for non-recoverable runtime errors.

Rules:

- `RagVerifier` trigger later depends only on `ragWasUsed=true`.
- Do not trigger verifier by keyword scanning.

### 7.6 `CALL_TOOL`

Handler:

- `CallToolActionHandler`

Required input:

- `stateDelta.toolIntent`

Procedure:

1. Validate capability code.
2. Validate tool name or goal is present.
3. Validate `CALL_TOOL` does not include final answer candidate.
4. Build `ToolActionCommandVO`.
5. Call `ToolActionOrchestratorPort.handleToolAction`.
6. If tool flow needs approval, return `WAITING_USER`.
7. If tool call succeeds, return `CONTINUE_LOOP`.
8. If denied or failed safely, return `CONTINUE_LOOP` with failure evidence or `FAILED` according to port result.

Rules:

- Handler does not invoke MCP directly.
- Handler does not create approval directly.
- Handler does not claim tool success.
- Tool approval must use `UserInteractionManager` inside the later tool orchestrator.

### 7.7 `PLAN`

Handler:

- `PlanActionHandler`

Required input:

- `stateDelta.planDraft`

Procedure:

1. Validate plan goal and steps.
2. Save internal plan through `PlanStatePort`.
3. Write developer trace.
4. Return `CONTINUE_LOOP`.

Rules:

- Plan text is internal state.
- Plan text must not become final answer.

### 7.8 `CONTINUE`

Handler:

- `ContinueActionHandler`

Required input:

- optional `stateDelta.nextActionHint`

Procedure:

1. Validate loop budget remains through `RuntimeLoopPolicy`.
2. Store next action hint in runtime facts.
3. Return `CONTINUE_LOOP`.

Rules:

- Must not create empty infinite loops.
- If no hint and no new state changed, return safe failure or ask user according to Runtime policy.

### 7.9 `REPAIR_FINAL`

Handler:

- `RepairFinalActionHandler`

Required input:

- `stateDelta.finalAnswerCandidate`

Procedure:

1. Validate current phase is `REPAIRING_FINAL`.
2. Build `FinalDeliveryCommandVO` with `sourceAction=REPAIR_FINAL`.
3. Call `FinalDeliveryPort.deliver`.
4. Return `COMPLETED`, `REPAIRING_FINAL`, or `FAILED` according to delivery result and retry budget.

Rules:

- Only valid during final repair.
- Repaired candidate must pass final delivery again.

### 7.10 `FAIL`

Handler:

- `FailActionHandler`

Required input:

- `stateDelta.failure`

Procedure:

1. Validate user-safe failure message exists.
2. Persist technical failure details as developer trace.
3. Convert failure user message into `FinalAnswerCandidateVO`.
4. Route candidate through `FinalDeliveryPort`.
5. If delivery passes, return `FAILED` with final response refs.
6. If delivery fails, return fixed safe failure from `RuntimeFailureFactory`.

Rules:

- Do not append `failure.userMessage` directly.
- Do not show technical code, stack trace, prompt, node, trace, contract, or tool receipt in normal chat UI.

## 8. Result Routing Table

| Handler status | Runtime next behavior |
|---|---|
| `CONTINUE_LOOP` | increment loop after dispatch and return to `PREPARING_CONTEXT` |
| `WAITING_USER` | set run status `WAITING_USER` and stop loop |
| `COMPLETED` | set run `COMPLETED` |
| `FAILED` | set run `FAILED` after safe final text path |
| `CANCELLED` | set run `CANCELLED` |

## 9. Required Tests

Create under:

- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/handler/`

Required test files:

- `MainActionDispatcherTest.java`
- `FinalActionHandlerTest.java`
- `ArtifactActionHandlerTest.java`
- `AskUserActionHandlerTest.java`
- `RetrieveRagActionHandlerTest.java`
- `CallToolActionHandlerTest.java`
- `PlanContinueActionHandlerTest.java`
- `RepairFinalAndFailActionHandlerTest.java`

### 9.1 Dispatcher Tests

Required test cases:

1. `dispatcher_rejects_missing_action`
2. `dispatcher_rejects_state_delta_scope_violation`
3. `dispatcher_routes_every_action_to_one_handler`
4. `missing_handler_returns_safe_failure`

### 9.2 Final Tests

Required test cases:

1. `final_action_uses_final_delivery_port`
2. `final_action_does_not_append_message_directly`
3. `final_delivery_repair_result_routes_to_repairing_final`

### 9.3 Artifact Tests

Required test cases:

1. `create_artifact_persists_payload_and_metadata`
2. `create_artifact_with_final_candidate_uses_final_delivery`
3. `update_artifact_validates_target`
4. `artifact_event_does_not_include_full_body`

### 9.4 Ask User Tests

Required test cases:

1. `ask_user_routes_through_user_interaction_manager`
2. `ask_user_does_not_persist_pending_input_directly`
3. `ask_user_single_choice_requires_options`

### 9.5 RAG Tests

Required test cases:

1. `retrieve_rag_sets_rag_was_used_before_port_call`
2. `retrieve_rag_success_continues_loop`
3. `retrieve_rag_no_hit_can_continue_with_evidence_or_recovery`

### 9.6 Tool Tests

Required test cases:

1. `call_tool_requires_capability_code`
2. `call_tool_does_not_invoke_mcp_directly`
3. `call_tool_waiting_approval_returns_waiting_user`
4. `call_tool_success_continues_loop`

### 9.7 Plan And Continue Tests

Required test cases:

1. `plan_is_saved_as_internal_state`
2. `plan_is_not_final_answer`
3. `continue_requires_loop_budget`
4. `continue_without_state_change_fails_safely`

### 9.8 Repair And Fail Tests

Required test cases:

1. `repair_final_only_valid_in_repairing_final`
2. `repair_final_uses_final_delivery_port`
3. `fail_action_routes_user_message_through_final_delivery`
4. `fail_action_hides_technical_fields_from_user_text`

## 10. Execution Tasks

### Task 1: Add Handler Ports And Result VOs

**Files:**

- Create files listed in Sections 3 and 6.

- [ ] Implement port interfaces.
- [ ] Implement command/result VOs.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 2: Add Handler Registry And Dispatcher

**Files:**

- `DefaultMainActionDispatcher.java`
- `MainActionHandlerRegistry.java`
- `MainActionHandlerSupport.java`

- [ ] Register exactly one handler per action.
- [ ] Enforce StateDelta scope validation before handler call.
- [ ] Return safe failure for missing handler.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 3: Add Final And Artifact Handlers

**Files:**

- `FinalActionHandler.java`
- `CreateArtifactActionHandler.java`
- `UpdateArtifactActionHandler.java`

- [ ] Implement Sections 7.1-7.3.
- [ ] Ensure artifact bodies are stored through payload/artifact services.
- [ ] Route user-visible text through `FinalDeliveryPort`.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 4: Add Ask User, RAG, And Tool Handlers

**Files:**

- `AskUserActionHandler.java`
- `RetrieveRagActionHandler.java`
- `CallToolActionHandler.java`

- [ ] Implement Sections 7.4-7.6.
- [ ] Ensure ASK_USER uses `UserInteractionManager`.
- [ ] Ensure RETRIEVE_RAG sets `ragWasUsed=true`.
- [ ] Ensure CALL_TOOL uses `ToolActionOrchestratorPort`.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 5: Add Plan, Continue, Repair, And Fail Handlers

**Files:**

- `PlanActionHandler.java`
- `ContinueActionHandler.java`
- `RepairFinalActionHandler.java`
- `FailActionHandler.java`

- [ ] Implement Sections 7.7-7.10.
- [ ] Ensure plan is internal only.
- [ ] Ensure fail action routes through final delivery.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 6: Add Action Handler Tests

**Files:**

- Create tests listed in Section 9.

- [ ] Use fake `FinalDeliveryPort`.
- [ ] Use fake `RagRuntimePort`.
- [ ] Use fake `ToolActionOrchestratorPort`.
- [ ] Use fake `UserInteractionManager`.
- [ ] Implement all Section 9 test cases.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=MainActionDispatcherTest,FinalActionHandlerTest,ArtifactActionHandlerTest,AskUserActionHandlerTest,RetrieveRagActionHandlerTest,CallToolActionHandlerTest,PlanContinueActionHandlerTest,RepairFinalAndFailActionHandlerTest" test
```

Expected result:

```text
BUILD SUCCESS
```

### Task 7: Cross-Spec Consistency Scan

- [ ] Run:

```powershell
rg -n "append.*assistant|saveMessage.*ASSISTANT|McpClient|ChatClient|raw tool receipt|raw model output" ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent\service\runtime\handler
```

Expected:

```text
No handler directly appends assistant messages, invokes MCP, invokes ChatClient, or exposes raw outputs.
```

- [ ] Run:

```powershell
rg -n "CALL_TOOL|RETRIEVE_RAG|FINAL|ASK_USER|CREATE_ARTIFACT|UPDATE_ARTIFACT|PLAN|CONTINUE|REPAIR_FINAL|FAIL" ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent\service\runtime\handler
```

Expected:

```text
Every action appears in a handler and dispatcher registration.
```

## 11. Acceptance Checklist

- [ ] Every `MainAgentActionTypeEnumVO` has exactly one handler.
- [ ] Dispatcher validates StateDelta scope before invoking handler.
- [ ] Final text always goes through `FinalDeliveryPort`.
- [ ] Artifact create/update uses `ArtifactManager`.
- [ ] ASK_USER uses `UserInteractionManager`.
- [ ] RETRIEVE_RAG sets `ragWasUsed=true`.
- [ ] CALL_TOOL uses `ToolActionOrchestratorPort`, not MCP directly.
- [ ] PLAN is internal state only.
- [ ] CONTINUE checks loop budget.
- [ ] REPAIR_FINAL is valid only during `REPAIRING_FINAL`.
- [ ] FAIL routes user-safe text through final delivery.
- [ ] User-visible events and developer traces remain separate.
- [ ] Tests pass.

## 12. Worker Split Guidance

If using subagents, split work by non-overlapping file ownership:

- Worker A: handler ports, command/result VOs, registry, dispatcher.
- Worker B: final and artifact handlers.
- Worker C: ask user, RAG, and tool handlers.
- Worker D: plan, continue, repair, and fail handlers.
- Worker E: action handler tests and fake ports.

The integrator must confirm no handler implements real RAG, real MCP, or final guard internals before accepting the phase.

