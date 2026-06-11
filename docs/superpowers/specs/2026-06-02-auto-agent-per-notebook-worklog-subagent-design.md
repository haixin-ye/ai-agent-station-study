# AutoAgent PER Notebook, Worklog, Evidence, And Future TaskAgent Delegation Design

Status: Draft Spec, authoritative after user review.

Primary audience: Codex and future AI coding agents implementing this repository.

Related references:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`
- `docs/architecture/auto-agent-prompt-harness-governance-spec.md`
- `docs/superpowers/specs/2026-05-20-auto-agent-memory-lifecycle-design.md`
- `docs/superpowers/specs/2026-05-28-auto-agent-rag-asset-module-redesign.md`

## 0. Spec Governance

### 0.1 Purpose

This spec defines the target design for upgrading the current AutoAgent main-loop harness into a PER-style controller:

```text
Plan -> Execute -> Replan
```

The design introduces:

- a Runtime-owned notebook for MainAgent task planning and progress interpretation;
- a Runtime-owned worklog for deterministic action execution records;
- an evidence pack model for original materials produced during a run;
- a structured `perUpdate` section in every MainAgent action output;
- a future TaskAgent delegation direction through `DELEGATE_AGENTS` with `WAIT_ALL`;
- a future child-agent `COMMIT` action for returning results to the parent run after Agent Harness abstraction is designed.

This is not a redesign of the existing Memory, RAG, or MCP modules. It is a harness-level upgrade that lets MainAgent consume those modules more reliably across multi-loop tasks.

### 0.2 Authority

After this spec is accepted:

- changes to MainAgent action contracts, Runtime working state, state-view projection, action result handling, notebook persistence, worklog persistence, evidence projection, Agent Harness abstraction, or future TaskAgent delegation must follow this spec;
- code comments, prompts, tests, and future implementation plans must use this terminology consistently;
- if older working notes conflict with this spec, this spec wins for PER/notebook/worklog/subagent behavior.

This spec does not override existing governance for:

- final delivery and final response guards;
- Java-owned contracts and parser validation;
- DDD package boundaries;
- Runtime ownership of lifecycle and pending input;
- RAG as a memory/context injection mechanism;
- MCP tool execution through Java Runtime rather than model-mounted tools.

### 0.3 Language And Audience

This canonical spec is written in English because it is meant to be consumed by Codex and other AI implementation agents.

User-facing discussion may happen in Chinese, but implementation must follow this document.

### 0.4 Non-Negotiable Rules

- Runtime remains deterministic Java orchestration. Runtime must not use an LLM to summarize, interpret, rank, or decide semantic meaning.
- MainAgent is the semantic controller. It interprets notebook, worklog, evidence, memory, RAG, user answers, and child-agent commits.
- `MainAgentStateView` is a read-only projection for a loop iteration, not the source of truth.
- `RunWorkingState` is the source of truth for in-run notebook, worklog, evidence, user clarifications, waiting children, and loop-local execution state.
- Memory/RAG context injection stays in the existing context preparation path.
- RAG action may remain as a fallback path, but the preferred RAG path is memory/context injection.
- MCP tools stay in the existing `CALL_TOOL -> ToolActionOrchestrator -> ToolRuntime -> McpToolInvokerPort` path.
- MCP tool results must be saved as original material. Runtime may store tool-provided text and mechanical execution metadata, but must not invent semantic summaries.
- `PLAN` may remain for compatibility, but notebook updates are driven by `perUpdate`, not by `PLAN` alone.
- Child agents, once implemented, must not directly answer the user. They return `COMMIT` results to the parent run.
- Delegation must not be implemented before the Agent Harness abstraction is designed and reviewed. When delegation is implemented, the first supported parent wait policy is `WAIT_ALL`; current-answer child tasks must not be detached.

## 1. Core Concepts

### 1.1 RunWorkingState

`RunWorkingState` is the authoritative mutable state for one run.

It must contain or be able to project:

- base state view produced by context preparation;
- notebook;
- worklog;
- evidence pack;
- user clarifications;
- previous loop outcome;
- waiting child-agent records;
- sequence counters;
- action history compatibility fields when existing code still needs them.

`RunWorkingState` is Runtime-owned. MainAgent never mutates it directly. MainAgent proposes changes through `perUpdate`.

### 1.2 MainAgentStateView

`MainAgentStateView` is the read-only view passed to MainAgent in each loop.

It is projected from:

- current user input and selected conversation context;
- memory pack and RAG pack from existing context preparation;
- available capabilities from existing capability selection;
- notebook from `RunWorkingState`;
- worklog from `RunWorkingState`;
- evidence pack from `RunWorkingState`;
- user clarifications;
- pending/waiting child-agent state;
- previous loop outcome and failure state when present.

MainAgent must treat `MainAgentStateView` as the complete visible state for the current loop.

### 1.3 Notebook

Notebook is MainAgent's PER working cognition for the current run.

It stores:

- task goal;
- task mode;
- plan steps;
- current or next step;
- facts learned by MainAgent from evidence;
- open questions;
- risks;
- last decision;
- links from steps to worklog records and evidence records.

Notebook is not memory, RAG, evidence, or debug trace. It is the current run's semantic task board.

Notebook content is generated by MainAgent through `perUpdate`, then validated and merged by Runtime.

### 1.4 Worklog

Worklog is Runtime's deterministic execution ledger for actions.

It stores what actually happened:

- which action was requested;
- when it was requested and completed;
- action status;
- tool name and arguments for tool calls;
- user ask request and answer link for `ASK_USER`;
- child-agent creation and commit/failure records;
- created evidence ids;
- failure code and failure message;
- repeat guard key for duplicate-action prevention;
- sequence and loop index.

Worklog must not contain Runtime-invented semantic summaries. It may contain mechanical status text such as:

```text
Tool read_file succeeded. Result saved as ev-001, 4382 chars.
```

### 1.5 EvidencePack

EvidencePack is the current run's material warehouse.

It stores original or directly produced material that MainAgent can use for reasoning and final answers:

- MCP tool results;
- retained RAG action results;
- important user clarifications;
- child-agent commit payloads;
- artifact operation results;
- verification/test/check outputs when future actions produce them.

EvidencePack may store tool-provided textual content, raw JSON, structured records, or payload references.

Runtime must not convert evidence into semantic conclusions. MainAgent reads evidence and writes semantic interpretation into notebook.

### 1.6 PER Update

`perUpdate` is the structured notebook update emitted by MainAgent in each loop.

It is separate from `stateDelta`:

- `perUpdate` updates notebook.
- `stateDelta` carries action-specific inputs.

Every MainAgent output should include `perUpdate`. For simple tasks, `perUpdate` may be lightweight.

### 1.7 Future TaskAgent

TaskAgent is the first child-agent type to design after the Agent Harness abstraction is reviewed.

It is a small, temporary agent for atomic delegated work:

- short-lived;
- parent-scoped;
- uses full local task context rather than the full MainAgent memory lifecycle;
- has bounded capabilities granted by the parent request and Runtime policy;
- cannot final-answer the user;
- returns `COMMIT` to parent Runtime.

TaskAgent is not the future CodeAgent. CodeAgent is a later specialized persistent agent with its own workspace and coding-friendly memory.

This PER foundation spec may describe TaskAgent concepts so later work keeps terminology consistent, but the first implementation slice must not add `DELEGATE_AGENTS`, `WAITING_CHILDREN`, `COMMIT`, or child runtime execution. Those belong after the Agent Harness abstraction spec.

## 2. Existing Module Compatibility

### 2.1 Memory And RAG

The existing memory lifecycle remains the primary source of historical and RAG context.

Current desired RAG behavior:

```text
User input
  -> context preparation
  -> memory recall
  -> RAG recall as part of memory/context injection
  -> stateView.ragPack
  -> MainAgent reads ragPack
```

The existing `RETRIEVE_RAG` action may remain available as fallback robustness:

- when automatic memory/RAG injection fails;
- when MainAgent explicitly needs another retrieval attempt;
- when current state view lacks required private/uploaded material.

The PER redesign must not move normal RAG behavior out of memory/context preparation.

### 2.2 MCP Tool Calling

The existing tool path remains:

```text
MainAgent CALL_TOOL
  -> CallToolActionHandler
  -> ToolActionOrchestrator
  -> ToolInvocationRequestBuilder
  -> ToolRuntime
  -> McpToolInvokerPort
  -> receipt capture
  -> evidence/worklog projection
  -> next MainAgent loop
```

MainAgent must not mount MCP tools directly.

Because Java Runtime executes MCP tools instead of Spring AI native tool-calling directly attached to the model, Runtime must emulate the important behavior of model tool-calling:

```text
tool result must be returned to the model-visible state
```

For AutoAgent, this means the original MCP tool result must be saved in EvidencePack and projected into `MainAgentStateView` within token budget.

### 2.3 Existing Action History

Existing `actionHistory` should not be removed immediately.

During migration:

- keep `actionHistory` for compatibility with existing tests and prompt logic;
- add `worklog` as the canonical future execution ledger;
- update prompt guidance to prefer `worklog` for execution details and sequence;
- gradually reduce reliance on `actionHistory` after tests prove `worklog` is complete.

## 3. MainAgent Output Contract

### 3.1 Target Output Shape

MainAgent output should evolve from:

```json
{
  "action": "CALL_TOOL",
  "stateDelta": {}
}
```

to:

```json
{
  "perUpdate": {},
  "action": "CALL_TOOL",
  "stateDelta": {}
}
```

`perUpdate` is optional only during migration. After the PER contract is fully adopted, it should be required for MainAgent outputs.

### 3.2 Direct Mode

Simple user questions should not require heavy planning.

For direct answers:

```json
{
  "perUpdate": {
    "mode": "DIRECT",
    "lastDecision": "The user asked a simple public-knowledge question. No tools, RAG action, user ask, or delegation is needed."
  },
  "action": "FINAL",
  "stateDelta": {
    "finalAnswerCandidate": {
      "content": "..."
    }
  }
}
```

Direct mode prevents PER from slowing down trivial tasks.

### 3.3 PER Mode

PER mode is required when the current run involves:

- multiple action steps;
- tool calls;
- user clarification;
- child-agent delegation;
- file or directory inspection;
- file writing or destructive operations;
- code or project analysis;
- long-form work that depends on intermediate materials;
- any task where MainAgent needs process memory across loops.

Example:

```json
{
  "perUpdate": {
    "mode": "PER",
    "goal": "Inspect the target domain folder, summarize it, and write a txt file to Desktop.",
    "stepUpdates": [
      {
        "stepId": "s1",
        "title": "Resolve the target domain folder",
        "status": "IN_PROGRESS",
        "note": "The user said 'domain folder'; resolve the actual path before reading or writing."
      }
    ],
    "nextStepId": "s1",
    "lastDecision": "Resolve the target folder before reading contents."
  },
  "action": "CALL_TOOL",
  "stateDelta": {
    "toolIntent": {
      "capabilityCode": "file_system_search_files",
      "toolName": "search_files",
      "arguments": {
        "path": "E:/javaProject/ai-agent-station-study",
        "pattern": "**/*domain*"
      }
    }
  }
}
```

### 3.4 Allowed Actions

MainAgent actions after this redesign:

| Action | Meaning |
|---|---|
| `FINAL` | Produce final user-facing answer through final delivery guard. |
| `CALL_TOOL` | Request Runtime to execute an MCP-backed tool. |
| `ASK_USER` | Request deterministic pending input. |
| `PLAN` | Only update notebook and continue. Compatibility action; not the primary notebook mechanism. |
| `CONTINUE` | Continue only when needed and non-empty. Must not create empty loops. |
| `DELEGATE_AGENTS` | Future action for creating child TaskAgent runs after Agent Harness abstraction is approved. First supported wait policy should be `WAIT_ALL`. |
| `RETRIEVE_RAG` | Fallback RAG action. Normal RAG is memory/context injection. |
| `FAIL` | Request safe failure handling. |
| `REPAIR_FINAL` | Runtime-directed final repair only. |

### 3.5 PLAN Semantics

`PLAN` remains for compatibility and migration.

`PLAN` means:

```text
MainAgent wants to update notebook but does not need a tool, user ask, child delegation, RAG action, or final answer in this loop.
```

`PLAN` must:

- require a meaningful `perUpdate`;
- continue the run;
- not produce final answer text;
- not call tools;
- not modify memory/RAG directly.

Runtime should prevent repeated empty `PLAN` loops.

### 3.6 CONTINUE Semantics

`CONTINUE` should be rare.

It may be used when:

- Runtime needs another loop after non-action state changes;
- MainAgent has a concrete next-action hint but no external action is required;
- migration code still needs compatibility behavior.

`CONTINUE` must include a concrete non-empty reason or hint. Runtime should reject empty `CONTINUE`.

## 4. Notebook Schema

### 4.1 Suggested VO

Suggested domain value objects:

```text
MainAgentNotebookVO
NotebookStepVO
NotebookFactVO
NotebookQuestionVO
NotebookRiskVO
PerUpdateVO
PerStepUpdateVO
```

Package placement:

```text
ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/
```

or, if the project already groups state-view VOs differently:

```text
ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/context/
```

Do not put these VOs under `service/**`.

### 4.2 MainAgentNotebookVO Fields

Recommended fields:

```text
String mode                    // DIRECT or PER
String goal
Integer notebookVersion
Integer lastUpdatedLoopIndex
Long lastUpdatedSequence
List<NotebookStepVO> steps
List<NotebookFactVO> facts
List<NotebookQuestionVO> openQuestions
List<NotebookRiskVO> risks
String nextStepId
String lastDecision
Map<String, Object> metadata
```

`notebookVersion` increments whenever Runtime accepts a `perUpdate`.

### 4.3 NotebookStepVO Fields

Recommended fields:

```text
String stepId
String title
String status                  // PENDING, IN_PROGRESS, DONE, BLOCKED, CANCELLED
String note
List<String> relatedWorkIds
List<String> relatedEvidenceIds
Integer createdLoopIndex
Integer updatedLoopIndex
Long createdSequence
Long updatedSequence
Map<String, Object> metadata
```

Runtime validates shape. MainAgent decides semantic status transitions.

### 4.4 NotebookFactVO Fields

Recommended fields:

```text
String factId
String content
List<String> sourceEvidenceIds
List<String> sourceWorkIds
Integer loopIndex
Long sequence
```

Facts are semantic statements created by MainAgent. They are not Runtime summaries.

### 4.5 Notebook Merge Rules

Runtime applies `perUpdate` as a deterministic patch:

- If `mode` is present, set notebook mode.
- If `goal` is present and non-blank, set or update goal.
- Merge step updates by `stepId`.
- Merge facts by `factId` when provided; otherwise append with generated id.
- Merge open questions by id or normalized content.
- Merge risks by id or normalized content.
- Set `nextStepId` when present.
- Set `lastDecision` when present.
- Increment `notebookVersion`.
- Set `lastUpdatedLoopIndex` and `lastUpdatedSequence`.

Runtime must reject:

- invalid step status;
- missing `stepId` in step updates;
- impossible oversized `perUpdate` beyond configured limits;
- attempts to write lifecycle phase, run status, raw traces, or final delivery internals.

Runtime must not decide whether a step is logically done. MainAgent decides that based on worklog and evidence.

## 5. Worklog Schema

### 5.1 Purpose

Worklog is the canonical in-run action execution ledger.

It exists to answer:

```text
What did Runtime do?
When did it happen?
Was it successful?
What material did it produce?
Can MainAgent safely avoid repeating it?
```

### 5.2 Suggested VO

Suggested domain value objects:

```text
RuntimeWorklogItemVO
ActionRequestSnapshotVO
ActionResultSnapshotVO
ToolCallSnapshotVO
AskUserSnapshotVO
SubAgentSnapshotVO
```

### 5.3 RuntimeWorklogItemVO Fields

Recommended fields:

```text
String workId
String runId
Integer loopIndex
Long sequence
String actionType
String status
String stepId
String sourceComponent          // MAIN_AGENT, TASK_AGENT, TOOL_APPROVAL, etc.
String requestRef               // payload ref for full request when needed
ActionRequestSnapshotVO request
String resultRef                // payload ref for full raw result when needed
ActionResultSnapshotVO result
List<String> resultEvidenceIds
String failureCode
String failureMessage
Boolean retryable
String repeatGuardKey
LocalDateTime startedAt
LocalDateTime completedAt
Map<String, Object> metadata
```

### 5.4 Status Values

Recommended statuses:

```text
REQUESTED
RUNNING
SUCCEEDED
FAILED
WAITING_USER
WAITING_CHILDREN
APPROVAL_REJECTED
SKIPPED_ALREADY_SUCCEEDED
CANCELLED
COMMITTED
```

These are mechanical statuses, not MainAgent plan statuses.

### 5.5 Tool Worklog Requirements

For every `CALL_TOOL`, worklog must include:

- capability code;
- MCP server code when available;
- tool name;
- materialized arguments or arguments ref;
- permission/approval status;
- tool call id;
- tool invocation id;
- repeat guard key;
- success/failure status;
- failure code/message if failed;
- evidence ids created from the tool result;
- payload ref for original result;
- loop index and sequence.

The worklog must be detailed enough that MainAgent can know:

- the same tool request already succeeded;
- the same tool request failed and why;
- user rejected a high-risk tool operation;
- a tool is waiting for approval;
- a tool produced evidence that should be read before deciding next action.

### 5.6 Repeat Guard Key

Runtime must create a deterministic repeat guard key for actions where repetition is dangerous or wasteful.

For `CALL_TOOL`:

```text
CALL_TOOL:<capabilityCode>:<mcpServerCode>:<toolName>:<stableArgsHash>
```

For `DELEGATE_AGENTS`:

```text
DELEGATE_AGENTS:<parentRunId>:<clientTaskId>:<stableTaskHash>
```

MainAgent prompt must say:

- If the same repeat guard key already succeeded, do not repeat it.
- If it failed, only retry when arguments or task scope materially changed.
- If it was rejected by user approval, do not retry the same operation.

Runtime may enforce duplicate prevention independently.

## 6. EvidencePack Schema

### 6.1 Purpose

EvidencePack is the material warehouse for the current run.

It answers:

```text
What original materials did previous actions produce?
Which worklog item produced each material?
Can MainAgent read and cite/use it for next-step reasoning or final answer?
```

### 6.2 Suggested VO

Current `MaterializedEvidenceVO` may be extended or a new canonical VO may be introduced.

Recommended fields:

```text
String evidenceId
String runId
Long sequence
Integer sourceLoopIndex
String sourceWorkId
String evidenceType
String content
String contentRef
String contentFormat             // TEXT, JSON, BINARY_REF, STRUCTURED
Boolean truncated
Integer totalChars
Long totalBytes
LocalDateTime createdAt
Map<String, Object> metadata
```

Current `summary` and `boundedSnippet` fields may remain for compatibility, but Runtime must not invent semantic summaries.

If `summary` is kept, it should be either:

- tool-provided text;
- mechanically derived status text;
- a bounded copy of original content;
- empty/null.

### 6.3 Evidence Types

Recommended evidence types:

```text
TOOL_RESULT
RAG_ACTION_RESULT
USER_CLARIFICATION
SUBAGENT_COMMIT
ARTIFACT_RESULT
VERIFICATION_RESULT
SYSTEM_RECEIPT
```

Normal memory/RAG injection through context preparation remains in `memoryPack` / `ragPack`.

Only explicit action-produced RAG results need to enter EvidencePack.

### 6.4 Tool Result Evidence

For MCP tool results:

- save original tool content as evidence;
- save full content in payload when large;
- project budget-limited content into state view;
- mark `truncated=true` when projected content is incomplete;
- preserve `contentRef` for full result;
- do not create an LLM summary.

Example:

```json
{
  "evidenceId": "ev-001",
  "sourceWorkId": "work-001",
  "evidenceType": "TOOL_RESULT",
  "content": "original read_file content within budget",
  "contentRef": "payload-tool-result-001",
  "contentFormat": "TEXT",
  "truncated": false,
  "totalChars": 4382
}
```

### 6.5 User Clarification Evidence

User clarifications are already stored in `userClarifications`.

When a clarification is important material for final reasoning, Runtime may also create evidence:

```text
USER_CLARIFICATION
```

Examples:

- user chooses a workspace;
- user selects one file from multiple candidates;
- user approves or rejects a high-risk operation;
- user gives missing project constraints.

### 6.6 SubAgent Commit Evidence

A child `COMMIT` result must create parent evidence:

```text
SUBAGENT_COMMIT
```

The parent evidence must include:

- child run id;
- child task id;
- commit status;
- full child result or payload ref;
- child-created evidence refs when transferred;
- whether returned material is full or partial.

For file/code/research tasks, full result or full references must be preserved. Parent MainAgent must not receive only a short child summary.

## 7. Time And Ordering

### 7.1 Required Ordering Fields

Notebook, worklog, and evidence must preserve ordering.

Every worklog item must include:

```text
runId
loopIndex
sequence
startedAt
completedAt
```

Every evidence item must include:

```text
runId
sourceLoopIndex
sequence
sourceWorkId
createdAt
```

Notebook must include:

```text
notebookVersion
lastUpdatedLoopIndex
lastUpdatedSequence
```

### 7.2 Ordering Priority

When MainAgent interprets state:

```text
sequence > loopIndex > timestamp
```

`sequence` is the reliable in-run order. Timestamps are for display and audit.

### 7.3 Prompt Guidance

MainAgent prompt must instruct:

1. Read notebook first to understand current task goal, plan, and next step.
2. Read worklog by sequence to understand what was actually done.
3. For each relevant worklog item, read `resultEvidenceIds`.
4. Use evidence pack content as original material.
5. If multiple records share a repeat guard key, use the latest sequence while respecting previous failures, rejections, or skipped duplicates.
6. Update notebook using `perUpdate` with related work ids and evidence ids.
7. Only then choose the next action.

## 8. Runtime Flow With PER

### 8.1 Canonical Loop

The target loop:

```text
prepare / refresh state
project MainAgentStateView from RunWorkingState
invoke MainAgent
parse and validate action output
validate and merge perUpdate into RunWorkingState.notebook
handle action
write worklog
write evidencePack when material is produced
route next phase
continue / wait / complete / fail
```

### 8.2 Merge Order

For each MainAgent output:

1. Parse and validate output contract.
2. Validate `perUpdate`.
3. Merge `perUpdate` into notebook.
4. Validate action-specific `stateDelta`.
5. Execute or route action.
6. Write action worklog result.
7. Write evidence for produced material.
8. Project next state view.

`perUpdate` is merged before action execution because it records MainAgent's decision context for the action.

Action results are interpreted by MainAgent in the next loop, not by Runtime.

### 8.3 Max Loop Behavior

The existing hard max-loop guard remains.

PER should reduce max-loop failures by making progress visible.

Before failing for max loop, future enhancement may ask MainAgent for a bounded failure/final summary using current notebook, but first implementation may keep current deterministic failure behavior.

### 8.4 Pending Input And Resume

PER state must survive `ASK_USER` pause/resume.

When Runtime pauses for pending input, the continuation checkpoint must preserve enough state to resume the same in-run task board:

- current `RunWorkingState` or a payload reference to it;
- notebook;
- worklog;
- evidence pack;
- user clarifications collected so far;
- sequence counters;
- current loop index and context selections needed to rebuild the next state view.

On resume, Runtime must restore or reconstruct `RunWorkingState` before calling MainAgent again. The resumed MainAgent state view must include the notebook/worklog/evidence state that existed before the pause plus the new user clarification.

This is required for both MainAgent `ASK_USER` and future child-agent `ASK_USER`. Child-agent resume rules are deferred to the Agent Harness abstraction, but they must reuse the same pending-input checkpoint principle.

First implementation may store an in-run serialized snapshot in the continuation checkpoint or a payload reference. It does not need to introduce final database tables for notebook/worklog, but it must not silently drop PER state across `WAITING_USER`.

## 9. File And Directory Target Resolution

### 9.1 Rule

When the user names a file or directory in natural language, MainAgent must resolve the target before reading, writing, or modifying it.

Examples:

- "domain folder"
- "the config file"
- "the previous generated txt"
- "the project root"
- "XXXX directory"

### 9.2 Required Behavior

MainAgent must:

1. Search or list likely locations.
2. If exactly one plausible target exists, use it.
3. If multiple plausible targets exist, ask the user.
4. If no target exists, ask the user or report inability.
5. Only then read or write.

This prevents incorrect direct assumptions such as treating "domain folder" as a specific module path without verification.

### 9.3 Prompt Requirement

MainAgent prompt must explicitly say:

```text
For file or directory references, resolve the target first. Do not assume a root-level path unless the user gave an exact absolute path or a path already discovered by tool evidence.
```

## 10. MCP Tool Result Projection

### 10.1 Problem

Previous behavior can expose only short tool result summaries to MainAgent. This can cause:

- repeated identical tool calls;
- inability to analyze file contents;
- loop exhaustion;
- false belief that tool result was unavailable;
- final answers based on incomplete material.

### 10.2 Target Behavior

After a tool call succeeds or fails, MainAgent must see enough original result material in the next state view to continue.

For `read_file`, this means the file content should be visible when within budget.

For large results:

- save full result in payload;
- project a budget-bounded original slice;
- mark `truncated=true`;
- include `contentRef`, `totalChars`, and total size metadata.

Runtime must not call another LLM to summarize tool results.

### 10.3 Worklog And Evidence Split

For tool calls:

```text
worklog = tool request, status, ids, arguments, repeat key, result evidence ids
evidencePack = original tool result material
notebook = MainAgent interpretation in next perUpdate
```

### 10.4 Tool-Provided Text

Tool-provided text may be stored as-is.

Examples:

- MCP text content;
- error message returned by MCP;
- list of files returned by filesystem tool;
- search result text returned by search MCP.

This is not Runtime semantic summarization.

### 10.5 Existing ToolRuntime Changes

Implementation should avoid breaking the existing tool path.

Expected changes:

- preserve existing result summary fields for compatibility;
- add full result payload capture if not already available;
- convert full result into evidence;
- project evidence into state view with budget limits;
- add worklog records with repeat guard keys.

## 11. Future TaskAgent Delegation

### 11.1 Scope

TaskAgent delegation is a future phase after the Agent Harness abstraction is designed and reviewed.

The Agent Harness abstraction must first define:

- reusable `AgentRuntime` lifecycle pieces;
- which lifecycle pieces MainAgent alone owns;
- child-agent memory mode;
- agent capability table and enforcement;
- agent-local notebook, worklog, and evidence;
- child `COMMIT`;
- child `ASK_USER`;
- child recovery and resume.

Only after that foundation exists should the first TaskAgent implementation support temporary TaskAgents with `WAIT_ALL`.

Out of first-stage scope:

- detached/background child agents;
- `WAIT_ANY`;
- long-running monitors;
- persistent CodeAgent;
- child agent final answers to user;
- child agent spawning grandchildren.

### 11.2 Future DELEGATE_AGENTS Action

MainAgent emits:

```json
{
  "perUpdate": {},
  "action": "DELEGATE_AGENTS",
  "stateDelta": {
    "waitPolicy": "WAIT_ALL",
    "delegations": []
  }
}
```

`waitPolicy` must be `WAIT_ALL` in the first TaskAgent implementation after Agent Harness approval.

### 11.3 Delegation Requirements

Each delegation must be atomic and clear.

Required fields:

```text
clientTaskId
name
task
expectedOutput
capabilities
stepId
```

Recommended fields:

```text
scope.include
scope.exclude
contextSeed
requiredEvidenceIds
outputRequirements
```

Bad task:

```text
Review the whole project and improve it.
```

Good task:

```text
Only inspect how CALL_TOOL results are persisted and projected back to MainAgent. Return call chain, exact visible fields, and 2-5 risks.
```

### 11.4 Runtime Validation

Runtime should reject delegation when:

- `waitPolicy` is not `WAIT_ALL`;
- no delegations are present;
- a delegation lacks `task`, `expectedOutput`, `stepId`, or capability list;
- task text is too short to be actionable;
- requested capabilities exceed the parent or system policy;
- a non-CodeAgent delegation requests file write capability in first-stage implementation;
- duplicate `clientTaskId` exists in the same action.

Rejected delegation should create a worklog failure and continue to MainAgent for replan, unless the error is a contract failure.

### 11.5 Parent Runtime Behavior

On valid `DELEGATE_AGENTS`:

```text
create child runs
write parent worklog DELEGATE_AGENTS / WAITING_CHILDREN
write waitingChildren records
set parent run status/phase to WAITING_CHILDREN
pause parent MainAgent loop
start child runtimes
```

The parent does not final-answer while waiting.

### 11.6 WAITING_CHILDREN

Add or model a waiting state equivalent to:

```text
WAITING_CHILDREN
```

This state is analogous to `WAITING_USER`, but its wakeup source is child completion rather than user input.

When all children finish with commit/fail/cancel:

```text
update parent waitingChildren
write parent worklog
write evidence
resume parent run
project state view
call MainAgent
```

### 11.7 Child TaskAgent Runtime

TaskAgent may reuse parts of the general Runtime loop, but it does not need MainAgent's full memory lifecycle.

Recommended TaskAgent context:

- task spec;
- parent run id and child run id;
- allowed capabilities;
- context seed;
- transferred parent evidence;
- child notebook/worklog/evidence;
- full tool results from its own actions.

TaskAgent should not use long-term memory/RAG lifecycle by default.

It may use read-only tools or RAG recall only when granted.

### 11.8 Child COMMIT Action

TaskAgent completes with:

```json
{
  "action": "COMMIT",
  "stateDelta": {
    "result": {
      "status": "SUCCEEDED",
      "briefResult": "...",
      "fullResult": "...",
      "fullResultRef": "payload-child-full-result-001",
      "createdEvidenceIds": [],
      "recommendedNextSteps": []
    }
  }
}
```

For file/code/research tasks:

- full result must be preserved;
- child tool evidence refs must be transferred or accessible to parent;
- parent must not receive only a brief result.

### 11.9 Parent Ingestion Of Child Commit

When child commits:

```text
parent worklog: SUBAGENT_COMMITTED
parent evidencePack: SUBAGENT_COMMIT
waitingChildren child status: DONE
if all WAIT_ALL children resolved: wake parent MainAgent
```

MainAgent then updates notebook through `perUpdate`.

Runtime must not mark notebook steps done merely because child committed. MainAgent decides semantic completion.

### 11.10 Child ASK_USER

TaskAgent may request user input only through the same pending-input system.

If a child waits for user:

```text
child run: WAITING_USER
parent waitingChildren child status: WAITING_USER
frontend shows which child/task asks
user reply resumes child
parent remains WAITING_CHILDREN until all children resolved
```

## 12. State View Projection

### 12.1 Required StateView Fields

`MainAgentStateViewVO` should eventually include:

```text
notebook
worklog
evidencePack
waitingChildren
```

Existing fields remain:

```text
runMeta
userInput
conversation
memoryPack
ragPack
resolvedArtifacts
artifactContent
userClarifications
availableCapabilities
pendingAction
previousLoopOutcome
actionHistory
currentPlan
lastVerifierFeedback
tokenBudget
failure
```

`currentPlan` may remain during migration but should not be the canonical PER state.

### 12.2 Budgeting

StateView projection must obey token budget.

For worklog:

- include latest and relevant records;
- preserve sequence;
- include request/status/evidence ids;
- include full request/result refs when details are not inlined.

For evidence:

- inline original content when within budget;
- mechanically truncate when over budget;
- set `truncated=true`;
- include `contentRef` and size.

Do not use LLM summarization in projection.

### 12.3 Ordering

Project worklog and evidence sorted by sequence ascending.

Prompt may instruct MainAgent to focus on latest records, but state should remain ordered.

## 13. Example Flow: Folder Summary And Write File

User:

```text
Please inspect the domain folder contents in this project, summarize it, and write a txt file to Desktop.
```

Correct behavior:

1. MainAgent enters PER mode.
2. First step resolves which folder the user means by "domain folder".
3. MainAgent searches/lists project root rather than assuming a specific path.
4. If multiple candidates are found, MainAgent asks user.
5. After target is resolved, MainAgent lists folder structure.
6. Runtime stores directory listing in evidence.
7. MainAgent reads evidence and updates notebook.
8. MainAgent reads key files through tools.
9. Runtime stores full file contents or budget-bounded original content plus content refs.
10. MainAgent generates summary from evidence.
11. MainAgent calls write tool for Desktop txt.
12. Runtime handles approval if required.
13. After write succeeds, Runtime writes worklog and evidence.
14. MainAgent updates notebook and returns final answer.

Important:

- The first action should not directly read a guessed module path.
- Tool results must be visible enough for MainAgent to summarize.
- Write-file approval must remain Runtime-owned.
- Final answer must go through final delivery guard.

## 14. Example Flow: RAG And MCP Review With TaskAgents

User:

```text
Please inspect the RAG and MCP implementations in this project for obvious risks and give me a combined improvement recommendation.
```

Correct behavior:

1. MainAgent enters PER mode.
2. MainAgent creates plan:
   - inspect RAG implementation;
   - inspect MCP implementation;
   - combine results.
3. MainAgent emits `DELEGATE_AGENTS` with `WAIT_ALL`.
4. Runtime creates two TaskAgents:
   - RAG risk review;
   - MCP risk review.
5. Parent run enters `WAITING_CHILDREN`.
6. Each TaskAgent has an atomic task and read-only capabilities.
7. Each TaskAgent uses full local context/tool results for its small task.
8. Each TaskAgent returns `COMMIT`.
9. Parent Runtime stores each commit in worklog and evidence pack.
10. Parent wakes MainAgent when all children resolve.
11. MainAgent reads child evidence, updates notebook, and returns final combined recommendation.

Important:

- Child result must preserve full useful content for file/code/research tasks.
- Parent MainAgent, not child, writes the final answer.
- Parent Runtime, not MainAgent, manages waiting and wakeup.

## 15. Prompt Changes

### 15.1 MainAgent Prompt Requirements

MainAgent prompt must explain:

- its role as PER semantic controller;
- relationship between notebook, worklog, evidencePack, memoryPack, ragPack, and userClarifications;
- direct mode for simple tasks;
- PER mode for complex/multi-action tasks;
- how to read sequence/order;
- how to avoid duplicate tool calls;
- target resolution rule for file/directory references;
- how to delegate atomic TaskAgent work;
- child-agent `WAIT_ALL` behavior;
- `PLAN` as compatibility weak action;
- `perUpdate` as notebook update mechanism.

### 15.2 Required Reading Order In Prompt

Prompt should instruct MainAgent:

1. Read user input and notebook.
2. Read new worklog records by sequence.
3. Follow worklog evidence ids to evidencePack.
4. Read user clarifications.
5. Read memoryPack/ragPack when relevant.
6. Update notebook through `perUpdate`.
7. Choose one action.

### 15.3 No Hidden Reasoning Requirement

`perUpdate` must not ask for chain-of-thought.

It should contain concise, structured task-state notes:

- goal;
- step status;
- facts learned;
- open questions;
- risks;
- last decision.

It must not contain long hidden reasoning, prompt text, or private debug details.

## 16. Implementation Phases

### 16.1 Phase A: PER Notebook And Worklog Foundation

Goal: make MainAgent able to remember current-run plan and action results.

Tasks:

1. Add PER/notebook/worklog VOs.
2. Extend `MainAgentActionVO` or parsing/mapping model to include `perUpdate`.
3. Extend `RunWorkingStateVO` with notebook, worklog, evidence sequence counters.
4. Extend `MainAgentStateViewVO` with notebook and worklog.
5. Implement `PerUpdateMergeService` or equivalent inside Runtime/domain service.
6. Update `RunWorkingStateManager.apply/project`.
7. Keep `actionHistory` compatibility.
8. Update MainAgent prompt.
9. Add contract tests for `perUpdate`.
10. Add projection tests proving notebook/worklog/evidence appear in next loop.

Acceptance:

- `perUpdate` updates notebook.
- notebook appears in next MainAgent state view.
- worklog records action execution with sequence and loop index.
- existing memory/RAG/MCP tests continue passing.

### 16.2 Phase B: MCP Full Result Evidence Projection

Goal: make tool results visible enough for MainAgent.

Tasks:

1. Preserve full MCP tool result in payload.
2. Convert tool result into `TOOL_RESULT` evidence.
3. Add worklog details for `CALL_TOOL`.
4. Include repeat guard key.
5. Project budget-bounded original evidence content into state view.
6. Mark truncation and content refs.
7. Update prompt duplicate-tool rules.
8. Add tests for read_file-like full content projection.
9. Add tests for duplicate successful tool call avoidance data.

Acceptance:

- MainAgent next loop can see read_file content when within budget.
- Large results preserve full `contentRef` and visible truncation flag.
- Runtime does not use LLM summarization.
- Same tool args create same repeat guard key.

### 16.3 Phase C: PLAN Compatibility

Goal: preserve `PLAN` while moving notebook updates to `perUpdate`.

Tasks:

1. Update `PlanActionHandler` to rely on `perUpdate`/notebook state where appropriate.
2. Keep existing `PlanStatePort` if still needed for trace/payload persistence.
3. Prevent repeated empty PLAN loops.
4. Update tests.

Acceptance:

- PLAN updates notebook or requires meaningful `perUpdate`.
- PLAN does not final-answer.
- PLAN does not become the only way to update notebook.

### 16.4 Phase D: Agent Harness Abstraction Design

Goal: design the reusable agent runtime foundation before any MainAgent-to-child delegation is implemented.

Tasks:

1. Define reusable `AgentRuntime` lifecycle boundaries.
2. Define which phases MainAgent, TaskAgent, and future CodeAgent share.
3. Define which phases MainAgent alone owns, especially user-facing final delivery.
4. Define child-agent memory mode as full in-run context rather than MainAgent's long-term memory lifecycle.
5. Define agent capability tables and Runtime enforcement.
6. Define agent-local notebook/worklog/evidence.
7. Define child `COMMIT` contract and parent ingestion.
8. Define child `ASK_USER` through the existing pending-input pause/resume mechanism.
9. Define recovery/resume behavior for parent and child runtimes.
10. Define how TaskAgent can run and commit independently before MainAgent receives `DELEGATE_AGENTS`.

Acceptance:

- Agent Harness abstraction spec is written and reviewed;
- implementation boundaries are clear enough to avoid copying the MainAgent chain wholesale;
- TaskAgent implementation plan is not written until this abstraction is accepted.

### 16.5 Phase E: TaskAgent WAIT_ALL Design Implementation

Goal: add first-stage child-agent delegation after Agent Harness abstraction approval.

Tasks:

1. Add `DELEGATE_AGENTS` action enum and stateDelta contract.
2. Add delegation VOs.
3. Add waiting child VOs.
4. Add `WAITING_CHILDREN` phase/status handling or equivalent paused state.
5. Implement parent runtime delegation handler.
6. Implement child TaskAgent runtime entry.
7. Add child `COMMIT` action.
8. Implement parent ingestion of child commit/fail.
9. Add pending-input support for child `ASK_USER`.
10. Add state-view projection for waiting children and child commits.
11. Add minimal tests for parent wait/wakeup and child commit ingestion.

Acceptance:

- parent blocks on `WAIT_ALL`;
- child commit creates parent worklog and evidence;
- parent wakes only after all children resolve;
- child cannot final-answer user;
- child task validation enforces atomic task requirements.

### 16.6 Phase F: Frontend And API Follow-Up

Goal: expose new runtime states cleanly.

Tasks:

1. Display parent waiting for child agents.
2. Display child pending user questions with child/task identity.
3. Avoid exposing raw internal state in normal UI.
4. Optionally add debug views for notebook/worklog/evidence.

Acceptance:

- normal UI shows clean progress and pending input;
- debug data remains isolated from normal final answer.

## 17. Testing Requirements

### 17.1 Required Unit Tests

Keep tests focused. Do not run or write broad full-suite tests for every phase. Add or update only the minimal tests needed for the files changed in the current implementation slice.

For Phase A/B/C, the required unit tests are:

- `PerUpdateMergeService`
- `RunWorkingStateManager`
- `MainAgentActionContract`
- `RuntimeWorkingStateProjection`
- `CallToolActionHandler`
- `ToolActionOrchestrator`
- `ToolEvidenceConverter`
- `RuntimeRepeatedActionGuard`
- `PlanContinueActionHandler`

Only add `StateDeltaScopeRules` tests when allowed fields are changed. Only add child-agent tests after Phase E begins.

### 17.2 Required Scenario Tests

For Phase A/B/C, keep scenario tests minimal:

1. Simple direct answer:
   - `DIRECT + FINAL`
   - no heavy notebook steps required.

2. Tool read then final:
   - `CALL_TOOL read_file`
   - result evidence visible in next state view
   - MainAgent final uses evidence.

3. Duplicate tool prevention:
   - same repeat guard key succeeded
   - next loop state view contains enough data to avoid repeat.

4. Folder target resolution:
   - user says ambiguous folder name
   - MainAgent searches first
   - asks user if multiple targets.

5. PLAN compatibility:
   - PLAN with perUpdate updates notebook
   - empty PLAN rejected or safe-failed.

6. ASK_USER resume preserves PER state:
   - MainAgent asks the user;
   - Runtime pauses;
   - resume restores notebook/worklog/evidence and appends the user clarification;
   - next MainAgent state view can still see the prior PER state.

Deferred Phase E scenario:

1. TaskAgent WAIT_ALL:
   - parent delegates two tasks
   - parent waits
   - child commits write evidence
   - parent wakes and final-answers.

### 17.3 Verification

Before claiming implementation complete:

- run targeted tests for changed areas;
- run `mvn -q -DskipTests compile` when feasible;
- report any skipped tests honestly.

## 18. Migration Notes

### 18.1 Do Not Break Existing Modules

Implementation must not break:

- current memory preparation;
- current RAG recall injection;
- current MCP configuration;
- current `CALL_TOOL` action entry;
- current pending input behavior;
- current final delivery guard;
- current frontend normal mode boundaries.

### 18.2 Recommended Migration Strategy

Implement in this order:

1. Add VOs and projection fields without changing behavior.
2. Add perUpdate parsing and merge.
3. Add worklog projection while keeping actionHistory.
4. Enhance tool evidence full result capture.
5. Update prompt and contract tests.
6. Preserve PER state across `ASK_USER` pause/resume.
7. Write the Agent Harness abstraction spec.
8. Add TaskAgent support only after Agent Harness approval.

### 18.3 Compatibility Fields

During migration:

- keep `currentPlan`;
- keep `actionHistory`;
- keep existing evidence summary/snippet fields;
- add canonical notebook/worklog/evidence fields alongside them.

Remove or deprecate old fields only after tests and user review confirm the new path is stable.

## 19. Open Decisions For Later Discussion

The following are intentionally deferred:

- exact database tables for persistent notebook/worklog snapshots;
- whether notebook is persisted after run completion as memory candidate;
- Agent Harness implementation details beyond the abstraction spec;
- TaskAgent implementation before Agent Harness approval;
- CodeAgent design;
- `WAIT_ANY`;
- background child agents;
- child agent ability to call other child agents;
- full frontend debug visualization.

These must not block Phase A/B.

## 20. Final Design Summary

The target design is:

```text
RunWorkingState = authoritative in-run state
MainAgentStateView = read-only loop projection
Notebook = MainAgent PER task cognition
Worklog = Runtime action execution ledger
EvidencePack = original materials produced by actions
perUpdate = MainAgent notebook patch
CALL_TOOL result = original MCP result saved as evidence and visible next loop
DELEGATE_AGENTS = future parent action after Agent Harness approval; parent creates TaskAgents and waits with WAIT_ALL
COMMIT = future child action; child returns result to parent, not to user
```

This design gives MainAgent process memory and global task awareness without replacing the current Memory/RAG/MCP modules.
