# AutoAgent Phase 5 Runtime Pending Input Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement deterministic Java Runtime lifecycle control, run loop orchestration, recovery counters, typed transcript boundaries, and unified pending-input pause/resume handling.

**Architecture:** `Runtime` is Java-only orchestration. It creates a `run`, appends the user message, prepares context, calls ContextPlanner and MainAgent through existing services, validates action output, and delegates action handling. If any component needs user input, Runtime creates a pending input through `UserInteractionManager`, pauses the same run in `WAITING_USER`, and later resumes it from a stored continuation checkpoint. No LLM node may write lifecycle state directly.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Maven multi-module project, Lombok, Fastjson2, JUnit4, DDD package layout under `yhx.com`.

---

## 0. Execution Rules

- Start this phase only after Phase 0/1 contracts compile.
- Phase 2 repositories and Phase 4 context services should exist before production wiring.
- Do not implement full action handlers here; Phase 6 owns action-specific business handling.
- Do not implement actual RAG retrieval, MCP tool invocation, or final guard internals here.
- Do not call an LLM from `Runtime`, `RuntimeStateMachine`, `UserInteractionManager`, or `UserReplyProcessor`.
- Do not let any node output set `runStatus`, `runtimePhase`, `loopIndex`, trace, audit, tool receipt, or verifier result.
- Do not create a new run when answering pending input; pending input resumes the same run.
- Do not commit unless the user explicitly asks.

## 1. Source Of Truth

Use the English canonical spec only:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`

Primary spec sections:

- Section 2.3: Runtime flow
- Section 2.6: data ownership
- Section 3.1-3.4: Runtime terms/status/phase/loop
- Section 3.6: ContextPlannerStatus handling
- Section 3.7: pending input and user reply handling
- Section 3.8: tool subflow pause point
- Section 3.9: recovery limits
- Section 4.7: `AskUserRequest` and `UserAnswer`
- Section 7.4: run and transcript persistence
- Section 7.9: pending input persistence
- Section 12.3.3: runtime state machine tests
- Section 13.8: Phase 5 implementation tasks

## 2. Phase Boundary

### In Scope

- `AutoAgentRuntimeService`
- `RuntimeStateMachine`
- `RuntimeLoopPolicy`
- `RuntimeRecoveryCounters`
- `RuntimePhaseGuard`
- run creation and user message creation orchestration
- context preparation loop integration points
- main node invocation integration point
- action validation integration point
- action dispatcher interface and stub dispatcher
- pending input creation, answer normalization, and continuation dispatch
- typed transcript append at durable boundaries
- user-visible event and developer trace write interfaces
- safe failure creation for lifecycle-level failures
- tests with fake repositories and fake pipeline services

### Out Of Scope

- Phase 6 action business logic for all `MainAgentAction` actions
- real `RagRuntime`
- real `ToolRuntime`
- real `FinalResponseGuard`
- real SSE emitter controllers
- frontend APIs
- old Node1-4 removal

## 3. Runtime File Map

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/`

Required files:

- `AutoAgentRuntimeService.java`
- `RuntimeStateMachine.java`
- `RuntimeLoopPolicy.java`
- `RuntimePhaseGuard.java`
- `RuntimeRecoveryCounters.java`
- `RuntimeExecutionContext.java`
- `RuntimeStartCommand.java`
- `RuntimeResumeCommand.java`
- `RuntimeStepResult.java`
- `RuntimeStepStatusEnumVO.java`
- `RuntimeFailureFactory.java`
- `RuntimeComponentPorts.java`
- `MainActionDispatcher.java`
- `MainActionHandler.java`
- `MainActionHandlerResult.java`
- `MainActionHandlerStatusEnumVO.java`
- `NoopMainActionDispatcher.java`
- `RunEventPublisher.java`
- `DeveloperTraceRecorder.java`
- `RunTranscriptRecorder.java`

## 4. Pending Input File Map

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/interaction/`

Required files:

- `UserInteractionManager.java`
- `PendingInputManager.java`
- `UserReplyProcessor.java`
- `PendingInputContinuationDispatcher.java`
- `PendingInputContinuationHandler.java`
- `PendingInputCreateCommand.java`
- `PendingInputCreateResult.java`
- `UserInputResolveCommand.java`
- `UserInputResolveResult.java`
- `ContinuationCheckpointVO.java`
- `ContextPlannerPendingInputHandler.java`
- `MainAgentPendingInputHandler.java`
- `ToolApprovalPendingInputHandler.java`
- `RagPendingInputHandler.java`
- `FinalRepairPendingInputHandler.java`

If `ContextPlannerPendingInputHandler` already exists from Phase 4, keep it in one package and update imports consistently. Do not create duplicate classes.

## 5. Runtime Value Objects

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/`

Required files when missing:

- `RunMetaVO.java`
- `UserInputVO.java`
- `RuntimeRecoveryStateVO.java`
- `RuntimeContinuationVO.java`
- `RuntimeSafeFailureVO.java`
- `UserVisibleEventVO.java`
- `PendingInputViewVO.java`

### 5.1 `RuntimeExecutionContext`

Fields:

```java
private String runId;
private String sessionId;
private String userId;
private String agentId;
private String userMessageId;
private String userInput;
private RunStatusEnumVO runStatus;
private RuntimePhaseEnumVO currentPhase;
private Integer loopIndex;
private Integer maxLoop;
private RuntimeRecoveryCounters recoveryCounters;
private MainAgentStateViewVO lastStateView;
private MainAgentActionVO lastAction;
private Map<String, Object> runtimeFacts;
```

Rules:

- This object is internal to Runtime.
- It is not passed directly to LLM nodes.
- It can hold references to state, but large payloads remain in repositories.

### 5.2 `RuntimeStepResult`

Fields:

```java
private RuntimeStepStatusEnumVO status;
private RuntimePhaseEnumVO nextPhase;
private RunStatusEnumVO nextRunStatus;
private MainAgentActionVO action;
private MainActionHandlerResult actionResult;
private AskUserRequestVO askUserRequest;
private RuntimeSafeFailureVO safeFailure;
private String message;
```

`RuntimeStepStatusEnumVO` constants:

```text
CONTINUE, WAITING_USER, COMPLETED, FAILED, CANCELLED
```

### 5.3 `RuntimeRecoveryCounters`

Fields:

```java
private Integer loopCount;
private Integer contractRepairCount;
private Integer finalRepairCount;
private Integer toolRetryCount;
private Integer ragRetryCount;
private Integer contextCompressionCount;
```

Default limits:

```text
maxLoop = 6
maxContractRepair = 1
maxFinalRepair = 2
maxToolRetry = 1
maxRagRetry = 2
maxContextCompression = 2
```

## 6. Runtime State Machine

### 6.1 `RuntimeStateMachine`

Required methods:

```java
RuntimePhaseEnumVO nextAfterStart();
RuntimePhaseEnumVO nextAfterContextPrepared(ContextPlannerOutputVO plannerOutput);
RuntimePhaseEnumVO nextAfterMainAction(MainAgentActionTypeEnumVO actionType);
boolean canEnter(RuntimePhaseEnumVO from, RuntimePhaseEnumVO to);
boolean isTerminalRunStatus(RunStatusEnumVO status);
boolean isPausedRunStatus(RunStatusEnumVO status);
```

### 6.2 Allowed Phase Transitions

Allow only these MVP transitions:

```text
CREATED -> PREPARING_CONTEXT
PREPARING_CONTEXT -> PLANNING_CONTEXT
PREPARING_CONTEXT -> BUILDING_STATE_VIEW
PLANNING_CONTEXT -> BUILDING_STATE_VIEW
PLANNING_CONTEXT -> WAITING_USER
PLANNING_CONTEXT -> FAILED
BUILDING_STATE_VIEW -> CALLING_MAIN_NODE
CALLING_MAIN_NODE -> VALIDATING_ACTION
VALIDATING_ACTION -> HANDLING_ACTION
VALIDATING_ACTION -> REPAIRING_CONTRACT
VALIDATING_ACTION -> FAILED
REPAIRING_CONTRACT -> VALIDATING_ACTION
REPAIRING_CONTRACT -> FAILED
HANDLING_ACTION -> PREPARING_CONTEXT
HANDLING_ACTION -> EXECUTING_RAG
HANDLING_ACTION -> PREPARING_TOOL
HANDLING_ACTION -> VERIFYING_FINAL
HANDLING_ACTION -> WAITING_USER
HANDLING_ACTION -> COMPLETED
HANDLING_ACTION -> FAILED
EXECUTING_RAG -> PREPARING_CONTEXT
PREPARING_TOOL -> WAITING_USER
PREPARING_TOOL -> INVOKING_TOOL_RUNTIME
PREPARING_TOOL -> FAILED
INVOKING_TOOL_RUNTIME -> VERIFYING_TOOL
VERIFYING_TOOL -> PREPARING_CONTEXT
VERIFYING_TOOL -> FAILED
VERIFYING_FINAL -> REPAIRING_FINAL
VERIFYING_FINAL -> COMPLETED
VERIFYING_FINAL -> FAILED
REPAIRING_FINAL -> VERIFYING_FINAL
WAITING_USER -> RESOLVING_USER_ANSWER
RESOLVING_USER_ANSWER -> PREPARING_CONTEXT
RESOLVING_USER_ANSWER -> PREPARING_TOOL
RESOLVING_USER_ANSWER -> CALLING_MAIN_NODE
RESOLVING_USER_ANSWER -> CANCELLED
RESOLVING_USER_ANSWER -> FAILED
```

Terminal phases:

```text
COMPLETED, FAILED, CANCELLED
```

### 6.3 Phase Guard

`RuntimePhaseGuard` must:

- reject illegal phase transitions
- record a developer trace when an illegal transition is attempted
- force safe failure when illegal transition cannot be recovered
- never silently skip from `WAITING_USER` to `CALLING_MAIN_NODE` without `RESOLVING_USER_ANSWER`

## 7. Runtime Loop

### 7.1 `AutoAgentRuntimeService.start`

Signature:

```java
RuntimeStepResult start(RuntimeStartCommand command);
```

`RuntimeStartCommand` fields:

```java
private String sessionId;
private String userId;
private String agentId;
private String userInput;
private String inputType;
private Map<String, Object> requestMetadata;
```

Start procedure:

1. Create or load session through `IConversationRepository`.
2. Save user message in `agent_message`.
3. Create run in `agent_run` with status `CREATED`.
4. Append transcript block `USER_MESSAGE`.
5. Emit user-visible event `RECEIVED`.
6. Set run status `RUNNING`.
7. Enter phase `PREPARING_CONTEXT`.
8. Call `runLoop(context)`.

### 7.2 `AutoAgentRuntimeService.runLoop`

Procedure:

```text
while run status is RUNNING:
  if loopCount >= maxLoop:
    create safe failure with MAX_LOOP_REACHED
    return FAILED

  enter PREPARING_CONTEXT
  build ContextCandidateBundle

  enter PLANNING_CONTEXT when context planner is enabled
  invoke ContextPlannerNodeService
  handle ContextPlannerStatus

  if planner asks user:
    create pending input
    return WAITING_USER

  enter BUILDING_STATE_VIEW
  materialize MainAgentStateView

  enter CALLING_MAIN_NODE
  invoke NodeInvocationPipeline for MAIN_AGENT

  enter VALIDATING_ACTION
  validate MainAgentAction

  enter HANDLING_ACTION
  dispatch action through MainActionDispatcher

  route result:
    CONTINUE -> increment loop and PREPARING_CONTEXT
    WAITING_USER -> return WAITING_USER
    COMPLETED -> return COMPLETED
    FAILED -> return FAILED
    CANCELLED -> return CANCELLED
```

### 7.3 Loop Rules

- Increment `loopCount` only after one completed MainAgent action dispatch.
- Do not increment loop count while paused in `WAITING_USER`.
- Contract repair attempts do not count as a new loop.
- Final repair attempts do not count as a new loop.
- Resuming from user input continues the same run.

## 8. Main Action Dispatcher Boundary

Phase 5 defines the boundary; Phase 6 implements detailed handlers.

### 8.1 `MainActionDispatcher`

Interface:

```java
MainActionHandlerResult dispatch(RuntimeExecutionContext context, MainAgentActionVO action);
```

### 8.2 `MainActionHandlerResult`

Fields:

```java
private MainActionHandlerStatusEnumVO status;
private RuntimePhaseEnumVO nextPhase;
private AskUserRequestVO askUserRequest;
private FinalAnswerCandidateVO finalAnswerCandidate;
private RuntimeSafeFailureVO safeFailure;
private List<String> createdEvidenceIds;
private List<String> createdArtifactIds;
private String message;
```

`MainActionHandlerStatusEnumVO` constants:

```text
CONTINUE_LOOP, WAITING_USER, COMPLETED, FAILED, CANCELLED
```

### 8.3 `NoopMainActionDispatcher`

Purpose:

- Allows Runtime tests before Phase 6 action handlers exist.

Behavior:

- `FINAL` returns `COMPLETED` only when test mode is enabled.
- `ASK_USER` returns `WAITING_USER` with the provided `askUserRequest`.
- `CONTINUE` returns `CONTINUE_LOOP`.
- Other actions return `FAILED` with message `Action handler not implemented in Phase 5`.

Production Runtime must replace this dispatcher in Phase 6.

## 9. Pending Input System

### 9.1 `UserInteractionManager`

Required methods:

```java
PendingInputCreateResult createPendingInput(PendingInputCreateCommand command);
UserInputResolveResult resolveUserInput(UserInputResolveCommand command);
```

Responsibilities:

- validate `AskUserRequestVO`
- create continuation checkpoint
- call `PendingInputManager` to persist pending input
- emit user-visible event through `RunEventPublisher`
- normalize user answer through `UserReplyProcessor`
- dispatch normalized answer through `PendingInputContinuationDispatcher`

### 9.2 `PendingInputCreateCommand`

Fields:

```java
private String runId;
private String sessionId;
private String sourceComponent;
private AskUserRequestVO askUserRequest;
private ContinuationCheckpointVO continuation;
private LocalDateTime expiresAt;
```

### 9.3 `ContinuationCheckpointVO`

Fields:

```java
private String handler;
private RuntimePhaseEnumVO resumePhase;
private String sourceComponent;
private String relatedRunId;
private Integer relatedLoopIndex;
private String expectedAnswerValueType;
private Map<String, Object> payload;
```

Rules:

- Runtime creates this object.
- Nodes must not create `pendingId` or continuation ids.
- Continuation payload must contain ids and references, not raw large payloads.

### 9.4 `PendingInputManager`

Required methods:

```java
String create(PendingInputCreateCommand command);
AgentPendingInputEntity findActiveByRunId(String runId);
AgentPendingInputEntity findByPendingId(String pendingId);
void markAnswered(String pendingId, String userAnswerRef);
void markCancelled(String pendingId);
void markExpired(String pendingId);
```

Dependencies:

- `IPendingInputRepository`
- `IPayloadRepository`

Persistence rules:

- Store options as payload.
- Store answer schema as payload when present.
- Store continuation checkpoint as payload.
- Store submitted `UserAnswer` as payload.

## 10. User Reply Processing

### 10.1 `UserReplyProcessor`

Required method:

```java
UserAnswerVO process(AgentPendingInputEntity pendingInput, UserInputResolveCommand command);
```

`UserInputResolveCommand` fields:

```java
private String runId;
private String pendingId;
private String selectedOptionId;
private String freeText;
private Boolean cancelled;
private Map<String, Object> requestMetadata;
```

Rules:

- If `cancelled=true`, return `status=CANCELLED`, `answerType=CANCEL`.
- If `selectedOptionId` is present, find exact stored option id and return `answerType=OPTION` with stored `option.value`.
- If selected option id is unknown, return `status=FAILED`.
- If free text is present and pending input allows free text, return `answerType=FREE_TEXT`.
- If free text is present but `inputMode=SINGLE_CHOICE`, return `status=FAILED`.
- Do not parse option labels.
- Do not use fuzzy matching.
- Do not call LLM.
- Do not decide semantic adequacy.

### 10.2 Tool Approval Rule

For `pendingType=TOOL_APPROVAL`:

- accepted input mode is `SINGLE_CHOICE`
- free text is invalid
- only option values with decision `APPROVED` or `REJECTED` are valid
- typing `ok`, `sure`, or similar text must not approve execution

## 11. Continuation Dispatch

### 11.1 `PendingInputContinuationDispatcher`

Required method:

```java
RuntimeStepResult dispatch(UserAnswerVO answer, ContinuationCheckpointVO checkpoint, RuntimeExecutionContext context);
```

Rules:

- Dispatch by `checkpoint.handler`.
- Unknown handler returns safe failure.
- Handler receives normalized `UserAnswerVO`.
- Handler decides whether to resume context preparation, tool preparation, main node call, cancellation, or safe failure.

### 11.2 Required Handlers

`ContextPlannerPendingInputHandler`:

- option answer with `resolutionType=SELECT_CONTEXT` sets forced context selection and resumes `PREPARING_CONTEXT`
- free text stores answer in runtime facts and resumes `PREPARING_CONTEXT`
- cancel returns `CANCELLED`

`MainAgentPendingInputHandler`:

- option or free text becomes `pendingAction` evidence/fact
- resumes `PREPARING_CONTEXT`
- cancel returns `CANCELLED`

`ToolApprovalPendingInputHandler`:

- approve resumes `PREPARING_TOOL`
- reject records denial fact and resumes `PREPARING_CONTEXT` or fails safely according to tool flow
- free text is invalid and cannot authorize execution
- cancel returns `CANCELLED`

`RagPendingInputHandler`:

- resumes `PREPARING_CONTEXT` with user-provided clarification
- cancel returns `CANCELLED`

`FinalRepairPendingInputHandler`:

- resumes `REPAIRING_FINAL` with user format clarification
- cancel returns safe failure

## 12. Transcript And Trace Boundaries

### 12.1 `RunTranscriptRecorder`

Required methods:

```java
void appendUserMessage(String runId, String sessionId, String messageId, String payloadRef);
void appendContextPlan(String runId, Integer loopIndex, ContextPlannerOutputVO output, String payloadRef);
void appendStateViewSummary(String runId, Integer loopIndex, MainAgentStateViewVO stateView, String payloadRef);
void appendAssistantAction(String runId, Integer loopIndex, MainAgentActionVO action, String payloadRef);
void appendUserReply(String runId, Integer loopIndex, UserAnswerVO answer, String payloadRef);
void appendError(String runId, Integer loopIndex, FailureCodeEnumVO failureCode, String summary, String payloadRef);
```

Rules:

- Transcript is for replay and compaction safety.
- Transcript is not the normal frontend message table.
- Tool request/result pairs must stay adjacent or be preserved by compaction metadata in later phases.
- Pending input continuation must be recorded before pausing and user reply must be recorded before resuming.

### 12.2 `RunEventPublisher`

Required methods:

```java
void received(String runId, String summary);
void phase(String runId, String title, String summary);
void askingUser(String runId, String pendingInputId, String question);
void completed(String runId, String finalMessageId);
void failed(String runId, String userSafeSummary);
void cancelled(String runId, String summary);
```

Rules:

- Emits only user-visible safe events.
- Does not include raw model output, prompt, trace, or tool receipt.

### 12.3 `DeveloperTraceRecorder`

Required methods:

```java
void phaseStarted(String runId, Integer loopIndex, RuntimePhaseEnumVO phase);
void phaseCompleted(String runId, Integer loopIndex, RuntimePhaseEnumVO phase);
void nodeInvocation(String runId, Integer loopIndex, String componentCode, String payloadRef);
void actionParsed(String runId, Integer loopIndex, MainAgentActionTypeEnumVO actionType, String payloadRef);
void contractFailure(String runId, Integer loopIndex, FailureCodeEnumVO failureCode, String payloadRef);
void error(String runId, Integer loopIndex, FailureCodeEnumVO failureCode, String summary, String payloadRef);
```

Rules:

- Debug data can reference payloads.
- Normal frontend must not call trace APIs.

## 13. Safe Failure Factory

`RuntimeFailureFactory` must create safe failure objects for:

- `MAX_LOOP_REACHED`
- illegal phase transition
- missing active run
- missing active pending input
- invalid pending answer
- contract repair exhausted
- context preparation failed without fallback
- action handler unavailable

`RuntimeSafeFailureVO` fields:

```java
private FailureCodeEnumVO failureCode;
private String userMessage;
private String developerMessage;
private Boolean retryable;
private RuntimePhaseEnumVO phase;
```

Rules:

- `userMessage` must not mention raw trace, raw model output, contract internals, prompt internals, or stack traces.
- Technical detail goes to developer trace.
- Phase 9 final delivery will guard final failure text. Phase 5 only creates the candidate safe text.

## 14. Required Tests

Create under:

- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/`

Required test files:

- `RuntimeStateMachineTest.java`
- `RuntimeLoopPolicyTest.java`
- `PendingInputUserAnswerTest.java`
- `PendingInputContinuationDispatcherTest.java`
- `RuntimeTranscriptBoundaryTest.java`
- `RuntimeLifecycleBoundaryTest.java`

### 14.1 `RuntimeStateMachineTest`

Required test cases:

1. `created_can_enter_preparing_context`
2. `waiting_user_must_resume_through_resolving_user_answer`
3. `illegal_transition_is_rejected`
4. `terminal_status_cannot_continue_loop`

### 14.2 `RuntimeLoopPolicyTest`

Required test cases:

1. `max_loop_reached_returns_safe_failure`
2. `contract_repair_attempt_does_not_increment_loop_count`
3. `waiting_user_does_not_increment_loop_count`
4. `resume_same_run_after_user_answer`

### 14.3 `PendingInputUserAnswerTest`

Required test cases:

1. `option_click_uses_stored_option_value`
2. `free_text_is_preserved_without_semantic_parsing`
3. `single_choice_rejects_free_text`
4. `tool_approval_rejects_free_text`
5. `unknown_option_id_returns_failed_answer`
6. `cancel_returns_cancelled_answer`

### 14.4 `PendingInputContinuationDispatcherTest`

Required test cases:

1. `context_selection_option_resumes_preparing_context`
2. `main_agent_free_text_resumes_preparing_context`
3. `tool_approval_approve_resumes_preparing_tool`
4. `tool_approval_reject_does_not_invoke_tool`
5. `unknown_handler_returns_safe_failure`

### 14.5 `RuntimeTranscriptBoundaryTest`

Required test cases:

1. `user_message_appended_before_run_loop`
2. `pending_input_checkpoint_recorded_before_waiting_user`
3. `user_reply_recorded_before_continuation_dispatch`
4. `normal_message_table_is_not_used_as_internal_transcript`

### 14.6 `RuntimeLifecycleBoundaryTest`

Required test cases:

1. `node_output_cannot_set_runtime_phase`
2. `node_output_cannot_set_run_status`
3. `runtime_writes_user_event_and_developer_trace_separately`
4. `missing_action_handler_returns_safe_failure`

## 15. Execution Tasks

### Task 1: Add Runtime Core Types

**Files:**

- Create files listed in Section 3.
- Create VOs listed in Section 5 when missing.

- [ ] Implement runtime status/result enums.
- [ ] Implement runtime commands and context objects.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 2: Add State Machine And Loop Policy

**Files:**

- `RuntimeStateMachine.java`
- `RuntimeLoopPolicy.java`
- `RuntimePhaseGuard.java`
- `RuntimeRecoveryCounters.java`

- [ ] Implement allowed transition table.
- [ ] Implement terminal/paused status checks.
- [ ] Implement loop/retry limit checks.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 3: Add Pending Input Services

**Files:**

- Create files listed in Section 4.

- [ ] Implement `UserReplyProcessor`.
- [ ] Implement `PendingInputManager`.
- [ ] Implement `UserInteractionManager`.
- [ ] Implement continuation dispatcher and handlers.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 4: Add Trace/Event/Transcript Interfaces

**Files:**

- `RunEventPublisher.java`
- `DeveloperTraceRecorder.java`
- `RunTranscriptRecorder.java`

- [ ] Define methods from Section 12.
- [ ] Use repository interfaces only.
- [ ] Keep normal events separate from developer trace.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 5: Add Runtime Service Skeleton

**Files:**

- `AutoAgentRuntimeService.java`
- `RuntimeComponentPorts.java`
- `MainActionDispatcher.java`
- `MainActionHandler.java`
- `MainActionHandlerResult.java`
- `NoopMainActionDispatcher.java`
- `RuntimeFailureFactory.java`

- [ ] Implement start procedure.
- [ ] Implement loop procedure with fake/stub action dispatcher support.
- [ ] Route `ASK_USER` to `UserInteractionManager`.
- [ ] Route missing handler to safe failure.
- [ ] Do not implement Phase 6 action business logic here.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 6: Add Runtime And Pending Input Tests

**Files:**

- Create tests listed in Section 14.

- [ ] Use fake repositories and fake component ports.
- [ ] Implement all Section 14 test cases.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RuntimeStateMachineTest,RuntimeLoopPolicyTest,PendingInputUserAnswerTest,PendingInputContinuationDispatcherTest,RuntimeTranscriptBoundaryTest,RuntimeLifecycleBoundaryTest" test
```

Expected result:

```text
BUILD SUCCESS
```

### Task 7: Cross-Spec Consistency Scan

- [ ] Run:

```powershell
rg -n "UserInputResolverNode|NEEDS_CLARIFICATION.*UserAnswer|new run.*pending|ToolRuntime.*create.*pending|MainAgentNode.*runStatus" docs\architecture\auto-agent-main-loop-harness-redesign-spec.md ai-agent-station-study-domain
```

Expected:

```text
No matches for obsolete user resolver or pending-input-as-new-run designs.
```

- [ ] Run:

```powershell
rg -n "runStatus|runtimePhase|loopIndex|developerTrace|auditRecord|toolReceipt" ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent\service\runtime ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent\service\interaction
```

Expected:

```text
Matches are allowed only in Runtime-owned code, guards, or rejection checks.
```

## 16. Acceptance Checklist

- [ ] Runtime owns all status and phase changes.
- [ ] `WAITING_USER` is a paused state inside the same run.
- [ ] User answer resumes through `RESOLVING_USER_ANSWER`.
- [ ] `UserReplyProcessor` is Java-only.
- [ ] Option click uses stored option value.
- [ ] Free text is preserved and not semantically interpreted.
- [ ] Tool approval rejects free text.
- [ ] Continuation dispatcher routes all pending input sources through one system.
- [ ] Loop limit produces deterministic safe failure.
- [ ] Contract repair and waiting-user pauses do not increment loop count.
- [ ] Transcript, event, and trace boundaries are separated.
- [ ] Missing action handler fails safely until Phase 6 implements real handlers.
- [ ] Tests pass.

## 17. Worker Split Guidance

If using subagents, split work by non-overlapping file ownership:

- Worker A: Runtime state machine, phase guard, loop policy, recovery counters.
- Worker B: pending input manager, user reply processor, continuation dispatcher.
- Worker C: runtime service skeleton and action dispatcher boundary.
- Worker D: event/trace/transcript recorder interfaces and safe failure factory.
- Worker E: tests and fake repositories/ports.

The integrator must verify that no code path creates a new run for pending user answers.

