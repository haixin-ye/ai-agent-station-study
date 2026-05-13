# AutoAgent Main-Loop Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the AutoAgent harness from the fixed Node1-4 chain into the main-loop Runtime architecture defined by `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`.

**Architecture:** The implementation is staged. First build Java contracts, value objects, enums, configuration, repositories, and validators. Then add Runtime orchestration, context materialization, RAG, MCP tool execution, final response guarding, API/SSE, and old-harness isolation. The English canonical spec is the implementation source of truth.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring AI, Maven multi-module project, MyBatis, SSE emitter, existing DDD layering under `yhx.com`.

---

## Scope Split

The canonical spec covers multiple subsystems. Do not implement it as one large task. Use this master plan as the backlog and create smaller executable phase plans before coding each phase.

Required detailed phase plans:

1. `Phase 0-1 Contract Skeleton Plan`: package scaffolding, enums, value objects, contract registry, validators, minimal tests.
2. `Phase 2 Persistence Plan`: tables, DAO/PO, repository adapters, payload storage, repository boundary tests.
3. `Phase 3 Prompt And Node Invocation Plan`: prompt assembler, layered prompt builders, node client abstraction, fake clients.
4. `Phase 4 Context Artifact Plan`: candidate preselection, ContextPlanner, budget, artifact resolver/materialization.
5. `Phase 5-6 Runtime Action Plan`: Runtime state machine, pending input, main action handlers.
6. `Phase 7 RAG Plan`: RAG runtime, `ragWasUsed`, evidence, verifier.
7. `Phase 8 Tool MCP Plan`: capability registry, MCP clients, permissions, approvals, receipts, tool verifier.
8. `Phase 9 Final Delivery Plan`: final guard, final repair, final response persistence.
9. `Phase 10 API SSE Plan`: controllers, normal SSE, debug SSE, mock mode.
10. `Phase 11-12 Migration Verification Plan`: old harness isolation, scenario verification, cleanup.

## Global Rules

- [ ] Follow `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`; do not use the Chinese sample as implementation reference.
- [ ] Keep code under `yhx.com`.
- [ ] Preserve DDD boundaries:
  - `domain`: contracts, entities, value objects, repository interfaces, Runtime semantics.
  - `infrastructure`: DAO, PO, mapper, repository implementations, external integration adapters.
  - `trigger`: HTTP/SSE API only.
  - `app`: Spring bean assembly and integration-style tests.
- [ ] Do not patch the old Node1-4 flow into the new design.
- [ ] Do not mount MCP tools directly on `MainAgentNode`.
- [ ] Do not let normal frontend read debug trace, raw payload, prompt, raw model output, or raw tool receipt.
- [ ] Keep tests minimal but real at phase boundaries.
- [ ] Prefer targeted tests before full Maven runs.

## Development Execution Strategy

The numbered Phase 0-12 plans are the complete backlog and verification map, but implementation must not be executed as a rigid one-module-at-a-time checklist. Use a vertical-slice-first strategy: create the overall skeleton, prove a minimal end-to-end flow, then fill local modules behind stable interfaces.

Preferred execution order:

1. Foundation skeleton: package layout, config properties, enums, value objects, contracts, parser, validator, and recovery mapping from Phase 0/1.
2. Minimal runtime slice: run creation, fake node invocation, one direct `FINAL` action, FinalResponseGuard, final persistence stub, and clean normal SSE/API output.
3. Persistence completion: tables, DAOs, repositories, payload storage, trace/audit/event separation from Phase 2.
4. Prompt and node invocation: PromptAssembler, ContractRegistry integration, NodeInvocationPipeline, fake clients, bounded repair from Phase 3.
5. Context/artifact/memory: candidate preselection, ContextPlanner wrapper, budget policy, artifact resolver, MainAgentStateView materialization from Phase 4.
6. Runtime completion: lifecycle, pending input, action dispatcher, loop/recovery counters, handler wiring from Phase 5 and Phase 6.
7. Capability modules: RAG runtime/verifier from Phase 7, then MCP/tool/permission/approval from Phase 8.
8. Final delivery hardening: final guard, final repair, safe fallback, final response persistence from Phase 9.
9. API/frontend/debug: chat APIs, normal SSE, debug APIs/SSE, mock scenarios from Phase 10.
10. Migration boundary: old Node1-4 isolation and route cleanup from Phase 11.
11. Verification closeout: MVP scenarios, safety-property checks, verification report, known-gaps backlog from Phase 12.

This order may still reference the individual phase documents for exact classes, methods, contracts, and tests. If a vertical slice needs a later-phase interface early, create the interface and a fake/stub implementation first, then replace the internals when that phase is developed.

## Git Checkpoint Strategy

Use a dedicated feature branch for this redesign work. Do not mix unrelated package-renaming, local runtime artifacts, or experimental files into harness commits.

Checkpoint rules:

- [ ] Create or switch to a dedicated branch before implementation starts.
- [ ] Make a Git commit after each coherent vertical slice, not after every tiny file.
- [ ] Each checkpoint must compile or must explicitly be marked as a temporary WIP checkpoint with the reason.
- [ ] After every checkpoint, update `progress.md` with branch name, commit hash, completed slice, verification command, and result.
- [ ] If a rollback is needed, use the recorded checkpoint information in `progress.md` to choose the target commit.
- [ ] Never commit `.m2/`, `.m2repo/`, `data/log/`, app runtime `data/`, generated node traces, or local tool caches.

Suggested checkpoint labels:

| Checkpoint | Meaning |
| --- | --- |
| `checkpoint-01-foundation-contracts` | Phase 0/1 foundation compiles and contract tests pass. |
| `checkpoint-02-direct-runtime-slice` | Minimal direct-answer Runtime path works with fake node client. |
| `checkpoint-03-persistence-boundary` | Repository and payload storage boundary is implemented. |
| `checkpoint-04-node-pipeline-context` | Prompt pipeline and context materialization work together. |
| `checkpoint-05-runtime-actions` | Runtime lifecycle, pending input, and action handlers are wired. |
| `checkpoint-06-rag-tool-capabilities` | RAG and MCP/tool paths work with verification and approval. |
| `checkpoint-07-final-api-debug` | Final guard, API/SSE, debug boundary, and old harness isolation are complete. |
| `checkpoint-08-mvp-verification` | Phase 12 scenario and safety-property verification is complete. |

## Phase 0: Scaffolding And Configuration

**Purpose:** Make the new package structure compile without changing old behavior.

**Primary files to create or modify:**

- Create package directories under `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/`.
- Create package directories under `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/`.
- Create controller package targets under `ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/`.
- Create app config classes under `ai-agent-station-study-app/src/main/java/yhx/com/config/`.

**Tasks:**

- [ ] Create domain package skeletons from spec section 8.2.
- [ ] Create infrastructure package skeletons from spec section 8.3.
- [ ] Create trigger controller skeleton names from spec section 8.5.
- [ ] Create app configuration skeleton names from spec section 8.4.
- [ ] Add typed configuration property classes for `auto-agent.runtime`, `auto-agent.context`, `auto-agent.nodes`, `auto-agent.rag`, `auto-agent.mcp`, and `auto-agent.capabilities`.
- [ ] Include fail-closed debug switches: `debug-api-enabled`, `debug-sse-enabled`, `debug-payload-preview-enabled`, `debug-payload-preview-max-chars`.
- [ ] Compile affected modules.

**Acceptance:**

- [ ] Project compiles.
- [ ] Old AutoAgent behavior is not changed.
- [ ] New package layout matches the canonical spec.

## Phase 1: Domain Model And Contract Layer

**Purpose:** Make Java contracts the source of truth before Runtime orchestration exists.

**Primary domain areas:**

- `model/entity`
- `model/valobj`
- `model/valobj/enums`
- `service/contract`

**Tasks:**

- [ ] Add enum classes from spec section 13.4:
  `RunStatus`, `RuntimePhase`, `MainAgentActionType`, `StateDeltaField`, `ContextLevel`, `EvidenceType`, `PendingInputType`, `ToolCallStatus`, `ToolInvocationStatus`, `ToolApprovalStatus`, `UserApprovalDecision`, `VerificationStatus`, `FinalGuardFailureCode`, `FailureCode`, `RecoveryAction`, `TranscriptBlockType`, `PermissionMode`, `RequiredPermission`, `ApprovalPolicy`, `PermissionDecisionType`, `McpTransportType`, `ToolArgumentSourceType`, `ToolArgumentContentMode`.
- [ ] Add value objects from spec section 13.4:
  `AgentState`, `MainAgentStateView`, `ContextPlannerInput`, `ContextPlannerOutput`, `MainAgentAction`, `StateDelta`, `ToolInvocationRequest`, `ToolInvocationResult`, `RunTranscriptBlock`, `PermissionDecision`, `ToolArgumentSource`, `AskUserRequest`, `ContinuationCheckpoint`, `UserAnswer`, `RagVerifierInput`, `VerificationResult`, `FinalResponseGuardInput`, `FinalResponseGuardResult`, `FinalResponse`, `DeveloperTrace`, `AuditRecord`.
- [ ] Add entities from spec section 8.2:
  `AgentRunEntity`, `AgentSessionEntity`, `AgentMessageEntity`, `AgentArtifactEntity`, `AgentEvidenceEntity`, `AgentPendingInputEntity`, `AgentMemoryEntity`, `ToolCallEntity`, `ToolApprovalEntity`, `RagQueryEntity`, `AgentRunTraceEntity`, `AgentRunAuditEntity`.
- [ ] Implement `ContractRegistry`.
- [ ] Implement `RawOutputParser`.
- [ ] Implement `ContractValidator`.
- [ ] Implement `ContractRepairPolicy` interfaces and retry counters.
- [ ] Add `MainAgentActionContractTest` for action enum coverage, strict `StateDelta` scope, lifecycle field rejection, and malformed JSON rejection.
- [ ] Add `ContextPlannerContractTest` for `READY`, `NEEDS_USER_CLARIFICATION`, `CONTEXT_OVER_BUDGET`, invalid context level, and raw payload rejection.
- [ ] Compile and run targeted contract tests.

**Acceptance:**

- [ ] Contract tests pass.
- [ ] Every action has explicit `StateDelta` allowed fields.
- [ ] Runtime-owned fields are rejected in node output.
- [ ] No LLM or database integration is required yet.

## Phase 2: Persistence And Repository Adapters

**Purpose:** Create storage boundaries required by Runtime without giving nodes database access.

**Tasks:**

- [ ] Add database migration or SQL initialization for all tables in spec section 7.
- [ ] Add infrastructure DAO interfaces:
  `IAgentRunDao`, `IAgentMessageDao`, `IAgentMemoryDao`, `IAgentArtifactDao`, `IAgentEvidenceDao`, `IAgentPendingInputDao`, `IAgentToolCallDao`, `IAgentToolApprovalDao`, `IAgentRagDao`, `IAgentRunEventDao`, `IAgentRunTraceDao`, `IAgentRunAuditDao`, `IAgentPayloadDao`, `IAgentNodePromptDao`.
- [ ] Add PO classes matching table fields.
- [ ] Add domain repository interfaces from spec section 7.14.
- [ ] Add infrastructure repository implementations.
- [ ] Implement payload storage for DB-backed text/JSON payloads first.
- [ ] Add repository boundary tests for user-visible message isolation and payload references.

**Acceptance:**

- [ ] Repository interfaces live in domain.
- [ ] Implementations live in infrastructure.
- [ ] Raw payloads are referenced, not inlined into messages or events.

## Phase 3: Prompt Assembly And Node Invocation

**Purpose:** Ensure every LLM node uses a single invocation path and Java-owned contracts.

**Tasks:**

- [ ] Implement `PromptAssembler`.
- [ ] Implement prompt layer builders from spec section 13.6.
- [ ] Implement `NodeInvocationPipeline`.
- [ ] Implement `NodeClient` abstraction around Spring AI `ChatClient`.
- [ ] Implement fake node clients for tests.
- [ ] Wire `agent_node_prompt` role/behavior content into the prompt envelope.
- [ ] Add prompt assembly tests proving database prompt cannot override Java contract.

**Acceptance:**

- [ ] ContextPlanner, MainAgent, RagVerifier, final repair, and contract repair all use the same invocation pipeline.
- [ ] Prompt text from database cannot override output contract.

## Phase 4: Context, Memory, Artifact, And Evidence Runtime

**Purpose:** Make `MainAgentStateView` reliable before full execution.

**Tasks:**

- [ ] Implement Java candidate preselection for recent messages, summaries, artifacts, memories, and evidence.
- [ ] Implement `ContextPlannerNode` invocation wrapper.
- [ ] Implement `ContextBudgetManager`.
- [ ] Implement `ArtifactResolver`.
- [ ] Implement `ArtifactContextPolicy`.
- [ ] Implement context materialization from `ContextPlannerOutput` to `MainAgentStateView`.
- [ ] Implement artifact creation, versioning, aliases, and relations.
- [ ] Implement MVP memory summary and recall stubs.
- [ ] Add `ContextMaterializationTest` and `ArtifactContextPolicyTest`.

**Acceptance:**

- [ ] Publish-like tasks load artifact metadata.
- [ ] Rewrite-like tasks load full text or chunked content according to budget.
- [ ] Raw traces/prompts/model outputs never enter `MainAgentStateView`.

## Phase 5: Runtime State Machine And Pending Input

**Purpose:** Implement deterministic Java lifecycle control.

**Tasks:**

- [ ] Implement `RuntimeStateMachine`.
- [ ] Implement `AutoAgentRuntime`.
- [ ] Implement run/message creation.
- [ ] Implement lifecycle phases and loop limits.
- [ ] Implement recovery routing by `FailureCode`.
- [ ] Implement `UserInteractionManager`.
- [ ] Implement `UserReplyProcessor`.
- [ ] Implement `PendingInputContinuationDispatcher`.
- [ ] Implement typed transcript append at durable boundaries.
- [ ] Add `RuntimeStateMachineTest` and `PendingInputUserAnswerTest`.

**Acceptance:**

- [ ] Runtime owns all status/phase changes.
- [ ] Pending input resumes the same run.
- [ ] Free text becomes `FREE_TEXT` `UserAnswer` without LLM interpretation.

## Phase 6: MainAgent Action Handlers

**Purpose:** Implement action handling incrementally.

**Implementation order:**

1. `FINAL`
2. `CREATE_ARTIFACT`
3. `UPDATE_ARTIFACT`
4. `ASK_USER`
5. `RETRIEVE_RAG`
6. `CALL_TOOL`
7. `PLAN`
8. `CONTINUE`
9. `REPAIR_FINAL`
10. `FAIL`

**Acceptance:**

- [ ] Unsupported fields are rejected.
- [ ] Final answer path always goes through guard.
- [ ] Action handlers write developer trace and user-visible events separately.

## Phase 7: RAG Runtime And Verification

**Purpose:** Add explicit RAG retrieval and evidence-based grounding.

**Tasks:**

- [ ] Implement `RagRuntime`.
- [ ] Persist RAG query and hits.
- [ ] Set and persist `ragWasUsed` on accepted `RETRIEVE_RAG`.
- [ ] Convert hits to evidence.
- [ ] Build `RagVerifierInput`.
- [ ] Implement `RagVerifier`.
- [ ] Implement RAG recovery handling.
- [ ] Add `RagExecutionAndVerificationTest`.

**Acceptance:**

- [ ] `RagVerifier` triggers only from `ragWasUsed`.
- [ ] No-hit and unsupported claim paths are deterministic.

## Phase 8: Tool Runtime, MCP, Permission, Approval

**Purpose:** Implement external tool use without polluting `MainAgentNode`.

**Tasks:**

- [ ] Implement `CapabilityRegistry`.
- [ ] Load capability defaults from yml.
- [ ] Implement `McpClientRegistry`.
- [ ] Implement `McpToolRegistry`.
- [ ] Implement `ToolArgumentMaterializer`.
- [ ] Implement `PermissionEnforcer`.
- [ ] Implement tool approval lifecycle.
- [ ] Implement `ToolRuntime`.
- [ ] Implement receipt capture and tool evidence.
- [ ] Implement MVP `ToolVerifier`.
- [ ] Add `ToolRuntimeAndVerificationTest`.

**Acceptance:**

- [ ] `MainAgentNode` has no MCP tools mounted.
- [ ] High-risk tool execution requires explicit option approval.
- [ ] Tool success requires real receipt.

## Phase 9: Final Delivery And Repair

**Purpose:** Make all user-visible output clean.

**Tasks:**

- [ ] Implement final guard pipeline.
- [ ] Implement `FinalResponseGuardInput`, `FinalResponseGuardResult`, and `FinalResponse`.
- [ ] Implement final repair invocation.
- [ ] Persist guard results as developer trace plus payload refs.
- [ ] Route `FINAL`, `REPAIR_FINAL`, `CREATE_ARTIFACT`, `UPDATE_ARTIFACT`, and `FAIL` through final delivery.
- [ ] Add `FinalResponseGuardTest`.

**Acceptance:**

- [ ] No normal assistant message is created from trace, verifier output, raw model output, tool receipt, or runtime summary.

## Phase 10: API, SSE, Debug API, Mock Mode

**Purpose:** Expose clean frontend boundaries.

**Tasks:**

- [ ] Implement `AgentChatController`.
- [ ] Implement `AgentRunController`.
- [ ] Implement `AgentEventController`.
- [ ] Implement `AgentArtifactController`.
- [ ] Implement `AgentDebugController`.
- [ ] Implement `AgentMockController`.
- [ ] Implement normal SSE.
- [ ] Implement separate debug SSE.
- [ ] Implement mock scenarios from spec section 12.5.
- [ ] Add API/SSE tests.

**Acceptance:**

- [ ] Normal events contain no raw internal payload.
- [ ] Debug data is available only through debug endpoints.
- [ ] Frontend can test progress and pending input without real LLM/tool calls.

## Phase 11: Old Harness Isolation

**Purpose:** Remove old behavior from normal execution after new Runtime works.

**Tasks:**

- [ ] Identify old Node1-4 classes and old trace payload paths.
- [ ] Remove old route from normal AutoAgent execution.
- [ ] Keep old classes only behind explicit migration/comparison path if needed.
- [ ] Delete dead prompt/parser code that conflicts with new contracts.
- [ ] Update documentation references.

**Acceptance:**

- [ ] No normal API path calls old Node1-4 flow.
- [ ] Old trace output cannot become final answer.

## Phase 12: MVP Verification

**Purpose:** Prove MVP behavior with critical tests and mock scenarios.

**Tasks:**

- [ ] Run required targeted tests.
- [ ] Run app module tests if feasible.
- [ ] Manually execute mock scenarios.
- [ ] Manually execute fake-client runtime scenarios:
  direct answer, artifact creation, artifact update, RAG answer, tool publish with approval, ambiguous artifact clarification, final guard repair, context over budget.
- [ ] Record known gaps and backlog mapping.

**Acceptance:**

- [ ] Critical safety properties in spec section 12.8 are demonstrated.
- [ ] User-visible output is clean.
- [ ] Debug data is isolated.

## Immediate Next Detailed Plan

The next document should be:

`docs/superpowers/plans/2026-05-12-auto-agent-phase-0-1-contract-skeleton.md`

It should include exact Java file paths, enum definitions, value object field lists, minimal validator behavior, and targeted contract tests.
