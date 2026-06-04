# AutoAgent Subagent Harness Design Spec

Status: Draft Spec, pending review.

Primary scope: generic subagent scheduling and Agent Harness abstraction.

Related references:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`
- `docs/architecture/auto-agent-prompt-harness-governance-spec.md`
- `docs/superpowers/specs/2026-05-20-auto-agent-memory-lifecycle-design.md`

## 0. Purpose

This spec promotes subagent scheduling from backlog into a dedicated design track.

The goal is not to add a narrow "call subagent" patch to `MainAgent`. The goal is to introduce an Agent Harness model that can run the current `MainAgent` behavior, generic temporary subagents, and future persistent specialized agents through a coherent orchestration protocol.

This spec focuses on:

- preserving the current `MainAgent` behavior after refactoring;
- introducing generic subagent scheduling;
- defining parent-child agent coordination;
- defining profile, memory, capability, action, and completion policies;
- reserving a bridge for future `CodeAgent` integration.

The internal `CodeAgent` harness is out of scope for this spec. It should be designed later as a Claude Code / claw-code style coding harness and connected through the bridge defined here.

## 1. Core Principles

### 1.1 MainAgent Remains The Canonical User-Facing Orchestrator

All user messages still enter through `MainAgent` by default.

`MainAgent` remains responsible for:

- understanding the user request;
- using the existing memory/RAG injection lifecycle;
- using PER notebook, worklog, and evidence;
- deciding whether to answer directly, call tools, ask the user, retrieve RAG, or delegate work;
- producing final user-visible answers through the final delivery path.

Refactoring the current MainAgent runtime is allowed. Interface changes, component extraction, and internal restructuring are allowed. However, after migration, MainAgent behavior must remain equivalent for non-subagent flows:

```text
User request
  -> current memory/RAG/context preparation semantics
  -> state view assembly
  -> MainAgent node invocation
  -> action validation and runtime routing
  -> RAG/MCP/ASK_USER/PER/notebook/worklog/evidence/final behavior
```

If `MainAgent` does not choose delegation, the user-visible flow and available capabilities must behave as they do in the current main-loop runtime.

### 1.2 Runtime Is Deterministic

Runtime and harness services are deterministic Java orchestration.

They may:

- build input views;
- validate contracts;
- route actions;
- enforce permissions;
- persist memory/worklog/evidence/notebook facts;
- create pending input;
- resume the correct agent run;
- coordinate parent and child runs.

They must not:

- use LLM calls to decide hidden business semantics;
- trust prompt-only permission rules;
- synthesize tool success;
- let subagents directly produce user-facing final answers.

### 1.3 Subagents Commit To Their Parent

Generic subagents do not `FINAL` to the user.

They complete by emitting `COMMIT`, which returns structured work results to the parent agent runtime.

Only the top-level `MainAgent` may produce user-visible final answers, and those answers must still pass the existing final delivery and guard path.

### 1.4 CodeAgent Is A Specialized Future Agent

`CodeAgent` should not be forced into the MainAgent PER/one-loop structure.

Future `CodeAgent` should use a coding-oriented harness inspired by Claude Code / claw-code:

- workspace-scoped project context;
- todo/task tracking;
- code-native read/search/edit tools;
- patch/edit application;
- command and test execution;
- code-oriented approval and verification loops;
- persistent project memory.

This spec only defines how `MainAgent` and `AgentDispatchRuntime` can call a future `CodeAgent` and receive its commit. The internal `CodeAgent` loop, tools, and memory model belong in a later CodeAgent spec.

### 1.5 Workspace Is A Boundary Object

Workspace is not only a UI selection. It is the authoritative boundary for:

- project context;
- future CodeAgent memory;
- filesystem scope;
- MCP file tool permissions;
- future cross-boundary approval requests.

Frontend may allow users to select an existing project, add a project, or choose "do not use project". Selecting a workspace does not bypass `MainAgent`; it gives `MainAgent` a current workspace scope in the state view.

## 2. Target Architecture

### 2.1 High-Level Flow

```text
User message
  -> MainAgent runtime/harness
  -> MainAgent emits one action
  -> Runtime routes action
     -> existing actions: FINAL / CALL_TOOL / RETRIEVE_RAG / ASK_USER / etc.
     -> new action: DELEGATE_AGENTS
     -> reserved action: DELEGATE_CODE_AGENT
  -> AgentDispatchRuntime creates child runs
  -> child agents run asynchronously
  -> child agents COMMIT or FAIL
  -> parent run worklog/evidence/state view is updated
  -> parent MainAgent is resumed when wait policy is satisfied
  -> MainAgent replans or finalizes
```

### 2.2 Main Components

| Component | Responsibility |
|---|---|
| `AgentRuntimeHarness` | Shared skeleton for running an agent according to an `AgentProfile`. |
| `AgentProfile` | Defines an agent's type, memory policy, capability policy, action policy, prompt/contract policy, and completion policy. |
| `AgentDispatchRuntime` | Creates child agent runs, records parent-child relations, applies wait policy, routes child commits back to the parent, and wakes parent runs. |
| `GenericSubAgentHarness` | Runs temporary full-context subagents using the generic harness model. |
| `CodeAgentBridge` | Reserved adapter boundary for future workspace-scoped CodeAgent. |
| `ParentChildRunRegistry` | Persists parent-child run relationships and waiting status. |
| `AgentCapabilityResolver` | Computes effective capabilities from requested, profile, system, workspace, and approval constraints. |
| `AgentActionRouter` | Routes validated actions according to the current agent profile. |
| `AgentMemoryPolicy` | Prepares input view and records step facts according to agent type. |
| `AgentCompletionPolicy` | Decides whether an agent can final, commit, wait, continue, fail, or cancel. |

## 3. AgentProfile Model

`AgentProfile` is the main extension point. The runtime should avoid scattered logic such as:

```text
if agentType == MAIN
if agentType == SUB
if agentType == CODE
```

Instead, each agent run binds to a profile:

```text
AgentProfile =
  identity
  + memory policy
  + context policy
  + capability policy
  + action policy
  + prompt policy
  + contract policy
  + completion policy
  + parent-child policy
```

### 3.1 MainAgentProfile

The first profile is the migrated `MainAgent`.

Expected behavior:

- uses the existing main-agent memory lifecycle;
- keeps RAG as memory injection first, with explicit RAG action retained;
- keeps MCP tool action flow through Runtime/ToolRuntime;
- keeps PER notebook/worklog/evidencePack;
- may ask the user;
- may delegate generic subagents;
- may later delegate CodeAgent;
- may final to the user through final delivery;
- does not commit to a parent.

### 3.2 GenericSubAgentProfile

Generic subagents are temporary delegated workers.

Expected behavior:

- created by `MainAgent`;
- receives a clear atomic task;
- uses full-context memory for its own run;
- may use only effective capabilities granted at creation time;
- may call allowed tools or RAG;
- may ask the user through unified pending input;
- cannot final to the user;
- must end with `COMMIT` or `FAIL`;
- should not create more subagents in the first version.

### 3.3 CodeAgentBridgeProfile

This profile is a reserved bridge, not a full internal design.

Expected behavior:

- created or resumed by workspace;
- receives a task from `MainAgent`;
- uses future CodeAgent runtime internally;
- can ask the user through the shared pending-input system;
- can request permissions through the shared approval system;
- returns a structured commit to `MainAgent`;
- does not final directly to the user.

## 4. Memory And Context Policies

### 4.1 MainAgent Memory Policy

`MainAgent` continues to use the current memory and context preparation model:

- short-term and long-term memory candidates;
- RAG recall injection through the memory lifecycle;
- current run PER notebook;
- runtime worklog;
- evidencePack;
- available capabilities;
- current workspace if selected.

This policy may be extracted behind a common interface, but its semantics must stay equivalent.

### 4.2 Generic Subagent Full-Context Memory Policy

Generic subagents do not use MainAgent's memory lifecycle.

They use a simple full-context policy:

```text
Initial context =
  parent task
  + parent-provided background
  + parent-provided evidence snippets or payloads
  + effective capabilities
  + workspace scope if present

Each loop appends =
  node output
  + action request
  + action result
  + tool result
  + RAG result
  + ASK_USER answer
  + failure/repair facts
  + final COMMIT/FAIL payload
```

The next subagent turn loads its own full context. This keeps generic subagents simple because their tasks should be small.

### 4.3 Future CodeAgent Memory Policy

Future CodeAgent uses workspace-scoped persistent memory, not MainAgent memory.

It should persist:

- workspace tasks;
- project structure understanding;
- files read;
- edits made;
- commands/tests run;
- failures and repairs;
- user preferences for the workspace;
- unfinished todo items.

The implementation details are out of scope for this spec.

## 5. Capability And Permission Policy

### 5.1 Capability Layers

Effective capabilities are computed, not trusted from prompt text.

```text
effectiveCapabilities =
  requestedCapabilities
  INTERSECT agentProfileMaximum
  INTERSECT systemAllowedCapabilities
  INTERSECT workspaceScope
  INTERSECT approvalGrants
```

The node prompt may describe available capabilities, but Runtime is the source of truth.

### 5.2 Suggested Capability Categories

The first version should keep categories coarse:

| Capability | Meaning |
|---|---|
| `RAG` | May request RAG retrieval. |
| `MCP_TOOL` | May call allowed MCP tools. |
| `FILE_READ` | May read files inside allowed scope. |
| `FILE_WRITE` | May write files inside allowed scope after policy checks. |
| `ASK_USER` | May create pending input. |
| `DELEGATE_AGENTS` | May create generic subagents. |
| `DELEGATE_CODE_AGENT` | May call future CodeAgent bridge. |
| `COMMIT` | May return work result to parent. |
| `FINAL` | May produce final user-facing answer. |

`MainAgent` may have broad capabilities, but file and code operations still require workspace and tool policies.

Generic subagents receive only the capabilities requested by `MainAgent` and allowed by the generic subagent profile.

### 5.3 Workspace Scope

If an agent action touches a filesystem path, Runtime/ToolRuntime must check workspace scope.

```text
Path inside workspace allowed scope -> may continue to normal tool policy
Path outside workspace allowed scope -> reject or create an approval request
```

The first implementation may reject out-of-scope access. Future implementation may support scope escalation:

```text
Agent requests out-of-scope path
  -> Runtime creates approval pending input
  -> user approves temporary scope grant
  -> ToolRuntime retries with grant
```

## 6. Action Policy

### 6.1 MainAgent Actions

`MainAgent` keeps existing actions and gains delegation actions:

```text
FINAL
CALL_TOOL
RETRIEVE_RAG
ASK_USER
PLAN
CONTINUE
REPAIR_FINAL
FAIL
CREATE_ARTIFACT
UPDATE_ARTIFACT
DELEGATE_AGENTS
DELEGATE_CODE_AGENT (reserved)
```

`PLAN` remains available for compatibility, but PER notebook updates are the primary planning memory.

### 6.2 Generic Subagent Actions

Generic subagents use a smaller action set:

```text
CALL_TOOL
RETRIEVE_RAG
ASK_USER
CONTINUE
COMMIT
FAIL
```

They cannot use `FINAL`.

They should not use `DELEGATE_AGENTS` in the first implementation.

### 6.3 CodeAgent Bridge Actions

The future CodeAgent internal action vocabulary may be different.

The bridge only requires:

```text
CodeAgentRequest from parent
CodeAgentCommit to parent
CodeAgentAskUser through shared pending input
CodeAgentApproval through shared approval flow
CodeAgentFailure to parent
```

## 7. Delegation Contract

### 7.1 `DELEGATE_AGENTS`

`DELEGATE_AGENTS` is emitted by `MainAgent`.

It should contain:

- wait mode, initially `WAIT_ALL`;
- one or more child tasks;
- child display names chosen by `MainAgent`;
- each task's objective;
- task boundaries;
- required output form;
- requested capabilities;
- parent-provided context/evidence;
- workspace scope if applicable.

Child task requirements:

- atomic;
- clear;
- directly actionable;
- bounded;
- independently completable;
- not phrased as a broad user-level goal;
- explicit about whether full results or concise findings are required.

Bad child task:

```text
Analyze the whole project and tell me what to do.
```

Good child task:

```text
Read these three files and return the package responsibilities, important classes, and any unclear dependencies. Include the full relevant file excerpts you used.
```

### 7.2 `COMMIT`

`COMMIT` is emitted by a child agent.

It returns work to the parent runtime, not to the user.

It should contain:

- status: success, blocked, partial, failed;
- task id;
- concise result;
- detailed result when the parent requested full information;
- tool/RAG evidence references;
- files or resources inspected;
- assumptions;
- blockers;
- suggested parent next step;
- whether user-visible wording is safe to use.

Runtime records the commit in the child run and projects it into the parent run's worklog/evidence/state view.

Runtime must not invent a summary beyond deterministic metadata. If a commit needs semantic summary, the child agent must provide it.

## 8. Parent-Child Coordination

### 8.1 Wait Mode

The first version supports only `WAIT_ALL`.

```text
MainAgent emits DELEGATE_AGENTS with N tasks
  -> Runtime creates N child runs
  -> parent run enters WAITING_CHILDREN
  -> each child runs independently
  -> each child COMMIT/FAIL updates waiting registry
  -> when all children are terminal, parent is resumed
```

Future wait modes:

- `WAIT_ANY`
- `WAIT_QUORUM`
- background child runs
- parent continues while children run

These are not part of the first implementation.

### 8.2 Child Failure

A child failure should not automatically fail the parent run.

Instead:

- record child failure in child run;
- project failure into parent worklog;
- mark waiting item terminal;
- resume parent when wait policy is satisfied;
- let `MainAgent` decide whether to retry, ask user, delegate another agent, or answer with limitations.

Runtime should only fail the parent directly for infrastructure-level errors that make parent recovery impossible.

### 8.3 Parent Wakeup

When wait policy is satisfied:

```text
ParentChildRunRegistry marks parent ready
Runtime appends child result facts to parent state
Runtime resumes parent MainAgent loop
MainAgent receives updated notebook/worklog/evidence view
MainAgent replans or finalizes
```

## 9. ASK_USER Across Agents

All agents use the same pending-input system.

```text
Agent emits ASK_USER
  -> Runtime validates ask request
  -> pending input records sourceAgentRunId and sourceAgentType
  -> frontend shows the question and agent identity
  -> user answers
  -> Runtime normalizes answer in Java
  -> PendingInputContinuationDispatcher resumes the source agent run
```

Subagent questions must be clearly scoped. The UI may show that the question came from a delegated worker, but the worker still does not become a direct conversational agent.

High-risk approvals follow the same deterministic approval rules already used by tool approval.

## 10. State Projection

### 10.1 Child-To-Parent Projection

Child results are projected into the parent as execution facts:

- parent worklog item: child task status and timing;
- parent evidence entry: child commit payload or referenced payload;
- parent state view: latest child results visible to MainAgent;
- optional notebook update: only if MainAgent later updates notebook based on child facts.

Runtime should not mutate the parent's notebook directly with semantic interpretation. Notebook remains MainAgent's working cognition. Runtime records facts; MainAgent updates notebook.

### 10.2 Evidence Detail

If a child task is research, file, code, or tool-result oriented, the child commit must include enough detail for the parent to reason accurately.

The parent must be able to see:

- what was done;
- what succeeded;
- what failed;
- exact tool result content or references when relevant;
- sequence and timestamps;
- child assumptions and limitations.

This prevents repeated actions caused by vague tool or child result feedback.

## 11. Prompt And Contract Requirements

### 11.1 MainAgent Prompt Updates

MainAgent prompt should explain:

- it is the user-facing orchestrator;
- it should use normal direct handling for simple tasks;
- it should delegate only when tasks are separable or require specialized workers;
- delegated tasks must be atomic and clear;
- child agents return commits, not user-facing final answers;
- it must replan after child commits using notebook/worklog/evidence;
- it may use workspace context when present.

### 11.2 Generic Subagent Prompt

Generic subagent prompt should explain:

- it is a temporary worker delegated by a parent agent;
- it has one bounded task;
- it must use only listed effective capabilities;
- it cannot answer the user directly;
- it may ask the user only through `ASK_USER` when genuinely blocked;
- it must return `COMMIT` when done;
- it should include full details when the task asks for file/tool/research evidence.

### 11.3 Java Contracts Are Source Of Truth

Prompt text must not define the authoritative schema.

Java contracts define:

- allowed actions per profile;
- required fields;
- allowed state writes;
- validation;
- repair policy;
- routing;
- retry limits.

Database prompts should remain role/style/boundary guidance.

## 12. Frontend Product Model

Frontend should eventually support a project selector near the input area:

- choose existing project/workspace;
- add new project;
- choose "do not use project";
- display current workspace;
- switch workspace;
- exit workspace.

Selecting a workspace does not transfer the session to CodeAgent. It gives `MainAgent` a current workspace scope.

```text
No workspace selected:
  MainAgent handles normal chat/RAG/MCP/simple tasks.

Workspace selected:
  MainAgent receives currentWorkspace in state view.
  MainAgent may still answer normal questions directly.
  MainAgent may delegate workspace code tasks to future CodeAgent.
```

The first generic subagent implementation does not require the frontend workspace UI, but the backend model should not block it.

## 13. Implementation Phases

### Phase 1: Harness Foundation

Goal: introduce profile/policy abstractions while preserving MainAgent behavior.

Work:

- define `AgentProfile`;
- define policy interfaces for memory, capability, action, prompt/contract, completion, and parent-child behavior;
- adapt current MainAgent runtime to run as `MainAgentProfile`;
- keep current non-delegation behavior equivalent;
- add focused tests proving existing MainAgent actions still route as before.

### Phase 2: Generic Subagent MVP

Goal: support temporary full-context child agents.

Work:

- add `GenericSubAgentProfile`;
- add subagent node entry service and prompt/contract;
- add `COMMIT` action;
- add full-context subagent memory;
- add child run creation;
- add child action validation and routing;
- add child commit recording.

### Phase 3: Parent-Child Dispatch

Goal: allow MainAgent to delegate multiple child tasks and wait for them.

Work:

- add `DELEGATE_AGENTS` action for MainAgent;
- implement `AgentDispatchRuntime`;
- persist parent-child relationships;
- support `WAIT_ALL`;
- project child commits into parent worklog/evidence/state view;
- wake parent when all child runs finish;
- test success, partial failure, and child failure paths.

### Phase 4: Unified ASK_USER For Child Runs

Goal: make child-agent pending input safe and resumable.

Work:

- record `sourceAgentRunId` and `sourceAgentType` in pending input;
- route answers back to the exact source run;
- ensure parent remains waiting when child asks user;
- test child ask/resume/commit flow.

### Phase 5: Workspace Foundation

Goal: prepare for CodeAgent and scoped file permissions.

Work:

- add workspace model and repository boundary;
- expose current workspace in MainAgent state view;
- add workspace scope to capability resolution;
- make file/tool path checks scope-aware;
- reject out-of-scope access in first version;
- reserve approval escalation for future.

### Phase 6: CodeAgent Bridge Reservation

Goal: define the connection contract without implementing CodeAgent internals.

Work:

- define `DELEGATE_CODE_AGENT` as reserved or disabled until CodeAgent runtime exists;
- define request/commit bridge contracts;
- ensure bridge can use shared ASK_USER and approval interfaces later;
- document that internal CodeAgent harness will be specified separately.

## 14. Acceptance Criteria

The design is implemented correctly when:

- MainAgent non-delegation flows remain behavior-equivalent after harness migration;
- MainAgent can emit `DELEGATE_AGENTS`;
- Runtime can create multiple generic subagent runs;
- generic subagents use full-context memory;
- generic subagents cannot `FINAL` to the user;
- generic subagents can `COMMIT` to the parent;
- parent run waits using `WAIT_ALL`;
- parent run resumes after all child runs are terminal;
- child results appear in parent worklog/evidence/state view with enough detail;
- child `ASK_USER` resumes the child, not the parent;
- capability enforcement is Runtime-owned;
- workspace scope is represented as a future permission boundary;
- CodeAgent is connected only through a bridge/reservation in this phase.

## 15. Explicit Non-Goals

This spec does not implement:

- internal CodeAgent harness;
- Claude Code / claw-code tool model;
- code edit/apply-patch loop;
- long-running background agents;
- wait-any or quorum scheduling;
- subagents creating subagents;
- admin UI for capabilities;
- advanced workspace approval escalation;
- frontend project selector implementation;
- distributed queue execution.

These are later specs or implementation phases.

