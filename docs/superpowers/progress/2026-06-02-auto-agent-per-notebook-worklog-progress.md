# AutoAgent PER Notebook Worklog Progress

Status: in progress.

This file records implementation progress for:

- `docs/superpowers/specs/2026-06-02-auto-agent-per-notebook-worklog-subagent-design.md`
- `docs/superpowers/plans/2026-06-02-auto-agent-per-notebook-worklog-implementation.md`

## 2026-06-02 Batch 1: PER Foundation Code Slice

### Completed

Implemented the first PER foundation slice for the current AutoAgent main-loop Runtime.

Core changes:

- Added `perUpdate` support to `MainAgentActionVO`.
- Added runtime notebook VOs:
  - `MainAgentNotebookVO`
  - `NotebookStepVO`
  - `NotebookFactVO`
  - `NotebookQuestionVO`
  - `NotebookRiskVO`
  - `PerUpdateVO`
  - `PerStepUpdateVO`
- Added `PerUpdateMergeService`.
- Extended `RunWorkingStateVO` with:
  - `notebook`
  - `worklog`
  - `nextSequence`
- Extended `MainAgentStateViewVO` with:
  - `notebook`
  - `worklog`
- Updated `RunWorkingStateManager` so:
  - `perUpdate` is merged into notebook before action effect merge;
  - notebook is projected into the next MainAgent state view;
  - action results append ordered worklog records;
  - `CALL_TOOL` worklog items include stable `repeatGuardKey`.
- Added runtime worklog VOs:
  - `RuntimeWorklogItemVO`
  - `ActionRequestSnapshotVO`
  - `ActionResultSnapshotVO`
  - `ToolCallSnapshotVO`
  - `AskUserSnapshotVO`
- Extended `ActionEffectVO` with optional worklog fields.
- Extended MCP tool result visibility:
  - `ToolInvocationResultVO.resultContent`
  - `ToolInvocationResultVO.resultContentRef`
  - `ToolInvocationResultVO.resultContentFormat`
  - `ToolInvocationResultVO.resultTotalChars`
  - `ToolInvocationResultVO.resultTotalBytes`
  - `MaterializedEvidenceVO.content`
  - `MaterializedEvidenceVO.contentRef`
  - `MaterializedEvidenceVO.contentFormat`
  - `MaterializedEvidenceVO.truncated`
  - `MaterializedEvidenceVO.totalChars`
  - `MaterializedEvidenceVO.totalBytes`
  - `MaterializedEvidenceVO.sequence`
  - `MaterializedEvidenceVO.sourceLoopIndex`
  - `MaterializedEvidenceVO.sourceWorkId`
  - `MaterializedEvidenceVO.createdAt`
  - `MaterializedEvidenceVO.metadata`
- Updated `ToolRuntime` so original MCP textual output is preserved as `resultContent`.
- Updated `ToolEvidenceConverter` so original tool output is projected as evidence content while keeping bounded summary compatibility.
- Updated `PlanActionHandler` so `PLAN` can continue with meaningful `perUpdate` even when legacy `stateDelta.planDraft` is absent.
- Updated `DefaultAutoAgentRuntimeService` pending-input checkpoint payload to include current `RunWorkingStateVO`.
- Updated `MainAgentPendingInputHandler` so MainAgent `ASK_USER` resume restores PER working state from checkpoint payload.

### Tests Added Or Updated

Added:

- `PerUpdateMergeServiceTest`
- `RunWorkingStateWorklogProjectionTest`

Updated:

- `RuntimeWorkingStateProjectionTest`
- `ToolRuntimeTest`
- `ToolActionOrchestratorTest`
- `PlanContinueActionHandlerTest`
- `PendingInputContinuationDispatcherTest`

### Verification

Focused tests passed:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=PerUpdateMergeServiceTest,RuntimeWorkingStateProjectionTest,RunWorkingStateWorklogProjectionTest,ToolRuntimeTest,ToolActionOrchestratorTest,PlanContinueActionHandlerTest,PendingInputContinuationDispatcherTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result:

```text
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
```

Compile passed:

```powershell
mvn -q -DskipTests compile
```

### Deferred

Not implemented in this batch:

- Agent Harness abstraction.
- `DELEGATE_AGENTS`.
- `WAITING_CHILDREN`.
- child `COMMIT`.
- persistent database tables for notebook/worklog snapshots.
- prompt/contract guidance for MainAgent PER reading order.

## Next Batch

## 2026-06-02 Batch 2: MainAgent PER Prompt And Contract Slice

### Completed

Updated MainAgent prompt and output contract guidance so MainAgent understands:

- `notebook` is its current-run PER task board;
- `worklog` is Runtime's ordered execution ledger;
- `evidencePack` contains original materials produced by actions;
- `memoryPack` and `ragPack` are context injected before or during state preparation;
- reading order should be notebook -> worklog by sequence -> evidence by ids -> user clarifications -> memory/RAG when relevant;
- duplicate tool calls should be avoided when a matching `repeatGuardKey` already succeeded;
- file/directory references in natural language should be resolved before reading/writing;
- every output should include concise `perUpdate` without chain-of-thought.

Core changes:

- Updated `MainAgentPromptBuilder`:
  - documented notebook/worklog/evidencePack responsibilities;
  - documented notebook -> worklog -> evidencePack reading order;
  - documented `perUpdate` as concise PER/DIRECT task-state notes;
  - documented duplicate tool avoidance through successful `repeatGuardKey`;
  - documented natural-language file/directory target resolution before direct reads;
  - updated few-shot examples to include top-level `perUpdate`.
- Updated `OutputContractPromptRenderer`:
  - added top-level `perUpdate` to the MainAgent output contract text;
  - described `perUpdate` as the notebook patch merged before Runtime executes the action;
  - added DIRECT and PER examples for FINAL, RETRIEVE_RAG, CALL_TOOL, ASK_USER, PLAN, CONTINUE, REPAIR_FINAL, and FAIL.
- Updated `ContextPlannerPromptBuilder` with explicit stable wording for existing reference-resolution prompt tests:
  - resolve follow-up references before asking;
  - infer original draft/latest revised draft pairs from recent messages when possible;
  - do not ask when recent messages contain enough context.

### Tests Added Or Updated

Updated:

- `PromptAssemblerTest`
- `PromptContractBoundaryTest`

### Verification

Focused prompt and contract tests passed:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=PromptAssemblerTest,PromptContractBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result:

```text
Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
```

Combined PER foundation and prompt/contract verification passed:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=PerUpdateMergeServiceTest,RuntimeWorkingStateProjectionTest,RunWorkingStateWorklogProjectionTest,ToolRuntimeTest,ToolActionOrchestratorTest,PlanContinueActionHandlerTest,PendingInputContinuationDispatcherTest,PromptAssemblerTest,PromptContractBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result:

```text
Tests run: 56, Failures: 0, Errors: 0, Skipped: 0
```

Compile passed:

```powershell
mvn -q -DskipTests compile
```

## Next Batch

Next implementation batch should review and refine Agent Harness abstraction design before implementing TaskAgent or MainAgent delegation.

## 2026-06-02 Batch 3 Started: Agent Harness Abstraction Design

### Started

Created draft spec:

- `docs/superpowers/specs/2026-06-02-auto-agent-harness-abstraction-design.md`

The draft defines:

- reusable `AgentRuntime` mechanics;
- `MAIN_AGENT`, `TASK_AGENT`, future `CODE_AGENT`, and optional `READ_ONLY_AGENT` profile boundaries;
- agent identity and local working state;
- context strategy, state-view projection, node invocation, action routing, capability policy, completion policy, and checkpoint strategy as composable harness units;
- shared pending-input behavior for MainAgent and child agents;
- parent/child `WAIT_ALL` coordination model;
- child `COMMIT` contract direction;
- TaskAgent memory mode as parent-scoped full in-run context rather than MainAgent's long-term memory lifecycle;
- CodeAgent as a deferred workspace-bound specialist;
- recommended implementation order: extract harness interfaces, adapt MainAgent, add TaskAgent local runtime, then add parent delegation.

### Deferred

No code was implemented for Batch 3 yet.

Deferred until user review:

- Agent Harness implementation plan;
- `DELEGATE_AGENTS`;
- `WAITING_CHILDREN`;
- child `COMMIT` implementation;
- TaskAgent runtime;
- CodeAgent runtime.

## 2026-06-02 PER Debug Pass 1: Notebook Merge Stability

### Focus

Before continuing with Agent Harness implementation, PER foundation testing and debug was prioritized.

Reviewed and re-ran the current PER baseline covering:

- notebook merge and projection;
- worklog projection;
- MCP tool original-result evidence visibility;
- `PLAN` compatibility;
- MainAgent pending-input resume;
- MainAgent PER prompt/contract guidance.

### Issue Found

`PerUpdateMergeService` already merged notebook steps by `stepId`, but `factsLearned`, `openQuestions`, and `risks` were append-only.

This conflicted with the accepted PER spec because repeated updates with the same id should update the existing notebook item instead of creating duplicate facts/questions/risks.

Potential runtime effect if left unfixed:

- MainAgent could see duplicated facts after replanning;
- resolved questions could coexist with stale open questions;
- closed risks could coexist with stale open risks;
- notebook would become harder for MainAgent to use across multi-loop tasks.

### Fix

Updated `PerUpdateMergeService` so:

- `factsLearned` merge by `factId` when present;
- `openQuestions` merge by `id` when present;
- `risks` merge by `id` when present;
- id-less facts/questions/risks still append with generated ids;
- updated items refresh `loopIndex` and `sequence`;
- existing source id lists are replaced when new source id lists are provided.

### Tests Added Or Updated

Updated:

- `PerUpdateMergeServiceTest`

Added test coverage:

- `merge_updates_existing_fact_question_and_risk_by_id`

### Verification

Focused test passed:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=PerUpdateMergeServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result:

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

Combined PER verification passed:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=PerUpdateMergeServiceTest,RuntimeWorkingStateProjectionTest,RunWorkingStateWorklogProjectionTest,ToolRuntimeTest,ToolActionOrchestratorTest,PlanContinueActionHandlerTest,PendingInputContinuationDispatcherTest,PromptAssemblerTest,PromptContractBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result:

```text
Tests run: 57, Failures: 0, Errors: 0, Skipped: 0
```

Compile passed:

```powershell
mvn -q -DskipTests compile
```
