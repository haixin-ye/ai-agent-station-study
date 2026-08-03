# AutoAgent Observability Studio Usability Refinement Design

**Date:** 2026-08-03  
**Status:** Approved design direction  
**Scope:** Current dev Runtime and standalone developer dashboard  
**Builds on:** `2026-07-31-auto-agent-developer-observability-studio-design.md`

## 1. Outcome

Refine the existing visual observability studio so a developer can understand one run without first
learning the internal JSON contracts. The board remains a dark-glass, modular execution graph, but its
content hierarchy and live interaction must become stable, causal, and decision-oriented.

The refinement must solve all seven user findings:

1. State View and MainNode memory cards start as titles rather than expanded content.
2. Delegated work shows the parent assignment, concise child execution flow, and the complete child
   result returned to MainNode.
3. Whole cards are clickable; live updates preserve the user's inspection state; the graph supports
   direct dragging, bounded zoom, and same-session run switching.
4. MainNode planning is an ordered plan, not two disconnected before/update card collections.
5. The dashboard keeps the complete TaskLedger view even though the chat mini panel intentionally
   shows only the latest `taskUpdate.stepUpdates`.
6. Action nodes explain decisions and effects rather than merely exposing technically complete
   objects.
7. Every node type receives a content and layout audit, not only the nodes shown in screenshots.

## 2. Confirmed Data Semantics

### 2.1 Planning

`TaskLedger` is accumulated state. `taskUpdate` is the current MainNode action's incremental change.
The dashboard must compute one effective plan:

```text
effective plan = TaskLedger before this loop + taskUpdate from this loop
```

Steps are the primary axis. Deliverables are attached through the existing relationships:

- `TaskStepVO.affectedDeliverableIds`
- `TaskDeliverableVO.relatedStepIds`

The dashboard must not infer relationships by comparing titles.

The chat page's compact “MainNode 当前规划” card uses only the latest action update and therefore may
show fewer records than the observability studio. The studio remains the complete source for
debugging and will not delete deliverables to match that compact card.

### 2.2 Delegated work

The parent action already stores each delegated task's:

- task ID and name;
- objective;
- boundary;
- required output;
- requested capabilities;
- wait mode.

Child lifecycle events already store:

- child started;
- child action and loop index;
- Runtime handler progress;
- terminal commit or failure;
- parent waiting and children-ready transitions.

The existing generic child prompt tells the model to place only a short conclusion in
`commit.result`. That is why a saved run can truthfully contain “已生成规划文本” while containing no
actual itinerary. This is a harness contract problem, not a dashboard truncation problem.

Future child commits must place the complete required work product in `commit.result` when the task
requires user-readable content. `detail` remains a concise work note. A generic completion
acknowledgement is not a valid substitute for the requested output.

Old runs remain immutable. Their cards must show the exact historical summary and a clear
“历史记录未返回完整正文” diagnostic rather than inventing content.

### 2.3 Same-session runs

No new run-list repository query is required for the first slice. The existing endpoint
`GET /agent/sessions/{sessionId}/messages` returns `runId`, role, content, and creation time. The
frontend groups those messages by `runId`, uses the user message as the label, and opens the existing
run-specific studio endpoint.

## 3. Disclosure and Card Interaction

### 3.1 Whole-card disclosure

Any card with structured detail becomes one semantic `<details>` card:

- the full card header is the `<summary>`;
- clicking anywhere on the card toggles it;
- keyboard Enter/Space works through native semantics;
- the old small “查看全部字段” target is removed;
- nested raw JSON remains a secondary disclosure inside the expanded body only when necessary.

### 3.2 Memory cards

State View and MainNode memory modules initially show only:

```text
全文 1      USER
全文 2      ASSISTANT
历史摘要 1  v3
长期记忆 1  PROFILE
RAG 1       0.87
```

The message body, summary, source metadata, and complete structured fields appear only after the card
opens. Long content must have a bounded reading region rather than expanding the whole detail panel.

### 3.3 Preserved local state

Every expandable element gets a deterministic `data-ui-key`. Before a snapshot update, the studio
captures:

- selected node ID;
- detail scroll position;
- expanded card/module keys;
- loop filter;
- graph scroll position;
- graph zoom;
- run-list state.

After merging the new snapshot, existing nodes update in place and the captured local state is
restored. New server data must not close the detail panel or reset the developer to the top.

## 4. Execution Graph Navigation

The graph remains a left-to-right execution mainline.

### 4.1 Direct manipulation

- Drag empty graph space with the left mouse button to pan horizontally.
- The cursor changes between `grab` and `grabbing`.
- A movement threshold prevents a drag from triggering a node click.
- The native horizontal scrollbar is hidden.
- Mouse wheel keeps normal vertical behavior; Shift+wheel pans horizontally.

### 4.2 Zoom

A compact floating control provides:

- zoom out;
- current zoom percentage;
- zoom in;
- reset/focus latest.

Zoom is bounded to a readable range. It changes the graph canvas only, never the header or detail
panel.

### 4.3 Incremental DOM updates

Polling may still fetch an authoritative studio snapshot, but the graph renderer reconciles by stable
node ID:

- update changed status/text on existing nodes;
- append only new edge/node pairs;
- rebuild only if historical node order actually changed;
- animate only appended nodes;
- focus the latest node only when the user has not manually panned away.

## 5. Same-Session Run Explorer

A compact history icon sits at the left or upper edge of the board.

- Default state: icon plus the number of available runs.
- Hover/focus/click: opens a glass run panel.
- Each row shows the user question, time, run status when available, and active marker.
- Selecting a row changes the run without leaving the studio.
- Switching runs intentionally clears node selection and reconnects debug SSE for the selected run.
- Loop switching remains separate and operates inside the selected run.

## 6. MainNode Planning Layout

### 6.1 Reading order

The MainNode detail starts with:

1. task goal;
2. current decision;
3. ordered step flow;
4. deliverables attached to their steps;
5. blockers and plan revision reason when present.

### 6.2 Effective plan merge

Records are merged by stable ID. A current-loop update changes the existing card in place:

- status transition is shown as `PENDING → COMPLETED`;
- changed fields receive a restrained teal outline and “本轮更新” badge;
- newly added records receive “本轮新增”;
- retained records remain neutral;
- the current step receives the strongest focus.

No separate “本轮新增/更新” column is rendered.

### 6.3 Obsolete and superseded records

A record is historical/inactive when any of these is true:

- its status is `CANCELLED`, `OBSOLETE`, `SUPERSEDED`, `REMOVED`, or equivalent;
- its step ID appears in a plan revision's `cancelledStepIds`;
- a later revision explicitly replaces it.

Inactive records:

- remain in their original order;
- use grey text, low opacity, and a subtle strike-through;
- show an explicit “已废弃” or “已替换” badge;
- keep their expandable fields for causal debugging;
- never compete visually with active steps.

### 6.4 Deliverable placement

Deliverables appear as compact chips/cards inside or beside the step that affects them. A deliverable
linked to multiple steps appears under the first producing step and shows the other related step IDs
as links. Unlinked deliverables appear in a small “未关联交付物” group after the step flow.

## 7. Delegated-Agent Detail

The delegated action detail is ordered as follows.

### 7.1 Parent assignment

One assignment card per task shows:

- name and task ID;
- full objective;
- required output;
- boundary;
- capabilities;
- child run ID and wait mode.

The section includes a secondary “完整 Action JSON” disclosure.

### 7.2 Child execution lane

Each child has a compact lane:

```text
开始 → Loop 1 · COMMIT → Runtime 接受 → 已提交
```

Only action, loop, status, tool/RAG/ask transitions, and failure are shown. Full child prompt and
MainNode-level memory are intentionally omitted.

### 7.3 Actual return to MainNode

The result card prioritizes:

1. complete `commit.result`;
2. status and safe-for-user-visible flag;
3. detail/work note;
4. assumptions;
5. blockers;
6. inspected resources and evidence;
7. suggested parent next step.

If the result is only a generic summary, the UI labels it as such and explains that the historical
child did not return the requested body.

## 8. Action-Specific Decision Layouts

All action nodes use the same top-level reading order:

```text
Intent → Runtime decision → Task/State effect → Next route → Raw evidence
```

### `READY_TO_DELIVER`

Show:

- why MainNode believes work is ready;
- effective step and deliverable completion;
- blockers;
- Runtime readiness status and summary;
- missing evidence/guard findings;
- next route (`FINAL`, repair, or continue).

### `DELEGATE_AGENTS`

Use the delegated-agent layout above.

### `CALL_TOOL`

Show tool goal, resolved capability, arguments, approval, receipt, normalized result, error, evidence,
and state effect.

### `ASK_USER`

Show question, options/input mode, checkpoint, waiting state, normalized answer, resume result, and
next component.

### `RETRIEVE_RAG`

Show query, filters, hit count, selected evidence, no-hit reason, state projection, and next route.

### `FINAL`

Show delivered content first, followed by guard, repair/fallback, references, persisted IDs, and
delivery status.

Raw MainNode action JSON remains available as the last disclosure in every action node.

## 9. Whole-Panel Node Audit Matrix

| Node | Primary question | Default visible content | Secondary content |
|---|---|---|---|
| Context Prepare | What was recalled? | real candidate groups/counts | individual candidates and diagnostics |
| Context Planner | What was selected and why? | input/selected counts and result | full prompt, raw output, validation |
| State View | What did MainNode actually see? | collapsed memory titles, runtime-memory groups | complete values and provenance |
| MainNode | What plan and action did it produce? | ordered effective plan, round memory, action | input memory, attempts, full prompt |
| Tool | What was executed and returned? | intent, arguments, result status | receipt, evidence, raw records |
| Ask User | Why did execution pause/resume? | question, checkpoint, answer state | normalized answer and continuation |
| Child Agent | What was assigned and returned? | task, child lane, complete return | assumptions, resources, raw action |
| RAG | What knowledge affected state? | query, selected hits, outcome | all hits, scores, evidence fields |
| Ready to Deliver | Why is delivery allowed? | completion/readiness decision | task changes, guard details, raw action |
| Final Delivery | What did the user receive? | complete answer and status | guard, refs, persisted identifiers |
| Failure | Where did causality first break? | failed boundary and impact | stack/contract detail and recovery |

Empty secondary modules are omitted. Missing required primary data produces a visible source
diagnostic rather than a zero-count placeholder.

## 10. Testing and Acceptance

### Automated behavior

- Memory cards are closed by default and toggle through the whole card.
- Effective-plan merging preserves order and applies updates by ID.
- Cancelled steps are rendered as inactive history.
- Delegated tasks and lifecycle events join by child run ID.
- Complete child commit content is projected to the parent.
- Snapshot refresh preserves selected node, open keys, detail scroll, loop filter, pan, and zoom.
- New graph nodes append without rebuilding existing nodes.
- Session messages group into a stable run list.
- READY_TO_DELIVER renders completion/readiness semantics.

### Real-run browser acceptance

Using a saved parent run and a new post-fix run:

1. Open State View and verify memory bodies are hidden until a card is clicked.
2. Keep a deep card open while a debug event refreshes; verify the panel and scroll position remain.
3. Drag and zoom the graph without triggering accidental node selection.
4. Switch between at least two runs in one session and back.
5. Verify the plan reads as numbered steps with attached deliverables and no duplicate delta column.
6. Verify inactive planning records are visibly greyed.
7. Verify the delegated action shows both assigned tasks and child lanes.
8. Verify an old run truthfully reports its summary-only child result.
9. Run a new delegated text task and verify the full child result reaches MainNode and the dashboard.
10. Inspect every node type present in the run for hierarchy, empty-state honesty, and useful primary
    information.

## 11. Non-Goals

- Replacing GenericSubAgentRuntime with the parent MainAgent Runtime.
- Persisting child runs as normal parent loops.
- Adding production sampling, retention, or OpenTelemetry.
- Changing the compact planning card in the chat page.
- Inventing missing historical child content.

