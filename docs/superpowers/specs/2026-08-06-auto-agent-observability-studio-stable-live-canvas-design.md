# AutoAgent Observability Studio Stable Live Canvas Design

**Date:** 2026-08-06  
**Status:** Approved design direction  
**Scope:** Current dev Runtime observability dashboard frontend  
**Builds on:** `2026-08-03-auto-agent-observability-studio-usability-refinement-design.md`

## 1. Outcome

Adopt the approved **Command Deck** composition and make the dashboard safe to inspect while an
Agent run is still changing. The execution chain remains the visual subject. Live data may update at
any time, but it must not take control away from a developer who is reading, selecting text, panning,
zooming, or inspecting a node.

This refinement must deliver:

1. stable incremental updates without detail-panel repaint flicker;
2. stable graph position unless the developer explicitly enables latest-node following;
3. copy-safe disclosure cards;
4. one visual lane per Loop with a continuous return connector between lanes;
5. a 72/28 Command Deck layout with graph expansion;
6. a compact, meaningful Agent-status rail that does not compete with the execution graph;
7. restrained motion that explains change without moving existing content.

## 2. Command Deck Composition

The normal dashboard uses a two-column workspace:

```text
┌──────────────────────────────────────────────────────────────────────────┐
│ Run header · run selector · live state · view controls                  │
├───────────────────────────────────────────────────┬──────────────────────┤
│ Execution canvas (about 72%)                      │ Agent rail (about 28%)│
│                                                   │                      │
│ PREP lane                                         │ Agent pulse          │
│ LOOP 0 lane                                       │ Run telemetry        │
│ LOOP 1 lane                                       │ Compact failures      │
│ ...                                               │ Recent meaningful evt │
└───────────────────────────────────────────────────┴──────────────────────┘
```

The execution canvas is the primary region. The rail is a supporting instrument panel and must never
overlay or shrink individual graph nodes unpredictably.

On narrower viewports, the rail moves below the graph rather than becoming an overlay. Its sections
remain compact and independently collapsible.

## 3. Loop-Lane Execution Topology

### 3.1 One Loop per row

Context preparation occupies a `PREP` lane. Every Runtime loop occupies one subsequent horizontal
lane. Nodes inside a lane continue left to right in event order.

```text
PREP    [Context Prepare] → [Context Planner]
                                         ╲
LOOP 0  [State View] → [MainNode] → [Delegate] → [Child Result]
                                                     ╲
LOOP 1  [State View] → [MainNode] → [Ready] → [Final]
```

### 3.2 Continuous row transition

The transition between loops is not a connector from the first node of one row to the first node of
the next row. It must originate at the **last node of the previous lane**, route to the right through
a quiet elbow/curve, descend one lane, and return to the **first node of the next lane**.

The connector:

- represents actual chronological continuity;
- is thinner and lower-contrast than same-lane arrows;
- stays behind cards;
- has a small loop-transition label only when useful;
- does not consume a card-sized amount of space;
- animates only when first inserted and never pulses continuously.

An SVG overlay or equivalent path layer should calculate the endpoints from stable node anchors.
Hard-coded coordinates are not acceptable because lane width changes as events arrive.

### 3.3 Incremental insertion

A new event may append a node to the current lane or create the next lane. Existing lane elements and
connectors retain their DOM identity. Only the new node and the affected final edge are inserted or
updated.

## 4. Live Data and Local Reading State

The dashboard has two state domains that must never be conflated.

### 4.1 Authoritative run state

Server snapshots and SSE events own:

- run and node status;
- node sequence and Loop assignment;
- node data, failures, attempts, and outputs;
- run telemetry and timestamps.

### 4.2 Local inspection state

The browser owns:

- selected node;
- detail-panel scroll position;
- expanded module/card keys;
- focused element;
- active text selection;
- graph pan and zoom;
- manual-pan/follow-latest mode;
- full-screen graph state;
- rail disclosure state.

Server updates must not overwrite local inspection state.

### 4.3 Stable reconciliation rules

Each graph node, module, card, field group, lane, and failure row receives a deterministic key.
Snapshot reconciliation follows these rules:

1. preserve an existing DOM element when its key still exists;
2. patch only fields whose value or status changed;
3. append new keyed elements without rebuilding their parent region;
4. remove an element only when authoritative history genuinely removes it;
5. preserve `details.open`, scroll positions, focus, and selection ranges;
6. do not assign `innerHTML` to the entire selected-node detail body during ordinary refresh;
7. rebuild a region only for a structural incompatibility, then restore all captured local state.

If the selected node receives new content, its existing modules update in place. A quiet “已更新”
marker may appear on changed modules; the developer stays at the same reading position.

## 5. Follow-Latest Policy

Real-time refresh and camera movement are separate behaviors.

- Live refresh stays enabled while the run is active.
- `跟随最新` is an explicit toggle and the only normal permission to move the canvas automatically.
- Any manual pan, zoom, node inspection, detail scroll, text selection, or card interaction disables
  following immediately.
- When following is disabled, newly appended nodes may create a small “有新节点” indicator, but the
  canvas does not move.
- Re-enabling following animates once to the latest node and then follows future nodes.
- A new node never calls unconditional `scrollTo(..., behavior: "smooth")`.

This policy applies even when the user has not selected a node. Passive viewing is still a valid
inspection state and must remain stable.

## 6. Disclosure and Copy Interaction

Cards retain whole-header disclosure without making content selection a toggle action.

- A collapsed card opens when its header/card activation surface is clicked.
- An expanded card closes through its header or an explicit compact collapse affordance.
- Expanded content is not a close target.
- Pointer movement beyond the click threshold is treated as selection/drag, not activation.
- If the browser selection is non-empty at pointer-up, no disclosure changes.
- Links, buttons, nested disclosures, code blocks, and copy controls stop card-toggle handling.
- Native keyboard activation remains available through semantic buttons or `summary` elements.

This removes the current failure mode in which releasing the mouse after copying text collapses the
module.

## 7. Motion System

Motion communicates insertion and state change, not decoration.

- New lane: short fade/translate from 8–12 px.
- New node: restrained scale/fade with no layout bounce.
- Status change: color/border cross-fade on the existing card.
- Changed detail module: brief low-opacity highlight that does not alter dimensions.
- Connectors: path reveal only on initial insertion.
- Detail opening: height/opacity transition with a stable scroll anchor.
- Full-screen transition: transform/opacity transition that preserves graph pan and zoom.

All repeated live refreshes with no visible data change produce no animation. Respect
`prefers-reduced-motion` by disabling nonessential transitions.

## 8. Graph Expansion and Direct Manipulation

The execution canvas header provides:

- zoom out;
- zoom percentage/reset;
- zoom in;
- follow-latest toggle;
- fit current run;
- near-full-screen expand/restore.

Expanding the graph keeps the same graph instance and local state. It does not re-render the graph
into a second container. The detail inspector can remain as a drawer or overlay inside expanded mode,
but opening it must not recalculate the graph camera.

Pan behavior keeps the already-established movement threshold: a short stationary click selects a
node; a real pointer movement pans. Pointer capture updates synchronously so the canvas stays under
the cursor.

## 9. Agent Rail

The rail contains only information that improves orientation or debugging.

### 9.1 Agent pulse

Show the currently active component and plain-language state, for example:

- `MainNode 正在思考`;
- `等待 2 个子 Agent`;
- `Tool Use 正在执行`;
- `等待用户输入`;
- `正在进行最终校验`.

Also show Loop index, elapsed time, and last-event age. This is derived from actual lifecycle data and
is not decorative fiction.

### 9.2 Run telemetry

Use a small metric grid for Loop count, visible node count, action/attempt count, and active child
count. Stable run/session identifiers appear once in the header rather than repeating inside nodes.

### 9.3 Compact failure locator

The rail shows only failure count, failure name/code, owning node, and Loop. Selecting a row navigates
to the owning node without forcing graph auto-follow afterward. Full evidence remains in that node's
detail panel.

Failures appear only at their owning node plus this global locator; they are not duplicated into
unrelated node panels.

### 9.4 Recent meaningful events

Optionally show a short list of state transitions such as child committed, approval requested, repair
attempted, or final guard failed. Routine polling and unchanged refreshes are omitted.

## 10. Implementation Boundary

The first implementation should modify the existing dev dashboard rather than introduce a frontend
framework migration. It should consolidate the currently shadowed duplicate render helpers into one
effective path where practical.

Expected ownership:

- graph/lane reconciliation and connectors: dashboard graph renderer;
- local inspection state and keyed detail patching: dashboard UI state/reconciler;
- current Agent pulse and telemetry: projection from existing studio snapshot/events;
- full-screen, follow, zoom, and pan: dashboard interaction layer;
- failure navigation: existing owner-aware failure projection plus new rail placement.

No Runtime contract change is required unless a status value needed by the Agent pulse is genuinely
absent from all existing lifecycle events.

## 11. Acceptance Criteria

1. Keep a deep detail card open while multiple events arrive: no flash, collapse, scroll jump, focus
   loss, or text-selection loss occurs.
2. Leave the graph untouched while a node arrives with follow disabled: pan and viewport do not move.
3. Enable follow latest: the graph moves to the newest node; manually pan and verify following stops.
4. Select and copy multiline content inside an expanded card: the card remains open.
5. Click an expanded card header: it closes; click its body: it stays open.
6. Verify every Runtime Loop occupies one row.
7. Verify the connector originates at the previous row's last node and terminates at the next row's
   first node.
8. Verify appending to a lane does not recreate or animate unchanged nodes.
9. Expand and restore the graph: selection, pan, zoom, open cards, and current Loop remain intact.
10. Verify the rail reports the actual active component, metrics, and owner-scoped failures.
11. Verify reduced-motion mode remains fully usable.

## 12. Non-Goals

- Replacing the dev dashboard with React/Vue or another frontend framework.
- Changing the Runtime execution model.
- Adding production observability retention or sampling modes.
- Showing raw debug payloads in the normal chat interface.
- Automatically moving the canvas merely because live data arrived.
