# AutoAgent PER Notebook Worklog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the current AutoAgent main-loop harness so MainAgent can run a PER-style loop with notebook state, ordered worklog records, original action evidence, and reliable MCP tool-result visibility without breaking current Memory/RAG/MCP modules.

**Architecture:** Keep `RunWorkingState` as the in-run source of truth and project `MainAgentStateView` on every loop. Add `perUpdate` as MainAgent's structured notebook patch, add worklog as Runtime's execution ledger, and enhance evidence projection so MCP tool results are visible to MainAgent as original material. Defer TaskAgent execution to a later implementation plan after the PER foundation is stable.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Maven multi-module project, Lombok VOs, JUnit tests in `ai-agent-station-study-app`, existing DDD package boundaries.

---

## Implementation Scope

This plan implements the first stable slice of the accepted spec:

- Phase A: PER notebook and worklog foundation.
- Phase B: MCP full-result evidence projection.
- Phase C: PLAN compatibility with `perUpdate`.
- Minimal pending-input resume support so PER notebook/worklog/evidence survives `ASK_USER`.

This plan intentionally does not implement:

- `DELEGATE_AGENTS`;
- `WAITING_CHILDREN`;
- `COMMIT`;
- TaskAgent runtime;
- CodeAgent.

Those require additional harness design before implementation. After this plan passes tests, the next work is not `DELEGATE_AGENTS`; the next work is an Agent Harness abstraction and infrastructure design covering reusable agent runtime, agent type boundaries, agent memory mode, agent capability limits, and child `COMMIT` semantics. Only after that foundation works should MainAgent-to-child delegation and `WAIT_ALL` be implemented.

Testing policy for this plan: keep tests minimal and targeted. Add only the tests needed for changed contracts and changed runtime behavior; do not add broad full-suite scenario coverage in this slice.

## Current Code Anchors

Use these existing files as anchors:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/invocation/MainAgentActionVO.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/context/MainAgentStateViewVO.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/context/MaterializedEvidenceVO.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/RunWorkingStateVO.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/ActionEffectVO.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/MainActionHandlerResult.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/RunWorkingStateManager.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/DefaultAutoAgentRuntimeService.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/interaction/MainAgentPendingInputHandler.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/handler/PlanActionHandler.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/handler/CallToolActionHandler.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/tool/ToolRuntime.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/tool/ToolEvidenceConverter.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/prompt/MainAgentPromptBuilder.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/prompt/OutputContractPromptRenderer.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/contract/StateDeltaScopeRules.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/contract/ContractValidator.java`

Test anchors:

- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/RuntimeWorkingStateProjectionTest.java`
- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/PendingInputContinuationDispatcherTest.java`
- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/RuntimeRepeatedActionGuardTest.java`
- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/handler/PlanContinueActionHandlerTest.java`
- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/handler/CallToolActionHandlerTest.java`
- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/tool/ToolRuntimeTest.java`
- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/tool/ToolActionOrchestratorTest.java`
- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/tool/ToolApprovalServiceTest.java`
- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/contract/MainAgentActionContractTest.java`

## Design Decisions Locked By This Plan

1. `perUpdate` is parsed as a top-level field on `MainAgentActionVO`.
2. Runtime merges `perUpdate` before executing the action.
3. `RunWorkingStateVO` stores the canonical notebook and worklog.
4. `MainAgentStateViewVO` exposes notebook and worklog.
5. `actionHistory` remains for compatibility during migration.
6. Evidence is original material. Runtime may store tool-provided text and mechanical metadata, but must not invent semantic summaries.
7. `PLAN` remains but is no longer the only way to update plan state.
8. Tool full-result visibility is implemented without changing the existing `CALL_TOOL` entrypoint.

## Task 1: Add PER Notebook VOs

**Files:**

- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/MainAgentNotebookVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/NotebookStepVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/NotebookFactVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/NotebookQuestionVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/NotebookRiskVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/PerUpdateVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/PerStepUpdateVO.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/PerUpdateMergeServiceTest.java`

- [ ] **Step 1: Create notebook carrier VOs**

Create the files with Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, and `@AllArgsConstructor`.

Use these exact fields as the first implementation baseline:

```java
// MainAgentNotebookVO
private String mode;
private String goal;
private Integer notebookVersion;
private Integer lastUpdatedLoopIndex;
private Long lastUpdatedSequence;
private List<NotebookStepVO> steps;
private List<NotebookFactVO> facts;
private List<NotebookQuestionVO> openQuestions;
private List<NotebookRiskVO> risks;
private String nextStepId;
private String lastDecision;
private Map<String, Object> metadata;
```

```java
// NotebookStepVO and PerStepUpdateVO
private String stepId;
private String title;
private String status;
private String note;
private List<String> relatedWorkIds;
private List<String> relatedEvidenceIds;
private Integer createdLoopIndex;
private Integer updatedLoopIndex;
private Long createdSequence;
private Long updatedSequence;
private Map<String, Object> metadata;
```

```java
// NotebookFactVO
private String factId;
private String content;
private List<String> sourceEvidenceIds;
private List<String> sourceWorkIds;
private Integer loopIndex;
private Long sequence;
```

```java
// NotebookQuestionVO and NotebookRiskVO
private String id;
private String content;
private String status;
private List<String> sourceEvidenceIds;
private List<String> sourceWorkIds;
private Integer loopIndex;
private Long sequence;
```

```java
// PerUpdateVO
private String mode;
private String goal;
private List<PerStepUpdateVO> stepUpdates;
private List<NotebookFactVO> factsLearned;
private List<NotebookQuestionVO> openQuestions;
private List<NotebookRiskVO> risks;
private String nextStepId;
private String lastDecision;
private Map<String, Object> metadata;
```

- [ ] **Step 2: Compile the domain model**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected: compile may fail because no merge service exists yet only if imports are wrong. If it fails on missing imports in these new files, fix those imports before continuing.

## Task 2: Add Worklog VOs

**Files:**

- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/RuntimeWorklogItemVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/ActionRequestSnapshotVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/ActionResultSnapshotVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/ToolCallSnapshotVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/AskUserSnapshotVO.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/RunWorkingStateWorklogProjectionTest.java`

- [ ] **Step 1: Create RuntimeWorklogItemVO**

Use this field baseline:

```java
private String workId;
private String runId;
private Integer loopIndex;
private Long sequence;
private String actionType;
private String status;
private String stepId;
private String sourceComponent;
private String requestRef;
private ActionRequestSnapshotVO request;
private String resultRef;
private ActionResultSnapshotVO result;
private List<String> resultEvidenceIds;
private String failureCode;
private String failureMessage;
private Boolean retryable;
private String repeatGuardKey;
private LocalDateTime startedAt;
private LocalDateTime completedAt;
private Map<String, Object> metadata;
```

- [ ] **Step 2: Create action snapshot VOs**

`ActionRequestSnapshotVO` baseline:

```java
private String actionType;
private String capabilityCode;
private String mcpServerCode;
private String toolName;
private Map<String, Object> arguments;
private String argumentsRef;
private String goal;
private Map<String, Object> raw;
```

`ActionResultSnapshotVO` baseline:

```java
private String status;
private String message;
private String content;
private String contentRef;
private String contentFormat;
private Boolean truncated;
private Integer totalChars;
private Long totalBytes;
private Map<String, Object> raw;
```

`ToolCallSnapshotVO` baseline:

```java
private String toolCallId;
private String toolInvocationId;
private String approvalId;
private String approvalStatus;
private String receiptRef;
private String failureCode;
private String failureMessage;
```

`AskUserSnapshotVO` baseline:

```java
private String pendingInputId;
private String question;
private String inputMode;
private Boolean answered;
private String answerType;
private Map<String, Object> value;
private String freeText;
```

- [ ] **Step 3: Compile model**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected: compile passes or fails only on unrelated dirty-worktree code. Do not modify unrelated dirty files.

## Task 3: Extend MainAgentActionVO With perUpdate

**Files:**

- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/invocation/MainAgentActionVO.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/invocation/NodeOutputMapper.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/invocation/FunctionCallMapper.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/contract/MainAgentActionContractTest.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/invocation/FunctionCallMapperTest.java`

- [ ] **Step 1: Add perUpdate field**

Add to `MainAgentActionVO`:

```java
private Map<String, Object> perUpdate;
```

Use `Map<String, Object>` for the first migration slice to avoid disrupting JSON parsing. Typed conversion happens in the merge service.

- [ ] **Step 2: Ensure NodeOutputMapper copies top-level perUpdate**

Inspect the current mapper. If it uses FastJSON object-to-VO mapping, verify it already copies top-level fields. If it manually builds `MainAgentActionVO`, add:

```java
.perUpdate(mapValue(root, "perUpdate"))
```

Use the existing local helper style. Do not introduce a new JSON library.

- [ ] **Step 3: Update function-call mapping for plan**

When function-call mapping creates `main_plan`, put the function arguments in both:

```json
{
  "perUpdate": {...},
  "action": "PLAN",
  "stateDelta": {"planDraft": {...}}
}
```

Keep `stateDelta.planDraft` for compatibility with `PlanActionHandler`.

- [ ] **Step 4: Add contract test**

Add a test asserting top-level `perUpdate` survives parse/mapper:

```java
String raw = """
        {"perUpdate":{"mode":"PER","goal":"inspect project"},"action":"FINAL","stateDelta":{"finalAnswerCandidate":{"content":"done"}}}
        """;
MainAgentActionVO action = mapper.parse(raw);
Assert.assertEquals("PER", action.getPerUpdate().get("mode"));
```

Use the repository's actual parser/mapper helper names.

- [ ] **Step 5: Run targeted tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=MainAgentActionContractTest,FunctionCallMapperTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: tests pass.

## Task 4: Implement PerUpdateMergeService

**Files:**

- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/PerUpdateMergeService.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/RunWorkingStateManager.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/PerUpdateMergeServiceTest.java`

- [ ] **Step 1: Write failing merge test**

Create tests for:

```text
merge creates notebook from empty state
merge updates existing step by stepId
merge appends generated fact when factId is missing
merge rejects invalid step status
merge increments notebookVersion
```

Use an assertion shape like:

```java
MainAgentNotebookVO notebook = service.merge(null, Map.of(
        "mode", "PER",
        "goal", "inspect domain",
        "stepUpdates", List.of(Map.of("stepId", "s1", "title", "resolve folder", "status", "IN_PROGRESS")),
        "nextStepId", "s1",
        "lastDecision", "resolve folder first"
), 2, 10L);

Assert.assertEquals("PER", notebook.getMode());
Assert.assertEquals("inspect domain", notebook.getGoal());
Assert.assertEquals(Integer.valueOf(1), notebook.getNotebookVersion());
Assert.assertEquals(Long.valueOf(10), notebook.getLastUpdatedSequence());
Assert.assertEquals("s1", notebook.getSteps().get(0).getStepId());
```

- [ ] **Step 2: Implement merge service**

Implement public method:

```java
public MainAgentNotebookVO merge(MainAgentNotebookVO existing,
                                 Map<String, Object> perUpdate,
                                 Integer loopIndex,
                                 Long sequence)
```

Rules:

- null/empty `perUpdate` returns existing notebook unchanged;
- valid statuses: `PENDING`, `IN_PROGRESS`, `DONE`, `BLOCKED`, `CANCELLED`;
- merge steps by `stepId`;
- preserve `createdLoopIndex` and `createdSequence` for existing steps;
- update `updatedLoopIndex` and `updatedSequence` on changed steps;
- append facts/questions/risks with generated ids when id is blank;
- increment `notebookVersion` only when a non-empty update is accepted.

- [ ] **Step 3: Run merge tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=PerUpdateMergeServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: tests pass.

## Task 5: Extend RunWorkingStateVO And MainAgentStateViewVO

**Files:**

- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/RunWorkingStateVO.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/context/MainAgentStateViewVO.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/context/MainAgentStateViewBuilder.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/RuntimeWorkingStateProjectionTest.java`

- [ ] **Step 1: Add RunWorkingState fields**

Add:

```java
private MainAgentNotebookVO notebook;
private List<RuntimeWorklogItemVO> worklog;
private Long nextSequence;
```

Initialize `nextSequence` to `1L` when missing.

- [ ] **Step 2: Add MainAgentStateView fields**

Add:

```java
private MainAgentNotebookVO notebook;
private List<RuntimeWorklogItemVO> worklog;
```

Keep existing `currentPlan` and `actionHistory`.

- [ ] **Step 3: Update MainAgentStateViewBuilder**

Ensure builder preserves incoming notebook/worklog from command if command is extended. If command is not extended yet, no behavior change is required in this task. The projection path in `RunWorkingStateManager.project` becomes the source for notebook/worklog in loop refreshes.

- [ ] **Step 4: Run compile**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected: compile passes.

## Task 6: Merge perUpdate In RunWorkingStateManager

**Files:**

- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/RunWorkingStateManager.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/RuntimeWorkingStateProjectionTest.java`

- [ ] **Step 1: Write failing projection test**

Add a test where:

1. working state starts empty;
2. action contains `perUpdate.mode=PER`, `goal`, and one step;
3. manager applies a `CONTINUE_LOOP` result;
4. projected state view contains notebook with the step.

Expected assertions:

```java
Assert.assertNotNull(projected.getNotebook());
Assert.assertEquals("PER", projected.getNotebook().getMode());
Assert.assertEquals("s1", projected.getNotebook().getSteps().get(0).getStepId());
```

- [ ] **Step 2: Add PerUpdateMergeService dependency**

For minimal disruption, instantiate inside `RunWorkingStateManager`:

```java
private final PerUpdateMergeService perUpdateMergeService = new PerUpdateMergeService();
```

If later injection is preferred, add a constructor after tests pass.

- [ ] **Step 3: Apply perUpdate before effect merge**

Inside `apply(...)`, before `mergeEffect(...)`, call the merge service when action has `perUpdate`.

Use a new sequence value:

```java
Long sequence = nextSequence(workingState);
workingState.setNotebook(perUpdateMergeService.merge(
        workingState.getNotebook(),
        action.getPerUpdate(),
        context.getLoopIndex(),
        sequence
));
```

Only increment sequence for accepted non-empty updates. If implementing exact increment detection is too invasive, increment sequence for any non-empty `perUpdate`; document this in code by method naming, not a comment.

- [ ] **Step 4: Project notebook**

In `project(...)`, set:

```java
.notebook(workingState.getNotebook())
.worklog(new ArrayList<>(defaultList(workingState.getWorklog())))
```

Keep:

```java
.currentPlan(base.getCurrentPlan())
```

for migration compatibility.

- [ ] **Step 5: Run targeted projection tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RuntimeWorkingStateProjectionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: existing projection behavior still passes and new notebook projection test passes.

## Task 6.5: Preserve PER State Across ASK_USER Resume

**Files:**

- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/DefaultAutoAgentRuntimeService.java`
- Modify only if needed: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/interaction/MainAgentPendingInputHandler.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/PendingInputContinuationDispatcherTest.java`

- [ ] **Step 1: Add one focused resume test**

Add or update a single test proving:

```text
MainAgent has notebook/worklog/evidence in RunWorkingState
MainAgent pauses through ASK_USER
user answer resumes the run
the rebuilt/projected state view still contains the prior notebook/worklog/evidence plus the new user clarification
```

Do not add broad pending-input scenario coverage in this task.

- [ ] **Step 2: Store PER working state in the continuation checkpoint**

Current checkpoint payload must continue to carry `contextSelections`.

Add a PER state snapshot or payload reference containing:

```text
workingState.notebook
workingState.worklog
workingState.evidencePack
workingState.userClarifications
workingState.previousLoopOutcome
workingState.nextSequence
loopIndex
```

If serializing the whole `RunWorkingStateVO` is simpler and stable, do that. If payload size is a concern, save it through `IPayloadRepository` and store a ref in the checkpoint payload.

- [ ] **Step 3: Restore working state before the resumed MainAgent loop**

On resume, before building/projecting the next MainAgent state view:

```text
read checkpoint PER state
restore context.workingState
merge the new user clarification
project state view from RunWorkingStateManager
continue to MainAgent
```

The restored state must preserve sequence/order fields. Do not re-run memory/RAG just to recover notebook/worklog/evidence.

- [ ] **Step 4: Run only the focused pending-input test**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=PendingInputContinuationDispatcherTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: test passes.

## Task 7: Add Worklog Creation For Action Results

**Files:**

- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/RunWorkingStateManager.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime/ActionEffectVO.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/RunWorkingStateWorklogProjectionTest.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/RuntimeRepeatedActionGuardTest.java`

- [ ] **Step 1: Add optional fields to ActionEffectVO**

Add:

```java
private String workId;
private String repeatGuardKey;
private String resultRef;
private Map<String, Object> requestSnapshot;
private Map<String, Object> resultSnapshot;
```

This keeps action handlers able to pass richer worklog data without introducing handler-specific coupling immediately.

- [ ] **Step 2: Write worklog projection test**

Create a test:

```java
manager.apply(context, writeFileAction(), successfulToolResult());
MainAgentStateViewVO projected = manager.project(context.getWorkingState());
Assert.assertEquals(1, projected.getWorklog().size());
RuntimeWorklogItemVO item = projected.getWorklog().get(0);
Assert.assertEquals("CALL_TOOL", item.getActionType());
Assert.assertEquals(ToolActionEffectStatusEnumVO.TOOL_SUCCEEDED.name(), item.getStatus());
Assert.assertEquals(List.of("evidence-success"), item.getResultEvidenceIds());
```

- [ ] **Step 3: Build worklog item in RunWorkingStateManager**

When applying an action result, append a `RuntimeWorklogItemVO` in addition to existing `actionHistory`.

Use:

```text
workId = effect.workId when present, otherwise "work-" + sequence
actionType = effect.action
status = effect.status
loopIndex = effect.loopIndex
resultEvidenceIds = effect.createdEvidenceIds
repeatGuardKey = effect.repeatGuardKey
request.raw = effect.toolIntent for CALL_TOOL compatibility
```

- [ ] **Step 4: Preserve duplicate actionHistory behavior**

Do not change existing duplicate merging for `actionHistory` in this task. Worklog may append more detailed records, but existing repeated-action tests must still pass.

- [ ] **Step 5: Run targeted tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RunWorkingStateWorklogProjectionTest,RuntimeRepeatedActionGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: all pass.

## Task 8: Generate Tool Repeat Guard Keys

**Files:**

- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/RunWorkingStateManager.java`
- Use existing: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/tool/ToolApprovalKeyGenerator.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/RuntimeRepeatedActionGuardTest.java`

- [ ] **Step 1: Reuse stable argument hashing**

`ToolApprovalKeyGenerator.argumentsHash(...)` is already public and stable. Reuse it directly. Do not modify `ToolApprovalKeyGenerator` unless implementation proves the existing hash is unstable.

The repeat guard key format must be:

```text
CALL_TOOL:<capabilityCode>:<mcpServerCode>:<toolName>:<stableArgsHash>
```

Use empty string for absent `mcpServerCode`.

- [ ] **Step 2: Add repeatGuardKey to CALL_TOOL worklog**

When `toolIntent` exists, compute repeat guard key from:

```text
capabilityCode
mcpServerCode
toolName
arguments
```

- [ ] **Step 3: Test same args same key**

Add assertion:

```java
Assert.assertEquals(first.getRepeatGuardKey(), second.getRepeatGuardKey());
```

for two identical tool actions in a manager-level test.

- [ ] **Step 4: Run repeated action tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RuntimeRepeatedActionGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: pass.

## Task 9: Extend Evidence VO For Original Content

**Files:**

- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/context/MaterializedEvidenceVO.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/evidence/EvidencePackBuilder.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/context/ContextMaterializationTest.java`

- [ ] **Step 1: Add compatibility-safe fields**

Add:

```java
private String content;
private String contentRef;
private String contentFormat;
private Boolean truncated;
private Integer totalChars;
private Long totalBytes;
private Long sequence;
private Integer sourceLoopIndex;
private String sourceWorkId;
private LocalDateTime createdAt;
private Map<String, Object> metadata;
```

Do not remove `summary` or `boundedSnippet`.

- [ ] **Step 2: Ensure existing builders still compile**

Because Lombok builder accepts missing fields, most existing code should compile. Fix only compile errors caused by missing imports.

- [ ] **Step 3: Run context tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=ContextMaterializationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: pass.

## Task 10: Preserve Full MCP Tool Result As Evidence

**Files:**

- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/tool/ToolRuntime.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/tool/ToolEvidenceConverter.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/tool/ToolInvocationResultVO.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/tool/ToolRuntimeTest.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/tool/ToolActionOrchestratorTest.java`

- [ ] **Step 1: Add full result fields to ToolInvocationResultVO**

Add:

```java
private String resultContent;
private String resultContentRef;
private String resultContentFormat;
private Integer resultTotalChars;
private Long resultTotalBytes;
```

Keep `resultSummary` for compatibility.

- [ ] **Step 2: Populate original content in ToolRuntime**

In `ToolRuntime.resultSummary(...)`, do not use that method as the only result carrier.

Add a helper:

```java
private String resultContent(McpToolInvokeResultVO invokeResult)
```

using the same content extraction logic:

```text
receipt.contentText when present
otherwise receipt.rawResult
```

Set `resultContent` on `ToolInvocationResultVO`.

- [ ] **Step 3: Keep resultSummary bounded**

Do not remove the current bounded `resultSummary` yet. It remains a compatibility field.

- [ ] **Step 4: Update ToolEvidenceConverter**

When `ToolInvocationResultVO.resultContent` is present:

```java
MaterializedEvidenceVO.builder()
    .evidenceType("TOOL")
    .sourceRef(result.getToolCallId())
    .summary(bounded mechanical status or bounded copy)
    .boundedSnippet(bounded original content)
    .content(projectedContent)
    .contentRef(result.getResultContentRef())
    .contentFormat(firstNonBlank(result.getResultContentFormat(), "TEXT"))
    .truncated(false)
    .totalChars(result.getResultTotalChars())
    .build()
```

If full payload refs are not yet available from `ToolRuntime`, use `receiptRef` as `contentRef` for the first slice.

- [ ] **Step 5: Write test for read_file-like content**

Fake MCP result content:

```text
package demo;
public class Demo {}
```

Assert the created evidence contains that exact content in `content` or `boundedSnippet`.

- [ ] **Step 6: Run tool tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=ToolRuntimeTest,ToolActionOrchestratorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: pass.

## Task 11: Project Evidence Content To MainAgentStateView

**Files:**

- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/RunWorkingStateManager.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/context/ContextBudgetManager.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/RuntimeWorkingStateProjectionTest.java`

- [ ] **Step 1: Add projection test for tool content**

Extend working state projection test:

```java
MaterializedEvidenceVO evidence = MaterializedEvidenceVO.builder()
    .evidenceId("ev-file")
    .evidenceType("TOOL")
    .content("full file content")
    .contentRef("payload-file")
    .truncated(false)
    .build();
```

Assert `projected.getEvidencePack().get(0).getContent()` equals `full file content`.

- [ ] **Step 2: Preserve evidence merge behavior**

`RunWorkingStateManager.mergedEvidence(...)` already merges by evidence id. Ensure it keeps the richer incoming evidence instead of replacing it with a sparse base item.

Use rule:

```text
incoming evidence wins when ids match
```

- [ ] **Step 3: Do not add LLM summaries**

No code should call LLM or summary node in this task.

- [ ] **Step 4: Run projection tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RuntimeWorkingStateProjectionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: pass.

## Task 12: Update Prompt And Contract Text

**Files:**

- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/prompt/MainAgentPromptBuilder.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/prompt/OutputContractPromptRenderer.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/contract/ContractValidator.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/invocation/PromptAssemblerTest.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/invocation/PromptContractBoundaryTest.java`

- [ ] **Step 1: Add PER state field guide**

Update MainAgent prompt to explain:

```text
notebook is your current run plan/progress state.
worklog is Runtime's ordered execution record.
evidencePack contains original materials produced by actions.
memoryPack/ragPack are context injected before or during state preparation.
```

- [ ] **Step 2: Add reading order**

Prompt must say:

```text
Read notebook first, then worklog by sequence, then evidencePack through worklog resultEvidenceIds, then userClarifications and memory/rag when relevant.
```

- [ ] **Step 3: Add duplicate tool rule**

Prompt must say:

```text
If a worklog item with the same repeatGuardKey already succeeded, do not repeat the same tool call. Use its evidence. If it failed, retry only with materially changed arguments.
```

- [ ] **Step 4: Add target resolution rule**

Prompt must say:

```text
For natural-language file or directory references, resolve the target first. Do not assume a path unless the user gave an exact absolute path or a path already discovered by tool evidence.
```

- [ ] **Step 5: Update output contract examples**

Add examples for:

```json
{"perUpdate":{"mode":"DIRECT","lastDecision":"simple answer"},"action":"FINAL","stateDelta":{"finalAnswerCandidate":{"content":"ok"}}}
```

and:

```json
{"perUpdate":{"mode":"PER","goal":"inspect folder","stepUpdates":[{"stepId":"s1","title":"resolve folder","status":"IN_PROGRESS"}],"nextStepId":"s1"},"action":"CALL_TOOL","stateDelta":{"toolIntent":{"capabilityCode":"file_system_search_files","toolName":"search_files","arguments":{"path":".","pattern":"**/*domain*"}}}}
```

- [ ] **Step 6: Run prompt tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=PromptAssemblerTest,PromptContractBoundaryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: pass.

## Task 13: PLAN Compatibility

**Files:**

- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/handler/PlanActionHandler.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/handler/ContinueActionHandler.java` only if CONTINUE validation must change.
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/handler/PlanContinueActionHandlerTest.java`

- [ ] **Step 1: Update PLAN test**

Add test:

```text
PLAN with perUpdate causes notebook to appear in projected state view.
```

This may be a manager/runtime test rather than a handler-only test, because `PlanActionHandler` should not own notebook merge.

- [ ] **Step 2: Keep PlanStatePort behavior**

Do not remove `planStatePort.savePlan(...)`.

The handler may continue saving `planDraft` for trace/payload compatibility.

- [ ] **Step 3: Reject empty PLAN at contract or handler level**

If `PLAN` lacks both `perUpdate` and `stateDelta.planDraft`, return validation failure.

If `PLAN` has a meaningful `perUpdate` but no `stateDelta.planDraft`, do not fail only because the legacy `planDraft` field is absent. Runtime notebook merge owns the new plan state; `PlanActionHandler` keeps `planDraft` only for compatibility.

- [ ] **Step 4: Run handler tests**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=PlanContinueActionHandlerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: pass.

## Task 14: Minimal Final Verification Slice

**Files:**

- No new files.

- [ ] **Step 1: Run critical targeted tests only**

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=PerUpdateMergeServiceTest,RuntimeWorkingStateProjectionTest,RunWorkingStateWorklogProjectionTest,RuntimeRepeatedActionGuardTest,ToolRuntimeTest,ToolActionOrchestratorTest,MainAgentActionContractTest,PlanContinueActionHandlerTest,PendingInputContinuationDispatcherTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: all specified tests pass.

Only add `PromptAssemblerTest` or `PromptContractBoundaryTest` to this command if prompt/contract files were changed in this implementation slice. Do not run unrelated full-suite tests for this plan.

- [ ] **Step 2: Run compile**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected: compile passes.

- [ ] **Step 3: Inspect git diff**

Run:

```powershell
git status --short
git diff -- ai-agent-station-study-domain ai-agent-station-study-app/src/test docs/superpowers/plans/2026-06-02-auto-agent-per-notebook-worklog-implementation.md
```

Expected:

- only PER/notebook/worklog/tool evidence/prompt/test files changed by this work;
- unrelated dirty files remain untouched.

## Task 15: Prepare Agent Harness Abstraction Design

**Files:**

- Create later: `docs/superpowers/specs/2026-06-02-auto-agent-harness-abstraction-design.md`

- [ ] **Step 1: Confirm PER foundation is stable**

Do not start Agent Harness abstraction design until:

```text
Task 14 targeted tests pass
mvn -q -DskipTests compile passes
user accepts PER foundation behavior
```

- [ ] **Step 2: Write Agent Harness abstraction spec**

The next spec must cover:

- what a reusable `AgentRuntime` abstraction is;
- which lifecycle pieces MainAgent and child agents share;
- which lifecycle pieces MainAgent alone owns;
- TaskAgent memory mode as full in-run context rather than MainAgent's long-term memory lifecycle;
- agent capability table and capability enforcement;
- agent-local notebook/worklog/evidence;
- child `COMMIT` contract and result ingestion model;
- child `ASK_USER` behavior through existing pending input;
- agent recovery/resume behavior;
- how TaskAgent can run and commit independently before MainAgent can call it.

This design must be reviewed before writing a TaskAgent implementation plan.

- [ ] **Step 3: Write TaskAgent implementation plan only after harness spec approval**

The later TaskAgent implementation plan must cover:

- `DELEGATE_AGENTS` action enum and contract;
- waiting child VOs;
- parent run wait/wakeup;
- child TaskAgent runtime entry;
- child `COMMIT`;
- child `ASK_USER`;
- frontend/API display follow-up.

Do not mix these into the PER foundation implementation slice or the Agent Harness abstraction spec.

## Self-Review Checklist

### Spec Coverage

Covered by this plan:

- `perUpdate` top-level contract.
- Runtime notebook merge.
- notebook state projection.
- worklog state projection.
- ordered sequence fields.
- MCP original result evidence.
- duplicate tool visibility through repeat guard key.
- PLAN compatibility.
- ASK_USER resume preservation for PER working state.
- prompt update for PER reading order.

Deferred by design:

- `DELEGATE_AGENTS`.
- `WAITING_CHILDREN`.
- Agent Harness abstraction.
- TaskAgent runtime.
- child `COMMIT`.
- frontend child-agent display.

### Placeholder Scan

This plan contains no `TBD`, no `TODO`, and no unspecified "add appropriate behavior" steps. Where exact helper method names depend on current code, the plan names the owning file and required behavior.

### Type Consistency

Canonical type names in this plan:

- `MainAgentNotebookVO`
- `NotebookStepVO`
- `NotebookFactVO`
- `NotebookQuestionVO`
- `NotebookRiskVO`
- `PerUpdateVO`
- `PerStepUpdateVO`
- `RuntimeWorklogItemVO`
- `ActionRequestSnapshotVO`
- `ActionResultSnapshotVO`
- `ToolCallSnapshotVO`
- `AskUserSnapshotVO`
