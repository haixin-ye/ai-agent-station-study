# AutoAgent Phase 8 Tool MCP Permission Approval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement deterministic external tool execution through Runtime-owned `ToolRuntime`, Spring AI MCP clients, typed permission checks, explicit user approval, real receipt capture, and execution-proof verification.

**Architecture:** `MainAgentNode` never mounts or calls MCP tools. It emits `CALL_TOOL` with structured `toolIntent`. Runtime resolves capability and MCP metadata, enforces permission and approval, materializes artifact/evidence references into tool arguments, invokes deterministic `ToolRuntime`, captures real receipts, verifies execution proof, stores tool evidence, and returns to the main loop.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring AI MCP client support, Maven multi-module project, Lombok, Fastjson2, JUnit4, DDD package layout under `yhx.com`.

---

## 0. Execution Rules

- Start this phase only after Phase 6 action handlers and Phase 5 user interaction compile.
- Do not mount MCP tools on `MainAgentNode`.
- Do not implement tool execution through an extra LLM node.
- Do not let `ToolRuntime` create pending input, approval records, run status, or lifecycle transitions.
- Do not allow free-text approval for high-risk tool actions.
- Do not mark tool success without a real captured receipt.
- Do not validate business completion in MVP; `ToolVerifier` verifies execution proof only.
- Do not commit unless the user explicitly asks.

## 1. Source Of Truth

Use the English canonical spec only:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`

Primary spec sections:

- Section 2.4: canonical tool flow
- Section 3.8: tool subflow
- Section 4.6: `ToolInvocationRequest`
- Section 5.15: `CALL_TOOL` action
- Section 5.21: `ToolRuntime` contract
- Section 5.22: tool invocation failure mapping
- Section 6.1: `VerificationResult`
- Section 7.9.1: permission model and tool approval lifecycle
- Section 7.10: tool tables
- Section 9: capability and MCP configuration
- Section 13.11: Phase 8 tasks

## 2. Phase Boundary

### In Scope

- `CapabilityRegistry`
- yml capability loading
- `McpClientRegistry`
- `McpToolRegistry`
- `ToolArgumentMaterializer`
- `PermissionEnforcer`
- tool approval lifecycle
- `ToolRuntime`
- Spring AI MCP adapter boundary
- receipt capture and persistence
- `ToolVerifier` MVP execution-proof validation
- tool evidence creation
- transcript blocks for `TOOL_CALL_REQUEST` and `TOOL_RESULT`
- tests with fake MCP client and fake repositories

### Out Of Scope

- business-result verification beyond call-level proof
- coding-agent file edit policy beyond MCP capability permissions
- frontend approval card implementation
- admin UI for tool configuration
- final response guard internals

## 3. File Map

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/tool/`

Required files:

- `CapabilityRegistry.java`
- `CapabilitySpec.java`
- `McpClientRegistry.java`
- `McpToolRegistry.java`
- `McpToolSpec.java`
- `ToolActionOrchestrator.java`
- `ToolInvocationRequestBuilder.java`
- `ToolArgumentMaterializer.java`
- `PermissionEnforcer.java`
- `ToolApprovalService.java`
- `ToolApprovalKeyGenerator.java`
- `ToolRuntime.java`
- `ToolReceiptCapture.java`
- `ToolVerifier.java`
- `ToolEvidenceConverter.java`
- `ToolTranscriptRecorder.java`
- `ToolFailureMapper.java`

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/tool/port/`

Required files:

- `McpToolInvokerPort.java`
- `McpToolDiscoveryPort.java`

Create under:

- `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/port/`

Required files:

- `SpringAiMcpToolInvokerAdapter.java`
- `SpringAiMcpToolDiscoveryAdapter.java`

Create under:

- `ai-agent-station-study-app/src/main/java/yhx/com/config/`

Required files when missing:

- `AutoAgentToolConfig.java`
- `AutoAgentMcpClientConfig.java`

Create tests under:

- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/tool/`

Required test files:

- `CapabilityRegistryTest.java`
- `PermissionEnforcerTest.java`
- `ToolApprovalServiceTest.java`
- `ToolArgumentMaterializerTest.java`
- `ToolRuntimeTest.java`
- `ToolVerifierTest.java`
- `ToolActionOrchestratorTest.java`

## 4. Capability Registry

### 4.1 `CapabilitySpec`

Fields:

```java
private String capabilityCode;
private String capabilityType;
private String mcpServerCode;
private String toolName;
private RequiredPermissionEnumVO requiredPermission;
private PermissionModeEnumVO permissionMode;
private ApprovalPolicyEnumVO approvalPolicy;
private String riskLevel;
private Boolean destructive;
private ToolArgumentContentModeEnumVO defaultContentMode;
private Boolean enabled;
private Map<String, Object> argumentDefaults;
```

Rules:

- MVP capability type is `TOOL`.
- Capability is external-tool-facing only.
- Internal harness abilities such as RAG, memory, final guard, or context planning are not capability registry entries.

### 4.2 `CapabilityRegistry`

Required methods:

```java
CapabilitySpec findCapability(String capabilityCode);
CapabilitySpec requireCapability(String capabilityCode);
List<CapabilitySpec> listEnabledCapabilities();
```

Rules:

- Load defaults from yml via app config.
- Fail closed when capability is missing or disabled.
- Do not infer a random MCP tool when capability config is ambiguous.

## 5. MCP Registries

### 5.1 `McpClientRegistry`

Required methods:

```java
boolean hasClient(String mcpServerCode);
Object getClientHandle(String mcpServerCode);
```

Rules:

- One MCP server may have one configured Spring AI MCP client instance.
- Support SSE and STDIO config shapes.
- Domain API must not expose Spring AI concrete classes.

### 5.2 `McpToolRegistry`

Required methods:

```java
McpToolSpec findTool(String mcpServerCode, String toolName);
McpToolSpec requireTool(String mcpServerCode, String toolName);
```

`McpToolSpec` fields:

```java
private String mcpServerCode;
private String toolName;
private String description;
private McpTransportTypeEnumVO transportType;
private String inputSchemaRef;
private Map<String, Object> inputSchema;
private RequiredPermissionEnumVO requiredPermission;
private String riskLevel;
private Boolean destructive;
```

Rules:

- Tool metadata may come from yml, discovery, or cached MCP schema.
- If schema is missing, ToolRuntime may still call only when capability config explicitly allows schema-less invocation.

## 6. Permission And Approval

### 6.1 `PermissionEnforcer`

Required method:

```java
PermissionDecisionVO decide(PermissionCheckCommand command);
```

`PermissionCheckCommand` fields:

```java
private String runId;
private String toolCallId;
private CapabilitySpec capability;
private McpToolSpec toolSpec;
private Map<String, Object> materializedArguments;
private String argumentsHash;
private String workspaceScope;
private Boolean destructive;
private ToolApprovalEntity existingApproval;
```

Decision rules:

- `DENY` when capability or tool is disabled/missing.
- `DENY` when workspace write target is outside workspace scope.
- `ASK_USER` when permission mode is `ASK_USER`.
- `ASK_USER` when approval policy is `ASK_USER_BEFORE_EXECUTE`.
- `ASK_USER` when policy is `ASK_USER_ON_RISK` and risk is high.
- `ALLOW` only when permission and approval facts are satisfied.
- destructive actions must never silently run under `ALLOW` unless an existing matching approval is present.

### 6.2 `ToolApprovalKeyGenerator`

Required methods:

```java
String argumentsHash(Map<String, Object> arguments);
String approvalKey(ToolApprovalKeyCommand command);
```

`approval_key` input fields:

```text
runId + toolCallId + capabilityCode + mcpServerCode + toolName + argumentsHash + requiredPermission + workspaceScope + destructive
```

Rules:

- hash must exclude timestamps, random ids, labels, and non-deterministic metadata.
- hash must be stable for semantically identical materialized arguments.

### 6.3 `ToolApprovalService`

Required methods:

```java
ToolApprovalDecisionResult ensureApproval(ToolApprovalDecisionCommand command);
ToolApprovalDecisionResult handleUserDecision(UserAnswerVO answer, ToolApprovalEntity approval);
```

Rules:

- find existing approval by `approval_key` before creating a new one.
- reuse existing `PENDING` approval.
- continue on matching `APPROVED`.
- block on `REJECTED`.
- create `TOOL_APPROVAL` pending input through `UserInteractionManager` for `ASK_USER`.
- approval pending input uses `SINGLE_CHOICE`, `allowFreeText=false`.
- approval options are exactly approve/reject values.
- free text never authorizes execution.

## 7. Tool Argument Materialization

### 7.1 `ToolArgumentMaterializer`

Required method:

```java
ToolArgumentsMaterializationResult materialize(ToolIntentVO intent, CapabilitySpec capability);
```

Rules:

- Resolve `contentSource.type=ARTIFACT` through `IArtifactRepository` and `IPayloadRepository`.
- Resolve `evidenceSource.type=EVIDENCE` through `IEvidenceRepository` and payload summary when needed.
- Preserve inline values when `type=INLINE_VALUE`.
- Enforce content mode:
  - `METADATA_ONLY`
  - `SUMMARY_ONLY`
  - `FULL_TEXT_REQUIRED`
  - `INLINE_VALUE`
- Fail when full artifact text is required but unavailable.
- Do not inline unrelated artifact, memory, trace, prompt, or raw model output.

`ToolArgumentsMaterializationResult` fields:

```java
private Map<String, Object> arguments;
private String argumentsRef;
private List<String> materializedArtifactIds;
private List<String> materializedEvidenceIds;
private FailureCodeEnumVO failureCode;
private String failureMessage;
```

## 8. Tool Invocation Request Builder

`ToolInvocationRequestBuilder.build` must:

1. Validate `ToolIntentVO`.
2. Resolve capability.
3. Resolve MCP server/tool metadata.
4. Materialize tool arguments.
5. Build stable `argumentsHash`.
6. Enforce permission and approval through `PermissionEnforcer` and `ToolApprovalService`.
7. Return `WAITING_USER` if approval is pending.
8. Return `DENIED` if permission is denied.
9. Return `ToolInvocationRequestVO` when ready to invoke.

`ToolInvocationRequestVO` must include:

- run metadata
- tool call id
- tool invocation id
- capability spec
- MCP tool metadata
- materialized arguments ref
- approval id when required
- constraints: must call real tool, max calls, timeout

## 9. ToolRuntime

### 9.1 `ToolRuntime.invoke`

Required method:

```java
ToolInvocationResultVO invoke(ToolInvocationRequestVO request);
```

Procedure:

1. Re-check capability and MCP metadata.
2. Re-check permission and approval as fail-closed guard.
3. Validate final arguments against MCP input schema when available.
4. Call `McpToolInvokerPort.invoke`.
5. Capture raw receipt as payload.
6. Persist `agent_tool_call` status and receipt refs.
7. Return `ToolInvocationResultVO`.

Rules:

- never create pending input.
- never create approval records.
- never write final answers.
- never ask another LLM to simulate tool execution.
- status `SUCCESS` requires real receipt.

### 9.2 `McpToolInvokerPort`

Required method:

```java
McpToolInvokeResult invoke(McpToolInvokeCommand command);
```

`McpToolInvokeCommand` fields:

```java
private String mcpServerCode;
private String toolName;
private Map<String, Object> arguments;
private Long timeoutMs;
```

`McpToolInvokeResult` fields:

```java
private boolean called;
private boolean success;
private Map<String, Object> receipt;
private String errorCode;
private String errorMessage;
private Long latencyMs;
```

## 10. ToolVerifier MVP

`ToolVerifier.verify` must validate execution proof only.

Required checks:

- tool call record exists.
- tool invocation id exists.
- receipt ref exists for success.
- status is not `NOT_CALLED` when success is claimed.
- approval exists when required.
- failed receipt returns failed verification with tool failure code.

It must not:

- judge whether a CSDN post is high quality.
- judge whether business content is correct.
- infer success from MainAgent text.
- call an LLM.

Failure mapping:

| Condition | Failure |
|---|---|
| no call record | `TOOL_NOT_CALLED` |
| no receipt for claimed success | `TOOL_RECEIPT_MISSING` |
| tool returned error | `TOOL_FAILED` |
| missing approval | `TOOL_APPROVAL_REQUIRED` |
| permission denied | `TOOL_PERMISSION_DENIED` |
| schema invalid | `TOOL_SCHEMA_ERROR` |

## 11. Tool Evidence

`ToolEvidenceConverter` must create evidence for:

- successful tool call with receipt summary
- tool failure with safe error summary
- approval rejection
- approval cancellation
- permission denial

Rules:

- evidence summary may include returned URL, id, status, or safe error.
- evidence payload may reference raw receipt payload.
- normal StateView uses evidence summary only.
- credentials, cookies, auth headers, and raw receipt internals must not appear in normal frontend events.

## 12. ToolActionOrchestrator

This is the Phase 6 `ToolActionOrchestratorPort` production implementation.

Procedure:

```text
CALL_TOOL action
  -> create tool_call REQUESTED
  -> build invocation request
  -> if approval pending: return WAITING_USER
  -> if denied: create denial evidence and return CONTINUE_LOOP
  -> invoke ToolRuntime
  -> verify with ToolVerifier
  -> create tool evidence
  -> append transcript TOOL_CALL_REQUEST and TOOL_RESULT
  -> return CONTINUE_LOOP or FAILED
```

Rules:

- `WAITING_USER` must be returned only through `UserInteractionManager`.
- Successful tool result returns to `PREPARING_CONTEXT` for the next `MainAgentNode` loop.
- ToolRuntime does not produce final response.

## 13. Required Tests

### 13.1 `CapabilityRegistryTest`

Required cases:

1. `missing_capability_fails_closed`
2. `disabled_capability_fails_closed`
3. `enabled_capability_resolves_mcp_tool`

### 13.2 `PermissionEnforcerTest`

Required cases:

1. `external_write_requires_approval`
2. `workspace_write_outside_scope_is_denied`
3. `destructive_action_requires_approval`
4. `approved_matching_key_allows_execution`

### 13.3 `ToolApprovalServiceTest`

Required cases:

1. `pending_approval_is_reused_by_approval_key`
2. `tool_approval_uses_single_choice_without_free_text`
3. `free_text_does_not_approve_tool`
4. `approve_option_marks_approval_approved`
5. `reject_option_marks_approval_rejected`

### 13.4 `ToolArgumentMaterializerTest`

Required cases:

1. `artifact_full_text_required_loads_payload`
2. `artifact_metadata_only_does_not_load_body`
3. `evidence_summary_only_loads_summary`
4. `missing_required_artifact_fails`

### 13.5 `ToolRuntimeTest`

Required cases:

1. `success_requires_real_receipt`
2. `missing_approval_returns_needs_user_action`
3. `schema_validation_failure_returns_invalid_intent`
4. `mcp_error_persists_failed_receipt`

### 13.6 `ToolVerifierTest`

Required cases:

1. `no_tool_call_fails_tool_not_called`
2. `success_without_receipt_fails_receipt_missing`
3. `real_receipt_passes_execution_proof`
4. `business_completion_is_not_checked`

### 13.7 `ToolActionOrchestratorTest`

Required cases:

1. `approval_required_returns_waiting_user`
2. `approved_tool_invokes_tool_runtime`
3. `rejected_approval_creates_denial_evidence_without_invocation`
4. `successful_tool_creates_evidence_and_continues_loop`

## 14. Execution Tasks

### Task 1: Add Capability And MCP Registry Types

**Files:**

- Create `CapabilityRegistry`, `CapabilitySpec`, `McpClientRegistry`, `McpToolRegistry`, `McpToolSpec`.

- [x] Implement yml-backed capability lookup.
- [x] Implement fail-closed missing/disabled behavior.
- [x] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 2: Add Permission And Approval Services

**Files:**

- `PermissionEnforcer.java`
- `ToolApprovalService.java`
- `ToolApprovalKeyGenerator.java`

- [x] Implement permission decisions.
- [x] Implement approval idempotency.
- [x] Implement explicit approve/reject pending input creation.
- [x] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 3: Add Argument Materialization And Request Builder

**Files:**

- `ToolArgumentMaterializer.java`
- `ToolInvocationRequestBuilder.java`

- [x] Resolve artifact and evidence references.
- [x] Store arguments payload.
- [x] Build invocation request.
- [x] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 4: Add MCP Invoker Adapter Skeleton

**Files:**

- `SpringAiMcpToolInvokerAdapter.java`
- `SpringAiMcpToolDiscoveryAdapter.java`
- `AutoAgentMcpClientConfig.java`

- [x] Implement port interfaces.
- [x] Use Spring AI MCP client if current dependency wiring is stable.
- [x] Otherwise provide compile-safe skeleton with structured unavailable result.
- [x] Run:

```powershell
mvn -q -pl ai-agent-station-study-infrastructure -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 5: Add ToolRuntime And Verifier

**Files:**

- `ToolRuntime.java`
- `ToolReceiptCapture.java`
- `ToolVerifier.java`
- `ToolFailureMapper.java`

- [x] Invoke MCP through `McpToolInvokerPort`.
- [x] Capture real receipt payload.
- [x] Verify execution proof only.
- [x] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 6: Add Tool Orchestrator And Evidence

**Files:**

- `ToolActionOrchestrator.java`
- `ToolEvidenceConverter.java`
- `ToolTranscriptRecorder.java`

- [x] Wire Phase 6 `ToolActionOrchestratorPort`.
- [x] Create evidence for success, denial, cancellation, and failure.
- [x] Append transcript blocks.
- [x] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 7: Add Tool Tests

**Files:**

- Create all tests listed in Section 13.

- [x] Use fake MCP invoker.
- [x] Use fake repositories.
- [x] Use fake user interaction manager for approval creation.
- [x] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=CapabilityRegistryTest,PermissionEnforcerTest,ToolApprovalServiceTest,ToolArgumentMaterializerTest,ToolRuntimeTest,ToolVerifierTest,ToolActionOrchestratorTest" test
```

Expected result:

```text
BUILD SUCCESS
```

### Task 8: Cross-Spec Consistency Scan

- [x] Run:

```powershell
rg -n "ChatClient|MainAgentNode|createPendingInput|agent_pending_input|agent_tool_approval" ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent\service\tool
```

Expected:

```text
No ChatClient or MainAgentNode references. Pending input and approval writes are allowed only in ToolApprovalService, not ToolRuntime.
```

- [x] Run:

```powershell
rg -n "FREE_TEXT|allowFreeText|SINGLE_CHOICE|APPROVED|REJECTED" ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent\service\tool
```

Expected:

```text
TOOL_APPROVAL uses SINGLE_CHOICE with allowFreeText=false; free text cannot approve execution.
```

## 15. Acceptance Checklist

- [x] MainAgentNode has no MCP tools mounted.
- [x] Capability resolution fails closed.
- [x] MCP server/tool resolution fails closed.
- [x] PermissionEnforcer returns deterministic `ALLOW`, `ASK_USER`, or `DENY`.
- [x] Approval key and arguments hash are stable.
- [x] Existing pending approval is reused.
- [x] High-risk approval uses explicit options only.
- [x] Free text cannot authorize tool execution.
- [x] ToolRuntime cannot create pending input or approval records.
- [x] ToolRuntime invokes real MCP adapter or structured unavailable result.
- [x] Tool success requires real receipt.
- [x] ToolVerifier checks execution proof only.
- [x] Tool results become evidence for the next MainAgentNode loop.
- [x] Tests pass.

## 16. Worker Split Guidance

If using subagents, split work by non-overlapping file ownership:

- Worker A: capability and MCP registries.
- Worker B: permission and approval services.
- Worker C: argument materializer and invocation request builder.
- Worker D: MCP infrastructure adapter skeleton.
- Worker E: ToolRuntime, receipt capture, ToolVerifier.
- Worker F: ToolActionOrchestrator, evidence, transcript.
- Worker G: tool tests and fake MCP invoker.

The integrator must verify that `ToolRuntime` has no dependency on `UserInteractionManager` and no code path treats free text as approval.
