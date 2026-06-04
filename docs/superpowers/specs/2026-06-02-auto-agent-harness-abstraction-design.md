# AutoAgent Agent Harness Abstraction Design

Status: Draft Spec, ready for user review before implementation plan.

Primary audience: Codex and future AI coding agents implementing this repository.

Related references:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`
- `docs/architecture/auto-agent-prompt-harness-governance-spec.md`
- `docs/superpowers/specs/2026-06-02-auto-agent-per-notebook-worklog-subagent-design.md`
- `docs/superpowers/plans/2026-06-02-auto-agent-per-notebook-worklog-implementation.md`
- `docs/superpowers/progress/2026-06-02-auto-agent-per-notebook-worklog-progress.md`

## 0. Purpose

This spec defines the next design stage after the PER notebook/worklog/evidence foundation.

The goal is to abstract the existing AutoAgent main-loop harness into reusable agent runtime building blocks before implementing child-agent delegation.

This document intentionally does not implement:

- `DELEGATE_AGENTS`;
- `WAITING_CHILDREN`;
- child `COMMIT`;
- TaskAgent runtime;
- CodeAgent runtime;
- frontend child-agent display.

Those require an implementation plan after this abstraction is reviewed.

## 1. Non-Negotiable Rules

- Runtime remains deterministic Java orchestration.
- LLM nodes emit structured actions; they do not mutate persistence or lifecycle state directly.
- MainAgent remains the only agent that can deliver final user-facing answers through final delivery and guard logic.
- Child agents must not directly answer the user. They return structured `COMMIT` results to their parent Runtime.
- All agents use Runtime-owned action routing, permission checks, pending input, worklog, evidence, and checkpoint/resume semantics.
- RAG remains primarily a memory/context injection capability for MainAgent. RAG action remains a fallback.
- MCP tools remain Java Runtime-owned. No node mounts MCP tools directly.
- Runtime never invents semantic summaries for evidence. Runtime may store original text, structured tool output, status, ids, refs, and mechanical metadata.
- TaskAgent design must be small and parent-scoped first. Persistent CodeAgent design is a later specialized stage.

## 2. Current Code Anchors

The abstraction must be extracted from the current main-loop architecture rather than invented as a separate subsystem.

Important current anchors:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/DefaultAutoAgentRuntimeService.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/RunWorkingStateManager.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/RunWorkingStateVO.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/context/MainAgentStateViewVO.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/node/mainagent/MainAgentNodeService.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/context/ContextPreparationService.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/interaction/MainAgentPendingInputHandler.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/interaction/ToolApprovalPendingInputHandler.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/tool/ToolActionOrchestrator.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/tool/ToolRuntime.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/rag/runtime/RagRuntime.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/prompt/MainAgentPromptBuilder.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/prompt/OutputContractPromptRenderer.java`

The future implementation should not duplicate these flows in parallel. It should extract stable interfaces and compose them per agent type.

## 3. Core Model

### 3.1 Agent Runtime

`AgentRuntime` is the reusable execution harness for one agent run.

It owns:

- run identity;
- lifecycle status and phase;
- working state;
- state-view projection;
- node invocation;
- action validation;
- action routing;
- capability enforcement;
- pending-input pause/resume;
- checkpoint persistence;
- worklog and evidence updates;
- completion handling.

It does not own:

- LLM semantic decisions;
- final user delivery for child agents;
- MCP execution semantics beyond routing to existing tool modules;
- RAG indexing or memory ingestion;
- frontend rendering.

### 3.2 Agent Profile

`AgentProfile` defines a runtime template, not a hard-coded list of child agents MainAgent must choose from.

Recommended profile categories:

| Profile | Purpose | Persistence | Completion |
|---|---|---|---|
| `MAIN_AGENT` | Primary session controller | session/run scoped | `FINAL` to user |
| `TASK_AGENT` | Temporary delegated atomic worker | parent-run scoped | `COMMIT` to parent |
| `CODE_AGENT` | Future coding specialist | workspace/session scoped | `COMMIT` or guarded code-work result to parent |
| `READ_ONLY_AGENT` | Optional future inspection worker | parent-run scoped | `COMMIT` to parent |

MainAgent should be able to create custom TaskAgents by naming them and granting capabilities within policy bounds. The system may provide templates, but templates must not become the only available child choices.

### 3.3 Agent Run Identity

Every run needs identity fields sufficient for parent/child coordination:

```text
runId
sessionId
agentRunId
agentType
agentName
parentRunId
parentAgentRunId
rootRunId
workspaceId
createdByActionWorkId
```

MainAgent runs have no parent. TaskAgent runs always have a parent.

### 3.4 Agent Working State

Every agent run has local working state.

Shared fields:

```text
notebook
worklog
evidencePack
userClarifications
previousLoopOutcome
nextSequence
checkpointPayload
```

MainAgent additionally owns:

```text
conversation context
memoryPack
ragPack from memory/context preparation
waitingChildren
final delivery candidate state
```

TaskAgent additionally owns:

```text
taskSpec
parentContextSeed
grantedCapabilities
transferredEvidenceIds
commitCandidate
```

## 4. Composable Harness Units

The implementation should evolve toward these units.

### 4.1 Context Strategy

Determines what input context is available before an agent loop.

MainAgent context strategy:

- current user input;
- recent messages and summaries;
- session task summary;
- memoryPack;
- RAG recall through memory/context preparation;
- current run notebook/worklog/evidence;
- waiting child state and child commit evidence.

TaskAgent context strategy:

- parent-assigned task spec;
- parent context seed;
- selected parent evidence;
- local child notebook/worklog/evidence;
- local userClarifications;
- optional RAG/tool outputs only when capability-granted.

TaskAgent should not run the full MainAgent long-term memory lifecycle by default.

### 4.2 State View Projector

Builds the read-only view passed to the agent node.

MainAgent uses `MainAgentStateViewVO` during the migration phase.

TaskAgent should use a dedicated future `TaskAgentStateViewVO` rather than forcing every child field into `MainAgentStateViewVO`.

Shared projection rules:

- include notebook;
- include ordered worklog;
- include evidence content or refs;
- include userClarifications;
- include available capabilities;
- preserve sequence ordering;
- obey context budget;
- mark truncated content mechanically;
- never use LLM summarization.

### 4.3 Node Invoker

Invokes the correct LLM node for the agent profile.

Current MainAgent path:

```text
MainAgentNodeService -> NodeInvocationPipeline -> MainAgentActionVO
```

Future TaskAgent path:

```text
TaskAgentNodeService -> NodeInvocationPipeline -> TaskAgentActionVO
```

TaskAgent should have its own component code, prompt builder, and output contract. It may reuse parser, repair, profile, and prompt assembly infrastructure.

### 4.4 Action Router

Routes structured actions to deterministic handlers.

Shared route families:

- `CALL_TOOL`
- `ASK_USER`
- `RETRIEVE_RAG` when granted
- `PLAN`
- `CONTINUE`
- `FAIL`

MainAgent-only route families:

- `FINAL`
- `REPAIR_FINAL`
- `DELEGATE_AGENTS` after implementation approval

TaskAgent-only route families:

- `COMMIT`

Route ownership rule:

```text
Agent node chooses action.
Runtime validates capability and contract.
Runtime handler executes deterministic effect.
Runtime updates local working state.
Runtime decides continue, wait, commit, final, or fail.
```

### 4.5 Capability Policy

Capability enforcement should be simple but strict.

Use two enforced layers:

1. System/user upper bound.
2. Runtime grant for this agent run.

Profile templates may suggest defaults, but they are not an enforcement layer.

Examples:

MainAgent upper bound may include:

```text
CALL_TOOL
ASK_USER
RETRIEVE_RAG
DELEGATE_AGENTS
FINAL
PLAN
CONTINUE
FAIL
```

TaskAgent default upper bound should exclude:

```text
FINAL
REPAIR_FINAL
DELEGATE_AGENTS
high-risk write/delete tools unless explicitly granted in a later phase
```

TaskAgent actual grants are created by MainAgent delegation request and then intersected with system/user upper bound.

### 4.6 Completion Policy

Completion differs by agent type.

MainAgent completion:

```text
FINAL -> FinalResponseGuard -> FinalDelivery -> assistant message -> run COMPLETED
FAIL -> safe failure path -> assistant message or failure response
```

TaskAgent completion:

```text
COMMIT -> child run completed -> parent worklog/evidence updated -> parent wait set checked
FAIL -> child failed -> parent worklog/evidence updated -> parent wait set checked
```

TaskAgent cannot create a normal assistant message.

### 4.7 Checkpoint Strategy

All agents need checkpoint/resume.

Shared checkpoint payload should include:

```text
agent identity
agent profile
working state
pending action
pending input id
loop index
capability grants
parent wait metadata when child
```

MainAgent `ASK_USER` resume continues the MainAgent loop.

TaskAgent `ASK_USER` resume continues the TaskAgent loop. If the parent is waiting for that child, the parent remains paused until the child commits, fails, or is cancelled.

## 5. Pending Input

`ASK_USER` and tool approval must use the existing Runtime pending-input system.

Required behavior:

- pending input records must identify the source agent run;
- frontend can show which agent/task is asking;
- user replies route back to the correct continuation handler;
- Java normalizes option clicks/free text/approval decisions;
- resumed agent receives the answer in its local state view;
- parent run is not resumed merely because a child got a user answer.

High-risk tool approval rule remains:

```text
Only deterministic approve/reject options authorize high-risk tool execution.
Free text cannot authorize high-risk tool execution.
```

## 6. Parent And Child Coordination

### 6.1 Delegation Is A Runtime Action

MainAgent will eventually emit `DELEGATE_AGENTS`.

Runtime will:

- validate delegation contract;
- validate requested capabilities;
- create child agent runs;
- write parent worklog;
- create parent waiting records;
- start child runtimes;
- pause parent when wait policy is `WAIT_ALL`.

MainAgent does not manually create threads or mutate child state.

### 6.2 First Wait Policy

The first implementation must support only:

```text
WAIT_ALL
```

Current-answer child work must not be detached in the first version. If MainAgent delegates work for the current user answer, the parent run waits until all children resolve.

### 6.3 Waiting Records

Parent working state needs waiting records equivalent to:

```text
childRunId
clientTaskId
agentName
task
stepId
status
startedAt
completedAt
commitEvidenceId
failureCode
failureMessage
```

Statuses:

```text
CREATED
RUNNING
WAITING_USER
COMMITTED
FAILED
CANCELLED
```

### 6.4 Wakeup

When a child resolves:

1. child runtime persists completion;
2. parent runtime ingests child result;
3. parent worklog receives child completion item;
4. parent evidencePack receives child commit or failure material;
5. parent waiting record updates;
6. if all `WAIT_ALL` children resolved, parent resumes MainAgent loop.

Runtime must not mark notebook steps done automatically. MainAgent reads worklog/evidence and updates notebook with `perUpdate`.

## 7. Child COMMIT

`COMMIT` is the child-agent completion action.

Target shape:

```json
{
  "perUpdate": {
    "mode": "PER",
    "lastDecision": "Task finished and result is ready to commit."
  },
  "action": "COMMIT",
  "stateDelta": {
    "commit": {
      "status": "SUCCEEDED",
      "briefResult": "Short parent-readable result.",
      "fullResult": "Full result for parent MainAgent.",
      "fullResultRef": "payload-child-result-001",
      "createdEvidenceIds": ["ev-child-001"],
      "recommendedNextSteps": []
    }
  }
}
```

For file, code, research, or tool-heavy tasks:

- `fullResult` or `fullResultRef` is required;
- relevant child evidence must be transferred or visible to parent;
- parent must not receive only a short summary.

Runtime may mechanically store `briefResult`, `fullResult`, refs, ids, and status. Runtime must not rewrite the child result semantically.

## 8. TaskAgent

### 8.1 Purpose

TaskAgent is a temporary delegated worker for atomic work.

Good TaskAgent task:

```text
Inspect only the ToolRuntime -> ToolEvidenceConverter path. Return the call chain, exact visible fields, and up to 5 risks. Do not modify files.
```

Bad TaskAgent task:

```text
Review the whole project and improve it.
```

### 8.2 Task Spec Requirements

Every TaskAgent creation request must include:

```text
clientTaskId
name
task
expectedOutput
capabilities
stepId
contextSeed
```

Recommended optional fields:

```text
requiredEvidenceIds
scopeInclude
scopeExclude
outputFormat
maxLoops
```

Task text must be clear, bounded, and atomic.

### 8.3 Memory Mode

TaskAgent uses full in-run context, not MainAgent's long-term memory lifecycle.

It receives:

- task spec;
- parent seed;
- selected parent evidence;
- local child worklog/evidence;
- local user answers;
- granted tool/RAG outputs.

It should not receive:

- all session history by default;
- unrelated user long-term memories;
- unrelated RAG knowledge.

### 8.4 Actions

First TaskAgent action set:

```text
CALL_TOOL
ASK_USER
RETRIEVE_RAG when granted
PLAN
CONTINUE
COMMIT
FAIL
```

Forbidden:

```text
FINAL
REPAIR_FINAL
DELEGATE_AGENTS
```

## 9. CodeAgent Boundary

CodeAgent is not TaskAgent.

CodeAgent is a future specialized agent for coding work.

Expected differences:

- workspace-bound;
- persistent per-workspace/task memory;
- coding-friendly full transcript plus compression;
- native read/write/edit file tools with Runtime narrow gate;
- command execution approval;
- stronger filesystem permissions;
- test/verification loop;
- code-work commit back to MainAgent or user-visible result.

This spec only reserves the boundary. It does not implement CodeAgent.

## 10. MainAgent Changes Required Later

After this abstraction is reviewed, MainAgent prompt and contract can add delegation behavior.

MainAgent must learn:

- when not to delegate simple work;
- when to ask user whether to use multi-agent delegation for larger work;
- when code modification should default to CodeAgent after CodeAgent exists;
- how to create atomic child tasks;
- how to grant bounded capabilities;
- how to wait for `WAIT_ALL`;
- how to read child commit evidence and update notebook.

Delegation should be used for multiple independent sub-tasks, not for every request.

## 11. Implementation Direction

The next implementation plan should proceed in this order.

### Phase 1: Extract Agent Harness Interfaces

Create reusable abstractions for:

- agent identity;
- agent profile;
- agent runtime command;
- context strategy;
- state-view projector;
- node invoker;
- action router;
- capability policy;
- completion policy;
- checkpoint strategy.

No child-agent behavior is required in this phase.

### Phase 2: Adapt MainAgent To The Harness

Rewire the current MainAgent path through the new harness units while preserving behavior.

Acceptance:

- existing PER tests pass;
- existing RAG/MCP paths pass;
- existing pending-input resume tests pass;
- MainAgent final delivery remains unchanged.

### Phase 3: Add TaskAgent Local Runtime

Add TaskAgent as an internal runtime that can run from a direct Java test command and complete with `COMMIT`.

No MainAgent delegation yet.

Acceptance:

- TaskAgent can receive a task;
- TaskAgent can use granted read-only tool/RAG capability;
- TaskAgent can ask user and resume;
- TaskAgent can commit;
- TaskAgent cannot final-answer user.

### Phase 4: Add Parent Delegation

Add `DELEGATE_AGENTS` and parent `WAIT_ALL` only after Phase 3 works.

Acceptance:

- parent creates children;
- parent waits;
- children commit;
- parent ingests commits into worklog/evidence;
- parent wakes and MainAgent produces final answer.

## 12. Test Strategy

Keep tests targeted.

Required test categories:

- harness unit selection for MainAgent;
- capability intersection;
- child forbidden `FINAL`;
- child `COMMIT` ingestion;
- child `ASK_USER` resume;
- parent `WAIT_ALL` wakeup;
- existing PER runtime tests;
- existing MCP/RAG tests that prove compatibility.

Do not add broad end-to-end tests until the small harness units are stable.

## 13. Package Placement

Suggested domain packages:

```text
domain/agent/model/valobj/agent/
domain/agent/model/valobj/agent/runtime/
domain/agent/model/valobj/agent/capability/
domain/agent/service/runtime/agent/
domain/agent/service/runtime/agent/context/
domain/agent/service/runtime/agent/action/
domain/agent/service/runtime/agent/capability/
domain/agent/service/node/taskagent/
```

Data carriers must stay under `model/**`.

Behavior classes must stay under `service/**`.

Infrastructure persistence adapters, if needed later, must stay in `infrastructure`.

Trigger/API additions, if needed later, must stay in `trigger`.

## 14. Acceptance For This Spec

This spec is accepted when the user agrees that:

- AgentRuntime should be composed from shared harness units;
- MainAgent and child agents share Runtime mechanics but not final-delivery authority;
- TaskAgent is temporary, parent-scoped, and commit-only;
- CodeAgent is deferred and specialized;
- child pending input uses the same pending-input mechanism;
- MainAgent delegation implementation waits until harness abstraction and TaskAgent local runtime are stable.

## 15. Summary

The target architecture is:

```text
Shared AgentRuntime mechanics
  -> MainAgent profile: memory/context lifecycle + final delivery + future delegation
  -> TaskAgent profile: parent-scoped context + bounded capabilities + COMMIT
  -> CodeAgent profile: future workspace-bound coding runtime
```

This keeps the project from splitting into two unrelated systems while still allowing CodeAgent and TaskAgent to have different memory, capability, and completion behavior.
