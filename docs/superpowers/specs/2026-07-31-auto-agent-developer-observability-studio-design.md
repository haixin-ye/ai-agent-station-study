# AutoAgent Developer Observability Studio Design

**Date:** 2026-07-31  
**Status:** Approved design direction; implementation not started  
**Scope:** Current development environment only

## 1. Purpose

Build a dedicated developer observability panel for the AutoAgent Runtime. The panel must make one
run's complete execution understandable as a live visual mainline while keeping detailed data behind
hover and click interactions.

This is not a conventional text-log viewer. It is a visual execution board for evaluating and
debugging the harness:

- what happened in every loop;
- what MainNode actually received;
- where each StateView item came from;
- what MainNode returned;
- how Runtime interpreted the action;
- what tools, RAG, user checkpoints, or child agents returned;
- how state changed before the next loop;
- why a final answer was poor, incomplete, or failed.

The first implementation targets the current dev runtime. Full capture is the default. Production
capture modes, OpenTelemetry, broad retention policy, and multi-run evaluation centers are deferred.

## 2. Confirmed User Decisions

1. The main board shows compact summaries only. Real data appears on hover or click.
2. The first version targets the current dev environment.
3. `runId`, `sessionId`, `userId`, and `agentId` are not repeated in the execution chain. They appear
   once in a fixed Run Header.
4. MainNode StateView, the sources that produced it, complete per-loop execution, tool results, child
   agent returns, and state changes are the highest-priority data.
5. MainNode prompt is shown in full, exactly as the model saw it. The first version keeps the original
   English text and does not translate it into Chinese.
6. Mainline stages and action labels are animated and appended from left to right as events arrive.
7. Content uses cards; action names are labels on causal arrows; complex stages open as large modular
   panels.
8. JSON is rendered as semantic fields and collapsible hierarchy. Raw JSON is an optional fallback
   view, never the default.

## 3. Product Surface

The chat page gets a single developer-only button such as `运行观测`. Clicking it opens an independent
full-viewport Trace Studio. The chat view becomes the launch context and is not displayed beside the
studio.

### 3.1 Visual language

- dark translucent glass surface;
- rounded panels and cards;
- thin low-contrast borders;
- soft blue, violet, teal, yellow, and red state accents;
- high-contrast short labels;
- restrained shadows and blur;
- smooth node entrance, edge drawing, focus, waiting, retry, and error animations.

The supplied dark migration panel is a visual reference for hierarchy, contrast, rounded containers,
and restrained typography. The supplied ASCII panel is an information-architecture reference only;
the implementation must be graphical rather than text-grid based.

### 3.2 Board structure

```text
┌─────────────────────────────────────────────────────────────┐
│ Run Header: status / current stage / loop / duration / close│
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  user_input → context → MainNode → tool_use → tool result   │
│       │             │          │                            │
│       │        memory cards  ask_user → checkpoint → resume │
│       │                         │                            │
│       └────────────── state changes / next loop ────────────┘
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ loop selector / event cursor / lightweight status timeline  │
└─────────────────────────────────────────────────────────────┘
```

The actual board is an animated graph with stable positions. Existing nodes do not jump when a new
event arrives. New nodes append to the right; branches use lower lanes; loop-back edges use curved
paths.

## 4. Interaction Model

### 4.1 Mainline level

One runtime phase is one compact node. An action is a label on the outgoing edge:

```text
上下文准备 → MainNode ── tool_use ──→ 工具执行
MainNode ── ask_user ──→ 等待用户
MainNode ── retrieve_rag ──→ RAG
MainNode ── delegate ──→ Child Agents
MainNode ── final ──→ Final Delivery
```

### 4.2 Hover level

Hover opens a small floating glass popover with only quick facts:

- title;
- status;
- loop number;
- action;
- duration;
- count or short summary;
- error badge if present.

Hover never displays a large JSON object and does not obscure the board.

### 4.3 Click level

Click pins a large detail module over the board. The graph dims but remains visible behind it. The
module is stage-specific and contains semantic subcards, field groups, collapsible lists, references,
and diff highlighting.

Escape or clicking outside returns to the board. The selected node remains highlighted.

## 5. Run Header

The following data appears exactly once at the top and is not repeated in stage cards:

| Field | Chinese meaning |
|---|---|
| `runId` | 本次运行的唯一标识 |
| `sessionId` | 所属会话 |
| `userId` | 当前用户 |
| `agentId` | 使用的 Agent |
| `status` | 运行状态 |
| `currentStage` | 当前阶段 |
| `loopCount` | 已执行轮数 |
| `duration` | 当前总耗时 |
| `errorCount` | 错误数 |

The first four identifiers are shown in a compact metadata row, with copy actions. They are not part
of the animated mainline.

## 6. MainNode-Centered Data Priority

The main debugging question is: “Why did this run produce an unsatisfactory result?” The information
architecture therefore follows the MainNode loop rather than the database table structure.

Every MainNode loop has a visible loop marker:

```text
Loop 0 → Loop 1 → Loop 2 → Loop 3 → Final
```

Clicking a loop marker filters the board and inspector to that loop while keeping links to previous
and next loops.

For each loop, the panel must connect these snapshots:

```text
StateView sources
      ↓
Candidate merge / planner selection
      ↓
Materialized StateView
      ↓
MainNode prompt + model call
      ↓
MainNode action
      ↓
Runtime action execution
      ↓
Tool/RAG/User/Child result
      ↓
State change
      ↓
Next loop
```

This causal chain is more important than a generic chronological log.

## 7. Stage Content Specification

The following table defines the first-version data boundary. “Board” means always visible in the
overview. “Hover” means quick facts. “Click” means full structured details. “Deferred” means not
shown in the first dev slice.

### 7.1 User input / Run creation

**Board:** `用户输入`

**Hover:**

- input summary;
- Run status;
- creation time;
- current loop.

**Click:**

- complete user input;
- initial runtime context reference;
- initial request type;
- initial state creation result.

**Deferred:**

- raw HTTP headers;
- transport diagnostics that do not affect Runtime behavior.

### 7.2 Context preparation

**Board:** `上下文准备`

Under the node, show compact candidate cards:

```text
全文1 全文2 全文3 全文4 全文5 全文6
摘要1 摘要2 摘要3
记忆1 记忆2 记忆3
RAG1  RAG2
证据1 证据2
任务摘要
```

**Hover:**

- candidate counts;
- MySQL/vector/RAG branch status;
- selected count;
- token estimate;
- timeout/fallback badge.

**Click:**

1. **MySQL Recall / MySQL 召回**
   - recent full turns;
   - historical summaries;
   - session task summary;
   - user clarifications;
   - existing evidence.
2. **Vector Recall / 向量召回**
   - query;
   - collection;
   - source ID;
   - score;
   - resolved source;
   - snippet.
3. **RAG Recall / 知识库召回**
   - query;
   - knowledge base;
   - document/chunk;
   - rank and score;
   - retrieval time.
4. **Merge / 合并**
   - before/after counts;
   - deduplicated candidates;
   - winning source;
   - rejection reason;
   - token budget effect.

Each candidate is rendered as labeled fields, not as a raw object:

```text
来源：上一轮完整对话
角色：用户 / 助手
时间：...
摘要：...
引用：payload-...
是否选中：是
```

**Deferred:**

- SQL text;
- embedding arrays;
- database connection internals;
- provider-specific diagnostic payloads.

### 7.3 ContextPlanner

**Board:** `上下文选择`

**Hover:**

- planner status;
- selected count;
- latency;
- repair badge.

**Click:**

- planner input candidate groups;
- selected source IDs;
- context level;
- selection reason;
- not-selected candidates and reason;
- full planner prompt;
- raw output;
- parse result;
- contract result;
- repair attempts.

**Deferred:**

- raw JSON as the default view; it remains available only under `Raw`.

### 7.4 State View construction

**Board:** `State View`

**Hover:**

- memory count;
- evidence count;
- RAG count;
- state token count;
- materialization status.

**Click:**

1. **User Task / 用户任务**
   - current input;
   - goal;
   - clarifications;
   - format requirements.
2. **Conversation / 会话**
   - recent messages;
   - historical summaries;
   - session task summary.
3. **Memory Pack / 记忆包**
   - memory type;
   - summary;
   - content;
   - source;
   - selected/injected flag.
4. **RAG Pack / 知识库包**
   - document;
   - chunk;
   - summary;
   - bounded content;
   - injection mode.
5. **Evidence Pack / 证据包**
   - evidence type;
   - source;
   - summary;
   - confidence;
   - used-by-final flag.
6. **TaskLedger / 任务台账**
   - goal;
   - steps;
   - deliverables;
   - blockers;
   - current step;
   - revision;
   - facts;
   - ledger version.
7. **Runtime Control / 运行控制**
   - stage;
   - loop index;
   - maximum loop;
   - remaining loops;
   - recovery counters.
8. **Token Budget / Token 预算**
   - maximum state tokens;
   - reserved output tokens;
   - current candidate tokens;
   - truncation;
   - over-budget state.

This is the highest-priority inspector in the product.

**Deferred:**

- duplicate serialized payload copies;
- historical objects not visible to this MainNode call;
- raw Java serialization.

### 7.5 MainNode

**Board:** `MainNode`

**Hover:**

- loop number;
- model;
- status;
- action name;
- latency;
- attempt count.

**Click:**

1. **Prompt Layers / Prompt 层**
   - full original prompt text;
   - exact order;
   - role prompt;
   - runtime rules;
   - task procedure;
   - decision policy;
   - output contract;
   - current state view;
   - output instruction;
   - layer source;
   - character/token size.
2. **Model Request / 模型请求**
   - model code;
   - temperature;
   - max output tokens;
   - invocation mode;
   - function specifications;
   - system prompt;
   - user prompt.
3. **Model Output / 模型输出**
   - parsed action;
   - action fields;
   - tool intent;
   - ask request;
   - state delta;
   - final candidate.
4. **Contract Pipeline / 契约流程**
   - raw output;
   - parse status;
   - normalized structure;
   - contract result;
   - violations;
   - typed output.
5. **Attempts / 尝试**
   - attempt number;
   - original failure;
   - repair request;
   - repair output;
   - accepted attempt.

Prompt content is displayed in its original form. The first version does not translate it into
Chinese.

**Deferred:**

- hidden model chain-of-thought;
- raw prompt as the default board content;
- implementation internals of the parser.

### 7.6 Action routing

**Board:** action text on the edge:

```text
tool_use
ask_user
retrieve_rag
delegate
ready_to_deliver
final
fail
```

**Hover:**

- action;
- current stage;
- route status;
- handler;
- short result.

**Click:**

- structured action fields;
- state delta;
- stage policy result;
- handler input;
- handler output;
- state before/after;
- state diff;
- next phase.

**Deferred:**

- raw JSON string as the primary representation.

### 7.7 Tool execution

**Board:** `工具执行`

**Hover:**

- tool name;
- status;
- latency;
- evidence count.

**Click submodules:**

```text
Tool Intent / 工具意图
Capability / 能力解析
Arguments / 参数物化
Permission / 权限校验
Approval / 用户批准
Invocation / 实际调用
Receipt / 工具回执
Verification / 结果验证
Evidence / 证据生成
State Effect / 状态效果
```

**Deferred:**

- secrets;
- authentication headers;
- internal network logs.

### 7.8 User ask / checkpoint

**Board:** `ask_user`

**Hover:**

- question;
- option count;
- input mode;
- waiting status.

**Click:**

- question;
- input mode;
- options;
- free-text policy;
- source component;
- handler;
- pending input ID;
- continuation reference;
- checkpoint payload;
- run context version;
- loop record version;
- resume phase;
- expected answer type;
- user answer;
- normalized answer;
- consumption result;
- restore result;
- continuation result.

**Animation:**

```text
ask_user 出现
→ checkpoint 卡片生成
→ 主线暂停并进入等待光晕
→ 用户回答节点出现
→ resume 连线绘制
→ 回到下一轮 MainNode
```

### 7.9 RAG

**Board:** `retrieve_rag`

**Hover:**

- query;
- hit count;
- selected evidence count;
- success/no-hit;
- latency.

**Click:**

- query;
- knowledge base;
- filters;
- documents;
- chunks;
- rank;
- score;
- selected evidence;
- no-hit reason;
- verification;
- projected StateView data.

### 7.10 Child Agent

**Board:** `delegate`

Child runs appear as lower lanes:

```text
Parent
 ├── Child A
 ├── Child B
 └── Child C
       ↓
Result Projection
       ↓
Parent Resume
```

**Click:**

- task ID;
- objective;
- boundary;
- capabilities;
- parent context;
- child run ID;
- wait mode;
- child output;
- projection result;
- resumed parent loop.

### 7.11 Final delivery

**Board:**

```text
ready_to_deliver → final → delivered
```

**Click:**

- final candidate;
- readiness result;
- RAG verification;
- final guard;
- guard failures;
- repair attempts;
- fallback;
- persisted content;
- final message ID;
- actual delivered answer.

### 7.12 Failure and recovery

**Board:** red stage or broken edge:

```text
contract_error
runtime_route_error
tool_error
checkpoint_error
final_guard_error
persistence_error
```

**Hover:**

- failure code;
- short Chinese explanation;
- first failed boundary;
- affected loop.

**Click:**

- developer message;
- user-safe message;
- exception type;
- stack trace;
- upstream span;
- downstream impact;
- recovery result.

## 8. Observation and Rendering Rules

The Runtime emits structured observation facts. The frontend never reconstructs the chain from
ordinary text logs.

Every observation has:

```text
runId
loopIndex
spanId
parentSpanId
eventType
seq
status
timestamp
payload references
```

The current `agent_run`, `agent_run_context`, `agent_run_loop`, `agent_run_trace`, `agent_run_event`,
and `agent_payload` records remain the migration baseline. The first implementation may extend
compatibility projections while introducing the missing causal and payload references.

Critical lifecycle events must not be silently dropped. If a large payload cannot be captured, the
board must show `payload omitted` rather than pretending the data exists.

## 9. Dev-Only First Slice

### Must include

- full-viewport developer panel;
- Run Header with one-time identity metadata;
- live left-to-right mainline;
- incremental node and edge animation;
- loop markers;
- compact content cards;
- action labels on edges;
- hover summary;
- click stage modules;
- MainNode full StateView and source chain;
- full MainNode prompt in original English;
- MainNode output and contract pipeline;
- tool result;
- RAG result;
- user ask and checkpoint;
- child agent result;
- state diff between loops;
- final candidate and delivered result;
- first failed boundary.

### Deferred

- production capture modes;
- OpenTelemetry;
- advanced authorization matrix;
- retention policy management;
- multi-run evaluation center;
- automatic LLM diagnosis;
- side-effectful re-execution replay;
- broad historical search.

## 10. Acceptance Criteria

The first implementation is accepted when:

1. A running dev request adds nodes and arrows without page refresh.
2. Existing nodes remain stable while new nodes append.
3. `runId`, `sessionId`, `userId`, and `agentId` appear once in the Run Header only.
4. Each loop can be selected and inspected independently.
5. A MainNode click reveals the exact StateView, all visible sources, complete prompt, raw output,
   parsed action, contract result, and attempts.
6. The inspector can connect StateView source → MainNode input → action → effect → next loop.
7. Tool, RAG, user checkpoint, and child-agent results are visible as structured modules.
8. JSON is rendered as labeled fields, nested sections, and diffs rather than a raw wall of text.
9. A failed run highlights the first failed boundary and shows downstream impact.
10. The board remains a visual overview even when the underlying payload is large.

## 11. Next Step

This specification defines the agreed design boundary. The next step is to create an implementation
plan that follows the existing DDD modules and current RunContext/timeline work, then implement the
dev-only observation foundation and Trace Studio incrementally.
