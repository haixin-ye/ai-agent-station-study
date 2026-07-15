# AutoAgent Pending Input Checkpoint Recovery Fix

## 1. Purpose

This document defines the required fix for AutoAgent `ASK_USER`, tool approval, context clarification, and child-agent pending-input recovery.

The existing implementation already supports the main control-flow lifecycle:

```text
create PendingInput
  -> persist question/options/continuation
  -> publish ASK_USER event
  -> move Run to WAITING_USER
  -> accept a later user answer
  -> dispatch to a continuation handler
  -> re-enter Runtime
```

The missing guarantee is exact execution-state continuity. Different pending-input producers currently build different checkpoint payloads, and `resume()` initializes important Runtime fields instead of restoring them. The result is control-flow recovery without a complete and uniform execution-state recovery contract.

The fix must make pending input a durable, idempotent continuation boundary that can survive HTTP return, SSE reconnect, process restart, and delayed user response without losing the Run's planning and execution state.

## 2. Current Problem

### 2.1 Checkpoint creation is duplicated

`ContinuationCheckpointVO` is currently constructed independently by multiple components:

- `DefaultAutoAgentRuntimeService.pauseForUser(...)`;
- `AskUserActionHandler`;
- `ToolApprovalService`;
- `SubAgentAskUserActionHandler`;
- future RAG and final-repair pending-input producers.

Each producer decides for itself what to place in `checkpoint.payload`. This creates incompatible checkpoint shapes.

Examples from the current implementation:

- the Runtime fallback path can save `workingState` and `contextSelections`;
- the MainAgent `ASK_USER` handler saves only `contextSelections`;
- tool approval saves `approvalId`, `approvalKey`, `toolCallId`, and `toolIntent`, but not the parent Run working state;
- child-agent pending input saves parent/child identifiers and child context, but does not use a shared Runtime snapshot contract.

Adding another field to only one producer does not fix the root cause. Every current and future pending-input producer must use the same common continuation snapshot contract.

### 2.2 Runtime resume initializes state instead of restoring it

`DefaultAutoAgentRuntimeService.resume(...)` currently creates a new `RuntimeExecutionContext` with values equivalent to:

```text
currentPhase     = WAITING_USER
runStatus        = WAITING_USER
loopIndex        = 0
recoveryCounters = RuntimeRecoveryCounters.initial()
workingState     = null
lastStateView    = null
lastAction       = null
```

`MainAgentPendingInputHandler` can restore `workingState` when the checkpoint contains it, but several creation paths do not persist it. Loop index, recovery counters, last state view, and last action are not restored by the common Runtime resume path.

The resumed Run may therefore rebuild context from durable repositories instead of continuing from the exact state that produced the pending input.

### 2.3 Pending input consumption is not atomic

The current `markAnswered` update is equivalent to:

```sql
UPDATE agent_pending_input
SET status = 'ANSWERED', ...
WHERE pending_id = ?
```

It does not require `status = 'PENDING'`, and the repository method returns no affected-row count. A repeated click, HTTP retry, duplicate request, or concurrent submission can process the same pending input more than once.

This is especially dangerous for tool approval because a duplicate resume can re-enter `PREPARING_TOOL` and attempt the same external side effect again.

### 2.4 Pause persistence is not one coordinated state transition

PendingInput creation, payload persistence, ASK_USER event publication, and Run transition to `WAITING_USER` are performed by different layers and are not represented as one explicit pause operation.

This permits partial states such as:

- PendingInput exists while Run is still `RUNNING`;
- Run is `WAITING_USER` but the continuation payload is missing;
- an ASK_USER event is visible before the durable pause state is complete;
- payload persistence succeeds but PendingInput creation fails.

The fix must define one owner and one ordering contract for the pause boundary.

## 3. User-Visible and Runtime Impact

The current problem can produce the following symptoms in complex Runs:

1. Notebook plan progress is lost or reconstructed differently after the user answers.
2. Worklog and ActionHistory no longer represent the complete pre-pause execution path.
3. Previous tool or RAG work can be repeated because duplicate-action guards lose their in-memory history.
4. Evidence may be recalled again from persistence, but its exact ordering and relation to the previous loop can be lost.
5. `previousLoopOutcome` and `lastAction` can disappear, weakening replan and repeated-action protection.
6. `loopIndex` and retry counters restart, allowing repeated pause/resume cycles to bypass Run-level budgets.
7. Logs and traces can contain multiple loop zero sequences for the same Run.
8. Duplicate user submissions can resume the same Run more than once.
9. A duplicated tool approval can cause duplicate external writes unless a later tool idempotency guard happens to stop it.

The issue is most visible in Plan-Execute-Replan tasks that call tools, accumulate evidence, pause for approval or clarification, and then continue for several more loops.

## 4. Design Principles

### 4.1 PendingInput is the wait condition, not the execution snapshot

`agent_pending_input` remains the interaction record. It answers:

- which Run is waiting;
- which component requested input;
- what input mode is allowed;
- whether the request is pending, answered, cancelled, or expired;
- where its options, answer, and continuation are stored.

Large or structured content remains in `agent_payload` and is referenced by ID.

### 4.2 Checkpoint contains a typed common snapshot plus source-specific payload

Do not continue storing all continuation state in an untyped `Map<String, Object>`.

Extend the checkpoint contract to contain:

```java
public class ContinuationCheckpointVO {
    private Integer snapshotVersion;
    private String handler;
    private RuntimePhaseEnumVO resumePhase;
    private String sourceComponent;
    private String relatedRunId;
    private Integer relatedLoopIndex;
    private String expectedAnswerValueType;
    private RuntimeContinuationSnapshotVO runtimeSnapshot;
    private Map<String, Object> payload;
}
```

`runtimeSnapshot` is the common Runtime-owned state. `payload` is reserved for source-specific data such as a tool approval key or child-run ID.

### 4.3 Do not serialize the entire RuntimeExecutionContext

Persist only data required to resume deterministic execution. Do not serialize Spring beans, repositories, clients, thread state, request objects, or other process-local dependencies.

### 4.4 Runtime owns common restoration

Continuation handlers must not each reimplement common Runtime restoration. Runtime restores the common snapshot first; the selected handler then applies only source-specific semantics and chooses the next phase.

### 4.5 One PendingInput can be consumed once

The transition from `PENDING` to `ANSWERED`, `CANCELLED`, or `EXPIRED` must use a conditional atomic update. A second consumer must receive an idempotent conflict result and must not dispatch a continuation again.

## 5. Required Runtime Snapshot Contract

Introduce a data carrier under `domain/agent/model/valobj/runtime`, for example:

```java
public class RuntimeContinuationSnapshotVO {
    private String runId;
    private String sessionId;
    private Integer loopIndex;
    private Integer maxLoop;
    private RuntimeRecoveryCounters recoveryCounters;
    private MainAgentStateViewVO lastStateView;
    private RunWorkingStateVO workingState;
    private List<ContextSelectionVO> lastContextSelections;
    private MainAgentActionVO lastAction;
    private Map<String, Object> resumableRuntimeFacts;
}
```

### 5.1 Required fields

The snapshot must preserve at least:

- `loopIndex`;
- `maxLoop`;
- all `RuntimeRecoveryCounters`;
- complete `RunWorkingStateVO`:
  - base StateView;
  - Notebook;
  - Worklog;
  - ActionHistory;
  - EvidencePack;
  - UserClarifications;
  - PreviousLoopOutcome;
  - next sequence;
- last context selections;
- last MainAgent action;
- the last StateView when required for deterministic continuation;
- an allowlisted subset of resumable RuntimeFacts.

### 5.2 RuntimeFacts allowlist

Do not persist the entire map blindly. Define explicit resumable keys, including currently required values such as:

- `userClarifications`;
- `resumeToolIntent` when applicable;
- tool approval/denial facts;
- child-agent resume identifiers;
- context or RAG clarification facts that are intentionally part of continuation.

Reject or omit unknown non-serializable values. Sensitive raw receipts, prompts, model traces, clients, and process-local objects must not enter the checkpoint.

### 5.3 Snapshot version

Set `snapshotVersion = 1` for the new contract. Restoration must reject unsupported future versions with a safe, non-retry-looping failure rather than guessing field semantics.

For pre-fix PendingInputs, support a bounded legacy fallback:

1. read legacy `payload.workingState` and `payload.contextSelections` when present;
2. preserve source-specific legacy fields such as `toolIntent`;
3. do not claim exact restoration when mandatory state is absent;
4. emit a diagnostic event indicating legacy checkpoint fallback.

## 6. Centralized Checkpoint Creation

Introduce one domain service named `RuntimeContinuationSnapshotService`.

Its responsibilities are:

1. copy the common resumable state from `RuntimeExecutionContext`;
2. deep-copy or serialize through the project JSON mapper so later in-memory mutations cannot change the saved snapshot;
3. apply RuntimeFacts allowlisting;
4. attach handler, resume phase, source component, answer type, and source-specific payload;
5. validate mandatory checkpoint fields before persistence;
6. assign `snapshotVersion`.

All parent-Run pending-input producers must use this factory:

- ContextPlanner clarification;
- MainAgent `ASK_USER`;
- tool approval;
- RAG clarification;
- final-repair clarification.

Child-agent pending input must use the same versioned checkpoint envelope. Its source-specific payload continues to contain parent/child/task identifiers, while any parent Runtime snapshot required to resume the parent must be produced by the shared factory.

Remove private ad hoc `checkpointPayload(...)` implementations once their fields are represented by the common snapshot or source-specific payload.

## 7. Pause Coordination and Ordering

Create one pause coordinator in the domain interaction/runtime boundary. A suitable name is `PendingInputPauseCoordinator`.

The coordinator receives:

```text
RuntimeExecutionContext
AskUserRequest
pendingType
handlerCode
resumePhase
expectedAnswerValueType
sourceSpecificPayload
```

It performs the following logical transaction:

```text
1. validate and normalize AskUserRequest
2. build and validate the versioned checkpoint
3. save options payload
4. save checkpoint payload
5. create PendingInput with status PENDING
6. update Run phase/status to WAITING_USER
7. append the ASK_USER user-visible event
8. return pendingInputId and WAITING_USER result
```

Database writes in steps 3-7 should execute in one Spring transaction when repositories use the same MySQL datasource. Event publication here means appending the durable `agent_run_event` row; SSE delivery remains asynchronous and replayable.

Do not emit the user-visible event before PendingInput and Run state are durable.

### 7.1 MainAgent ASK_USER ownership

Avoid persisting PendingInput inside `AskUserActionHandler` before `RunWorkingStateManager.apply(...)` has recorded the ASK_USER action result.

Preferred flow:

```text
AskUserActionHandler validates and returns a WAITING_USER intent
  -> Runtime applies action/perUpdate to WorkingState
  -> Runtime invokes PendingInputPauseCoordinator
  -> coordinator snapshots the updated WorkingState and persists the pause
```

This ensures the checkpoint represents the exact post-action state at the pause boundary.

### 7.2 Tool approval ownership

Tool approval must preserve both:

- common parent Run snapshot;
- source-specific approval data: `approvalId`, `approvalKey`, `toolCallId`, `argumentsHash`, capability/server/tool identity, and `toolIntent`.

The approval record and pending-input pause must be transactionally consistent. If the pause cannot be created, do not leave an actionable orphan approval.

## 8. Atomic Answer Consumption

Change repository operations to conditional transitions that return whether the transition succeeded.

Example:

```sql
UPDATE agent_pending_input
SET status = 'ANSWERED',
    user_answer_ref = ?,
    answered_at = NOW()
WHERE pending_id = ?
  AND run_id = ?
  AND status = 'PENDING'
  AND (expires_at IS NULL OR expires_at > NOW())
```

Apply equivalent conditions to cancel and expire operations.

Required repository behavior:

- return affected-row count or a typed transition result;
- only the successful consumer may dispatch the continuation;
- duplicate answers return an idempotent `ALREADY_RESOLVED` result;
- an expired input cannot resume the Run;
- a PendingInput belonging to another Run is rejected;
- the Run itself must still be in `WAITING_USER` before continuation dispatch.

Save the answer payload and consume the PendingInput in one transaction. Do not leave an answer payload that appears accepted when the conditional state transition failed.

## 9. Required Resume Flow

The repaired `resume(...)` flow must be:

```text
1. load Run and verify status=WAITING_USER
2. load PendingInput and verify runId/status/expiry
3. load and validate checkpoint and snapshot version
4. normalize the submitted answer against inputMode/options
5. atomically persist answer and consume PendingInput
6. rebuild RuntimeExecutionContext identity from Run/original message
7. restore common Runtime snapshot
8. append normalized UserClarification exactly once
9. dispatch source-specific handler by checkpoint.handler
10. handler validates source payload and returns next phase
11. update Run to RUNNING and enter the validated resume phase
12. re-enter Runtime loop
```

Common restoration must happen before handler dispatch so every handler sees the same restored Notebook, Worklog, ActionHistory, EvidencePack, counters, and clarification history.

### 9.1 Handler responsibilities after the fix

`MainAgentPendingInputHandler`:

- records the MainAgent clarification semantic fact;
- resumes at `BUILDING_STATE_VIEW`;
- does not independently deserialize WorkingState.

`ContextPlannerPendingInputHandler`:

- records the context-planner answer;
- resumes at `PREPARING_CONTEXT` or the checkpoint's validated phase.

`ToolApprovalPendingInputHandler`:

- loads the approval by approval key;
- verifies Run, tool call, arguments hash, capability/server/tool identity, and pending approval status;
- on approval, sets `resumeToolIntent` and resumes at `PREPARING_TOOL`;
- on rejection, records a denial clarification and resumes at `BUILDING_STATE_VIEW`;
- never treats free text as approval.

`SubAgentPendingInputHandler`:

- validates parent/child/task relation;
- sends the answer to the exact child Run;
- keeps the parent in `WAITING_CHILDREN` until child completion;
- restores the parent snapshot before the parent later returns to its main loop.

Unknown handlers, invalid phases, mismatched Run IDs, or malformed source payloads must fail safely without dispatching the LLM or a tool.

## 10. Resume Phase Validation

Do not trust an arbitrary serialized phase. Validate allowed handler-to-phase mappings in Java.

Minimum mapping:

```text
CONTEXT_PLANNER  -> PREPARING_CONTEXT or BUILDING_STATE_VIEW
MAIN_AGENT      -> BUILDING_STATE_VIEW or CALLING_MAIN_NODE
TOOL_APPROVAL   -> PREPARING_TOOL or BUILDING_STATE_VIEW
GENERIC_SUB_AGENT -> WAITING_CHILDREN
RAG             -> BUILDING_STATE_VIEW or the documented RAG recovery phase
FINAL_REPAIR    -> REPAIRING_FINAL
```

The Runtime phase state machine remains the source of truth. A checkpoint cannot bypass phase guards.

## 11. Persistence and Transaction Requirements

No new table is required for the initial fix. Continue using:

- `agent_run` for Run status and phase;
- `agent_pending_input` for the wait condition and references;
- `agent_payload` for options, answer, and checkpoint JSON;
- `agent_run_event` for replayable user-visible ASK_USER events;
- `agent_tool_approval` for tool-specific approval state.

Required schema/index behavior:

- preserve the unique pending ID;
- conditional updates must include `status = 'PENDING'`;
- query-active-by-run continues to use `(run_id, status)`;
- enforce in domain policy that one parent Run has at most one active PendingInput;
- if concurrent creation is possible, add a database-enforced active-input guard or locking strategy rather than relying only on a pre-query.

Use `@Transactional` in the application/infrastructure-visible orchestration boundary that owns the MySQL writes. Domain services continue to express decisions; transaction configuration remains outside persistence entities.

## 12. Observability

Add diagnostics for:

- checkpoint created: version, handler, resume phase, loop index, snapshot payload reference;
- Run paused: pending ID and source component;
- answer accepted or rejected: pending status transition result;
- checkpoint restored: version and restored loop index;
- legacy checkpoint fallback;
- duplicate answer ignored;
- resume phase entered;
- checkpoint validation failure.

Do not expose raw WorkingState, prompts, tool receipts, approval secrets, or full checkpoint payloads in the normal UI/SSE event. User-visible events contain only safe question and status data.

## 13. Required Tests

### 13.1 MainAgent ASK_USER full-state round trip

Build a Runtime context containing:

- non-zero loop index;
- non-zero recovery counters;
- Notebook with completed and in-progress steps;
- Worklog entries;
- ActionHistory;
- EvidencePack;
- UserClarifications;
- PreviousLoopOutcome;
- last state view, context selections, and last action.

Pause through the real MainAgent `ASK_USER` path, serialize through the real payload repository shape, resume through the real Runtime facade, and assert every required field is restored before MainAgent is called again.

### 13.2 Plan-Execute-Replan continuation

Execute:

```text
PLAN/CONTINUE
  -> successful tool or RAG action
  -> ASK_USER
  -> user answer
  -> resume
```

Assert that completed Notebook steps remain completed, evidence remains available, sequence numbers remain monotonic, and completed actions are not executed again.

### 13.3 Loop and retry budget preservation

Pause with non-zero loop, tool retry, RAG retry, contract repair, final repair, and compression counters. Resume and assert that none reset to zero.

Verify that repeated ASK_USER cycles cannot bypass `maxLoop`.

### 13.4 Tool approval round trip

Pause a high-risk tool call after prior WorkingState has been accumulated.

On approval, assert:

- original WorkingState is restored;
- approval key and arguments hash match;
- original tool intent is resumed once;
- tool execution occurs once;
- the tool result is appended to the existing ActionHistory/EvidencePack.

On rejection, assert the tool is not invoked and the rejection clarification reaches the next StateView.

### 13.5 Duplicate and concurrent answer test

Submit the same PendingInput twice, including a concurrent test when practical.

Assert:

- exactly one conditional update succeeds;
- exactly one continuation is dispatched;
- exactly one tool call can result;
- the second response is the typed result `ALREADY_RESOLVED`;
- no duplicate UserClarification or transcript entry is created.

### 13.6 Expiry and ownership test

Assert that:

- an expired PendingInput cannot resume;
- a PendingInput cannot be submitted under another Run ID;
- a Run not in `WAITING_USER` cannot consume a pending answer;
- malformed or unknown checkpoint handlers fail safely.

### 13.7 SSE reconnect test

Create a PendingInput, disconnect the SSE consumer, reconnect using event replay, and query the active PendingInput endpoint.

Assert that the same pending ID and safe user-visible question are returned without creating a second PendingInput.

### 13.8 Process-restart serialization test

Do not reuse the original in-memory objects. Serialize the checkpoint to JSON, deserialize it into a fresh Runtime context, and then resume. This test must prove durable recovery rather than object-reference reuse.

### 13.9 Legacy checkpoint compatibility

Load pre-fix checkpoint JSON for MainAgent and tool approval.

Assert that:

- supported legacy fields are read;
- a diagnostic records fallback use;
- missing mandatory source data fails safely;
- legacy fallback cannot reset budgets and then silently claim exact restoration.

## 14. Acceptance Criteria

The fix is complete only when all of the following are true:

1. Every production PendingInput creation path uses one versioned checkpoint creation contract.
2. MainAgent `ASK_USER` restores complete WorkingState, loop index, counters, last action, and context selections.
3. Tool approval restores the parent Run state and invokes an approved tool at most once.
4. PendingInput answer/cancel/expire transitions are conditional and atomic.
5. Duplicate submissions do not dispatch continuation twice.
6. Runtime restores common state before invoking a source-specific continuation handler.
7. Handler-to-phase mappings are validated by Java phase policy.
8. ASK_USER events are emitted only after the durable pause state exists.
9. Page refresh, SSE reconnect, delayed response, and process restart do not lose the pending question or resumable state.
10. Existing input-mode rules remain intact, including the rule that free text cannot authorize a high-risk tool.
11. Normal UI/SSE output does not expose internal checkpoint or WorkingState content.
12. Targeted Runtime, interaction, tool approval, subagent, repository, and controller tests pass.

## 15. Non-Goals

This fix does not:

- change the MainAgent Action JSON contract;
- let the LLM control Runtime phases;
- replace MySQL with an in-memory continuation store;
- serialize Java threads or the full Spring application context;
- redesign the frontend interaction components;
- expose checkpoint internals to users;
- redesign tool idempotency beyond guaranteeing one continuation dispatch per PendingInput;
- redesign child-agent scheduling beyond preserving its continuation state.

## 16. Implementation Guidance for the Coding Agent

Before editing, inspect these current ownership points:

- `DefaultAutoAgentRuntimeService.resume(...)`, `pauseForUser(...)`, and `runLoop(...)`;
- `AskUserActionHandler`;
- `UserInteractionManager` and `PendingInputManager`;
- `PendingInputContinuationDispatcher` and every registered handler;
- `ToolApprovalService` and `ToolApprovalPendingInputHandler`;
- `SubAgentAskUserActionHandler` and `SubAgentPendingInputHandler`;
- `IPendingInputRepository`, `PendingInputRepository`, DAO, PO, and MyBatis mapper;
- `RunWorkingStateManager`;
- current pending-input, continuation-dispatcher, Runtime transcript, repeated-action, and tool-approval tests.

Implementation order:

```text
1. add typed/versioned snapshot contract
2. add common snapshot capture and restore services
3. centralize parent-Run pause coordination
4. move MainAgent ASK_USER persistence to the coordinated pause boundary
5. pass the common snapshot through tool approval and other producers
6. implement atomic PendingInput consumption
7. restore snapshot before continuation handler dispatch
8. validate handler/phase/source relationships
9. add observability
10. add round-trip, duplicate, approval, restart, and regression tests
```

Do not implement only a local `workingState` addition in `AskUserActionHandler`. That patch would hide one symptom while leaving tool approval, counters, duplicate consumption, and future pending-input producers on incompatible continuation contracts.
