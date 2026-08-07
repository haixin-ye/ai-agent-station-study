# AutoAgent Observability Studio Usability Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the dev observability studio a readable, truthful, incrementally updated debugger for parent loops, MainNode memory, delegated agents, actions, planning, and final delivery.

**Architecture:** Keep the existing Runtime and `/debug/studio` contract as the source of truth. Add small pure JavaScript projection helpers for effective plans, session runs, and child lifecycle lanes, then use them from the existing standalone HTML. Preserve local UI state across authoritative snapshot refreshes and change child prompt guidance so future delegated text work returns the actual required work product.

**Tech Stack:** Java 17, Spring Boot, JUnit 4, standalone HTML/CSS/JavaScript, Node.js test runner, existing SSE and REST endpoints.

---

### Task 1: Add deterministic observability projections

**Files:**
- Create: `docs/dev-ops/nginx/html/agent_observability_logic.js`
- Create: `docs/dev-ops/nginx/html/agent_observability_logic.test.js`

- [ ] **Step 1: Write failing Node tests**

```js
const { mergeEffectivePlan, groupSessionRuns, groupChildLifecycle } = require('./agent_observability_logic');

const plan = mergeEffectivePlan(
  { steps: [{ id: 's1', title: '旧步骤', status: 'PENDING', affectedDeliverableIds: ['d1'] }], deliverables: [{ id: 'd1', title: '交付物', status: 'PENDING', relatedStepIds: ['s1'] }] },
  { stepUpdates: [{ id: 's1', status: 'COMPLETED' }, { id: 's2', title: '新步骤', status: 'CANCELLED' }], deliverableUpdates: [{ id: 'd1', status: 'READY' }] }
);
if (plan.steps[0].status !== 'COMPLETED' || !plan.steps[0].changed || !plan.steps[1].obsolete) throw new Error('effective plan merge failed');
if (groupSessionRuns([{ runId: 'r1', role: 'USER', content: '第一问' }, { runId: 'r1', role: 'ASSISTANT', content: '答复' }])[0].label !== '第一问') throw new Error('run grouping failed');
const lanes = groupChildLifecycle([{ type: 'CHILD_STARTED', childRunId: 'c1' }, { type: 'CHILD_ACTION', childRunId: 'c1', loopIndex: 1, action: 'COMMIT' }, { type: 'CHILD_COMMITTED', childRunId: 'c1', commit: { result: '完整结果' } }], [{ taskId: 't1', childRunId: 'c1', name: '规划' }]);
if (lanes[0].assignment.name !== '规划' || lanes[0].result !== '完整结果') throw new Error('child lane projection failed');
console.log('logic projections pass');
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `node docs/dev-ops/nginx/html/agent_observability_logic.test.js`

Expected: FAIL with `Cannot find module './agent_observability_logic'`.

- [ ] **Step 3: Implement the pure helpers**

Export CommonJS functions and attach the same object to `window.AgentObservabilityLogic` in browsers. Merge records by `id`/`taskId`, preserve ledger order, mark `changed`, `newRecord`, `obsolete`, and `replacementState`, attach deliverables through declared relationship IDs, group messages by `runId`, and join child events to delegated tasks by `childRunId`.

- [ ] **Step 4: Run the test and verify it passes**

Run: `node docs/dev-ops/nginx/html/agent_observability_logic.test.js`

Expected: `logic projections pass`.

- [ ] **Step 5: Commit the isolated projection helpers**

```bash
git add docs/dev-ops/nginx/html/agent_observability_logic.js docs/dev-ops/nginx/html/agent_observability_logic.test.js
git commit -m "test: add observability projection helpers"
```

### Task 2: Make detail cards honest and state-preserving

**Files:**
- Modify: `docs/dev-ops/nginx/html/agent_observability.html`
- Modify: `ai-agent-station-study-app/src/test/java/yhx/com/test/frontend/AgentObservabilityStudioFrontendTest.java`

- [ ] **Step 1: Add static frontend contracts**

Assert the page contains `AgentObservabilityLogic`, `data-ui-key`, `captureDetailState`, `restoreDetailState`, `run-explorer`, `graph-pan`, and `zoom-control`; assert it no longer uses the old `查看全部字段` mini-card disclosure.

- [ ] **Step 2: Run the focused test and verify the new contracts fail**

Run: `mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentObservabilityStudioFrontendTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL because the new state-preservation and run-explorer tokens are absent.

- [ ] **Step 3: Replace mini-card disclosure with whole-card disclosure**

Render structured modules as `<details class="mini-card" data-ui-key="...">` with a compact `<summary>` containing only kicker, title, and metric. Put copy and structured fields in the body. Memory cards use the same pattern and start closed; empty secondary modules are omitted and required missing data uses a source diagnostic.

- [ ] **Step 4: Preserve local state around refresh**

Capture selected node, `detailBody.scrollTop`, open `data-ui-key` values, loop filter, graph scroll, graph transform, and active run before replacing snapshot data. Restore them after rendering. Do not close the detail layer during `load()`.

- [ ] **Step 5: Run the focused test and verify it passes**

Run the same Maven command. Expected: PASS.

- [ ] **Step 6: Commit the disclosure and refresh behavior**

```bash
git add docs/dev-ops/nginx/html/agent_observability.html ai-agent-station-study-app/src/test/java/yhx/com/test/frontend/AgentObservabilityStudioFrontendTest.java
git commit -m "feat: preserve observability detail state during refresh"
```

### Task 3: Render one ordered effective plan

**Files:**
- Modify: `docs/dev-ops/nginx/html/agent_observability.html`
- Test: `docs/dev-ops/nginx/html/agent_observability_logic.test.js`

- [ ] **Step 1: Add merge assertions for attached deliverables and obsolete records**

Test that `affectedDeliverableIds` and `relatedStepIds` attach the same deliverable to its producing step, that status changes render as `PENDING -> COMPLETED`, and that `CANCELLED`, `OBSOLETE`, `SUPERSEDED`, and `REMOVED` are inactive.

- [ ] **Step 2: Implement `renderPlanBoard` from `mergeEffectivePlan`**

Use numbered step cards as the only primary axis. Place related deliverable chips inside each step, place unlinked deliverables in a final “未关联交付物” group, and render current-loop additions/changes as small badges on the original card. Apply `.plan-record-obsolete` with grey text, reduced opacity, strike-through title, and an explicit status badge. Never render a separate delta column.

- [ ] **Step 3: Reuse the same plan projection for READY_TO_DELIVER task effects**

Show completion transitions, blockers, and readiness route using the same step and deliverable IDs so action effects explain the exact plan record they changed.

- [ ] **Step 4: Run Node and frontend tests**

Run: `node docs/dev-ops/nginx/html/agent_observability_logic.test.js` and the focused Maven frontend test. Expected: both PASS.

- [ ] **Step 5: Commit the ordered plan board**

```bash
git add docs/dev-ops/nginx/html/agent_observability.html docs/dev-ops/nginx/html/agent_observability_logic.test.js
git commit -m "feat: show effective ordered plan with obsolete history"
```

### Task 4: Make delegated work returnable and inspectable

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/prompt/GenericSubAgentPromptBuilder.java`
- Modify: `docs/dev-ops/mysql/patches/auto-agent-latest-prompts-only.sql`
- Modify: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/harness/SubAgentActionContractTest.java`
- Modify: `docs/dev-ops/nginx/html/agent_observability.html`

- [ ] **Step 1: Add a prompt contract regression test**

Build the generic child prompt and assert it says `commit.result` is the complete required work product for user-readable delegated tasks, while `detail` is only a concise work note.

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn -q -pl ai-agent-station-study-app -am "-Dtest=SubAgentActionContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`. Expected: FAIL because the current prompt forbids long required output in `result`.

- [ ] **Step 3: Align Java and SQL prompt wording**

Permit multiline Markdown in `commit.result` when `requiredOutput` asks for a document, itinerary, report, or other user-readable artifact. Keep JSON valid by requiring proper escaping at the serialization boundary, and retain structured fields for assumptions, resources, and evidence.

- [ ] **Step 4: Render child assignment, lifecycle lane, and actual commit result**

Load `/agent/runs/{runId}/events?limit=200`, project delegated tasks and events with `groupChildLifecycle`, show the exact assignment first, put raw action JSON behind a secondary disclosure, show each child’s compact action/loop lane, and put the full historical `commit.result` before notes. If only a generic acknowledgement exists, render an explicit history diagnostic instead of inventing content.

- [ ] **Step 5: Run the backend and frontend focused tests**

Expected: PASS for the prompt contract and frontend static contracts.

- [ ] **Step 6: Commit the child contract and inspector**

```bash
git add ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/prompt/GenericSubAgentPromptBuilder.java docs/dev-ops/mysql/patches/auto-agent-latest-prompts-only.sql ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/harness/SubAgentActionContractTest.java docs/dev-ops/nginx/html/agent_observability.html
git commit -m "feat: expose delegated assignments and complete child results"
```

### Task 5: Add graph pan/zoom and incremental reconciliation

**Files:**
- Modify: `docs/dev-ops/nginx/html/agent_observability.html`
- Modify: `docs/dev-ops/nginx/html/agent_observability_logic.test.js`

- [ ] **Step 1: Add pure reconciliation assertions**

Verify unchanged node elements keep their stable IDs and only new nodes are classified as appended.

- [ ] **Step 2: Add graph interaction state**

Implement left-button drag on empty graph space with a movement threshold, Shift+wheel horizontal pan, bounded zoom controls, and a reset/focus-latest action. Store transform and scroll state outside snapshot data.

- [ ] **Step 3: Reconcile graph DOM by node ID**

Update status/text in existing node elements, append new edge/node pairs, rebuild only when order changes, animate appended nodes, and auto-focus the newest node only before the user manually pans.

- [ ] **Step 4: Run Node tests and browser-static tests**

Expected: PASS.

- [ ] **Step 5: Commit graph interaction**

```bash
git add docs/dev-ops/nginx/html/agent_observability.html docs/dev-ops/nginx/html/agent_observability_logic.test.js
git commit -m "feat: add stable graph pan zoom and incremental updates"
```

### Task 6: Add same-session run explorer

**Files:**
- Modify: `docs/dev-ops/nginx/html/agent_observability.html`
- Modify: `docs/dev-ops/nginx/html/agent_observability_logic.test.js`

- [ ] **Step 1: Test run grouping**

Cover multiple run IDs, user-message labels, timestamps, active marker, and empty messages.

- [ ] **Step 2: Load and render the explorer**

Fetch `/agent/sessions/{sessionId}/messages`, group by run ID, render a compact icon/count trigger and glass list, and show question, time, status, and active state.

- [ ] **Step 3: Switch runs without leaving the studio**

Change the active run ID, clear selected node intentionally, close the old EventSource, load the selected snapshot, and reconnect its debug SSE. Keep the run panel usable while snapshot updates arrive.

- [ ] **Step 4: Run all focused tests and commit**

Expected: Node and frontend tests PASS.

```bash
git add docs/dev-ops/nginx/html/agent_observability.html docs/dev-ops/nginx/html/agent_observability_logic.test.js
git commit -m "feat: browse runs within the current session"
```

### Task 7: Audit every node and verify a real run

**Files:**
- Modify: `docs/dev-ops/nginx/html/agent_observability.html`
- Modify: `ai-agent-station-study-app/src/test/java/yhx/com/test/frontend/AgentObservabilityStudioFrontendTest.java`
- Modify: `progress.md`

- [ ] **Step 1: Add static coverage for all node decisions**

Assert action-specific labels and modules for Context Prepare, Context Planner, State View, MainNode, Tool, Ask User, Child Agent, RAG, Ready to Deliver, Final Delivery, and Failure.

- [ ] **Step 2: Run focused tests**

Run frontend and Node tests. Expected: PASS with no placeholder/empty required module regressions.

- [ ] **Step 3: Compile the application**

Run: `mvn -q -DskipTests compile`. Expected: BUILD SUCCESS.

- [ ] **Step 4: Start or reuse the dev server safely and inspect a saved run**

Use the existing dev process if its port is healthy. Otherwise start the documented dev server on a free port, open the saved parent run, and verify memory disclosure, plan ordering, obsolete styling, child lane/result, preserved refresh state, graph drag/zoom, and run switching.

- [ ] **Step 5: Record evidence and residual risks**

Update `progress.md` with commands, real run IDs, screenshots or browser observations, and any limitation such as historical child runs that only contain a summary.

- [ ] **Step 6: Run diff checks and complete the branch review**

Run `git diff --check` and inspect only task files before reporting completion. Do not stage unrelated user changes.

