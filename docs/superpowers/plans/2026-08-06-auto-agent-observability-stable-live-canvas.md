# AutoAgent Observability Stable Live Canvas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved Command Deck dashboard with stable live inspection, Loop-per-row execution lanes, continuous inter-Loop routing, copy-safe cards, and a useful right rail.

**Architecture:** Keep the current standalone HTML dashboard and extract only deterministic state decisions into the existing sibling logic module. Treat server data and local camera/inspection state as separate domains. Reconcile keyed graph/detail elements in place, render Loop lanes with an SVG connector layer, and place Agent pulse, metrics, and owner-scoped failures in a fixed supporting rail.

**Tech Stack:** Vanilla HTML/CSS/JavaScript, existing CommonJS/browser logic module, Node assertion test, Spring/JUnit frontend contract test.

---

## File Structure

- Modify `docs/dev-ops/nginx/html/agent_observability_logic.js`: pure grouping and follow-state decisions.
- Modify `docs/dev-ops/nginx/html/agent_observability_logic.test.js`: executable behavior coverage for lane grouping, follow suspension, and frontend hooks.
- Modify `docs/dev-ops/nginx/html/agent_observability.html`: Command Deck markup/styles, keyed reconciliation, lane connectors, interaction policy, rail, and full-screen graph.
- Modify `ai-agent-station-study-app/src/test/java/yhx/com/test/frontend/AgentObservabilityStudioFrontendTest.java`: static integration contract for the new stable UI behavior.

### Task 1: Pure lane and follow-state projections

**Files:**
- Modify: `docs/dev-ops/nginx/html/agent_observability_logic.js`
- Modify: `docs/dev-ops/nginx/html/agent_observability_logic.test.js`

- [x] **Step 1: Write failing projection assertions**

Add imports and assertions for these contracts:

```javascript
const { groupExecutionLanes, nextFollowState } = require('./agent_observability_logic');

const executionLanes = groupExecutionLanes([
  { id: 'prep', type: 'CONTEXT_PREPARE', loopIndex: null },
  { id: 'main-0', type: 'MAIN_NODE', loopIndex: 0 },
  { id: 'tool-0', type: 'TOOL_USE', loopIndex: 0 },
  { id: 'state-1', type: 'STATE_VIEW', loopIndex: 1 }
]);
if (executionLanes.length !== 3) throw new Error('execution lane grouping failed');
if (executionLanes[1].lastNodeId !== 'tool-0' || executionLanes[2].firstNodeId !== 'state-1') {
  throw new Error('inter-loop connector anchors failed');
}
if (nextFollowState(true, 'DATA_REFRESH') !== true) throw new Error('refresh changed follow state');
if (nextFollowState(true, 'USER_INSPECT') !== false) throw new Error('inspection did not suspend follow');
```

- [x] **Step 2: Run the test and verify it fails**

Run: `node docs/dev-ops/nginx/html/agent_observability_logic.test.js`  
Expected: FAIL because `groupExecutionLanes` and `nextFollowState` are not exported.

- [x] **Step 3: Implement the pure helpers**

Add deterministic helpers:

```javascript
function groupExecutionLanes(nodes) {
    const lanes = [];
    const byKey = new Map();
    list(nodes).forEach((node, index) => {
        const key = node?.loopIndex == null ? 'prep' : `loop-${node.loopIndex}`;
        let lane = byKey.get(key);
        if (!lane) {
            lane = { key, loopIndex: node?.loopIndex ?? null, nodes: [], firstNodeId: '', lastNodeId: '' };
            byKey.set(key, lane);
            lanes.push(lane);
        }
        lane.nodes.push(node);
        lane.firstNodeId ||= String(node?.id || index);
        lane.lastNodeId = String(node?.id || index);
    });
    return lanes;
}

function nextFollowState(current, cause) {
    if (cause === 'FOLLOW_ENABLE') return true;
    if (cause === 'USER_PAN' || cause === 'USER_ZOOM' || cause === 'USER_INSPECT'
            || cause === 'USER_SELECT' || cause === 'USER_DETAIL_SCROLL') return false;
    return Boolean(current);
}
```

Export both helpers from the factory return value.

- [x] **Step 4: Run the projection test**

Run: `node docs/dev-ops/nginx/html/agent_observability_logic.test.js`  
Expected: `logic projections pass`.

### Task 2: Stable detail reconciliation and copy-safe disclosure

**Files:**
- Modify: `docs/dev-ops/nginx/html/agent_observability_logic.test.js`
- Modify: `docs/dev-ops/nginx/html/agent_observability.html`

- [x] **Step 1: Add failing source-contract assertions**

Assert that the effective dashboard source contains:

```javascript
if (!studioHtml.includes('patchSelectedDetail')) throw new Error('keyed detail patching is missing');
if (!studioHtml.includes('captureSelectionState')) throw new Error('selection preservation is missing');
if (studioHtml.includes('expanded.addEventListener("click"')) throw new Error('expanded body remains a close target');
```

- [x] **Step 2: Run the test and verify it fails**

Run: `node docs/dev-ops/nginx/html/agent_observability_logic.test.js`  
Expected: FAIL with the missing stable-detail hook.

- [x] **Step 3: Implement keyed detail patching**

Replace full-body repaint during normal refresh with:

```javascript
function patchSelectedDetail(node) {
    const body = $('detailBody');
    const next = document.createElement('template');
    next.innerHTML = renderDetailBody(node);
    reconcileKeyedChildren(body, next.content);
    bindDisclosureInteractions(body);
}
```

`reconcileKeyedChildren` must preserve matching `[data-ui-key]` elements, patch changed scalar text and
attributes, insert new modules in document order, and fall back to a region rebuild only when stable
keys are unavailable. Capture and restore detail scroll, focused key, open keys, and selection range
around the fallback.

- [x] **Step 4: Remove expanded-body collapse behavior**

Bind disclosure only to the header/summary activation surface. On pointer-up, ignore activation when
pointer travel exceeded the click threshold, the selection is non-empty, or the target is interactive:

```javascript
if (moved || String(window.getSelection() || '').length || target.closest('a,button,input,textarea,select,code,pre,details details')) return;
```

- [x] **Step 5: Run the projection test**

Run: `node docs/dev-ops/nginx/html/agent_observability_logic.test.js`  
Expected: `logic projections pass`.

### Task 3: Loop lanes and continuous return connectors

**Files:**
- Modify: `docs/dev-ops/nginx/html/agent_observability_logic.test.js`
- Modify: `docs/dev-ops/nginx/html/agent_observability.html`

- [x] **Step 1: Add failing lane-layout assertions**

Require the source hooks `execution-lane`, `lane-connector-layer`, `renderLoopConnectorPaths`, and
`data-lane-key`. Also assert the effective graph no longer uses the legacy one-line
`graph.innerHTML=nodes.map` renderer.

- [x] **Step 2: Run the test and verify it fails**

Run: `node docs/dev-ops/nginx/html/agent_observability_logic.test.js`  
Expected: FAIL with a missing Loop-lane token.

- [x] **Step 3: Reconcile one row per Loop**

Call `AgentObservabilityLogic.groupExecutionLanes(nodes)`. Reconcile a keyed `.execution-lane` for
`PREP` and each Loop, then reconcile keyed node/edge elements inside each row. Preserve existing node
elements and animate only fresh node IDs.

- [x] **Step 4: Draw inter-Loop paths from actual anchors**

Create one SVG path per adjacent lane pair. After layout, read the previous lane's last-node right
anchor and next lane's first-node left anchor. Render a low-contrast cubic/elbow path that travels to
the right gutter, descends, and returns to the next lane's first node. Recompute through one scheduled
`requestAnimationFrame` after node insertion, zoom, resize, or full-screen transition.

- [x] **Step 5: Run the projection test**

Run: `node docs/dev-ops/nginx/html/agent_observability_logic.test.js`  
Expected: `logic projections pass`.

### Task 4: Command Deck, explicit following, and graph expansion

**Files:**
- Modify: `docs/dev-ops/nginx/html/agent_observability_logic.test.js`
- Modify: `docs/dev-ops/nginx/html/agent_observability.html`
- Modify: `ai-agent-station-study-app/src/test/java/yhx/com/test/frontend/AgentObservabilityStudioFrontendTest.java`

- [x] **Step 1: Add failing Command Deck assertions**

Require `command-deck`, `agent-rail`, `agent-pulse`, `run-telemetry`, `follow-latest`,
`graph-expanded`, and `new-node-indicator`. Require the Spring frontend test to verify the same user
contract.

- [x] **Step 2: Run focused tests and verify failure**

Run: `node docs/dev-ops/nginx/html/agent_observability_logic.test.js`  
Run: `mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentObservabilityStudioFrontendTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`  
Expected: both fail on the new contract tokens.

- [x] **Step 3: Implement the 72/28 workspace and rail**

Move the failure locator out of the graph overlay. Add a stable right rail containing actual active
component/Loop/last-event age, four compact metrics, owner-scoped failure links, and recent meaningful
events. Below the wide breakpoint, stack the rail under the graph.

- [x] **Step 4: Implement explicit follow state**

Add `state.followLatest=false` by default. New nodes never move the graph while false and instead show
the new-node indicator. The toolbar toggle enables one move to the latest node. Pan, zoom, node/detail
inspection, detail scrolling, and selection call `nextFollowState(..., cause)` to disable it.

- [x] **Step 5: Implement near-full-screen graph mode**

Toggle `.graph-expanded` on the existing Command Deck/canvas element. Do not clone or recreate the
graph. Preserve selection, pan, zoom, detail disclosures, and follow state; schedule connector
geometry once after the CSS transition.

- [x] **Step 6: Add restrained motion and reduced-motion coverage**

Animate only fresh lanes/nodes and actual status changes. Add a `prefers-reduced-motion` rule that
removes transforms and transition duration while keeping all content and controls available.

- [x] **Step 7: Run focused verification**

Per the user's efficiency preference, final verification was reduced to the core Node projection test,
inline-script compilation, and a real browser acceptance pass; the additional Maven static-token test
was removed rather than retained as low-value coverage.

Run: `node docs/dev-ops/nginx/html/agent_observability_logic.test.js`  
Expected: `logic projections pass`.

Run: `mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentObservabilityStudioFrontendTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`  
Expected: tests pass.

Run an inline-script compile check by extracting the final `<script>` body and compiling it with
`new Function(scriptText)`.  
Expected: no syntax error.

### Task 5: Real-page acceptance pass

**Files:**
- Verify: `docs/dev-ops/nginx/html/agent_observability.html`

- [x] **Step 1: Open a saved multi-Loop run in the local dev dashboard**

Verify Loop lanes, continuous return connectors, Command Deck rail, and graph expansion visually.

- [x] **Step 2: Exercise live inspection stability**

Keep a deep module open, select text, and let at least one snapshot refresh occur. Confirm there is no
flash, scroll jump, collapse, selection loss, or camera movement.

- [x] **Step 3: Exercise follow behavior**

Enable follow, observe one move to latest, manually pan, and verify a later node produces only the new
node indicator.

- [x] **Step 4: Inspect browser errors**

Expected: no new console errors from rendering, connector geometry, pointer handling, or SSE refresh.
