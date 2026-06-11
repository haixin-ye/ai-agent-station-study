# AutoAgent Main Loop Harness Working Notes

> Status: working notes, not final spec.
> Purpose: record agreed design decisions during brainstorming before producing the final spec and implementation plan.

## 1. Overall Direction

The current fixed multi-node harness should be redesigned around a single main agent loop.

The new direction is:

- `IntentRouter`（意图路由器）classifies the request and enables capabilities.
- `MainAgentNode`（主智能体节点）is the main decision-making LLM node.
- `Runtime`（运行时）executes actions, attaches capabilities, records evidence, controls loop progress, and emits events.
- `Verifier`（验收器）components run as hooks when specific evidence needs validation.
- `AgentState`（智能体状态）replaces the old loosely shared `DynamicContext`（动态上下文）semantics.

The design should avoid forcing simple tasks through the old `Node1 -> Node2 -> Node3 -> Node4` chain.

## 2. Core Runtime Shape

High-level flow:

```text
request
-> MemoryProbe（记忆探测）
-> MemoryRetrieval（记忆召回）
-> MemoryPackBuilder（记忆包构建器）
-> IntentRouter（意图路由器）
-> MainAgentLoop（主智能体循环）
-> FinalAnswer（最终回答）
```

Main loop:

```text
build StateView（状态视图）
-> MainAgentNode（主智能体节点）
-> Runtime executes Action（动作） or observes Spring AI tool calls
-> Observation（观察结果） writes AgentState（智能体状态）
-> Verifiers（验收器） run if needed
-> continue / ask user / final
```

`MainAgentNode`（主智能体节点） handles both simple and complex tasks. Complex tasks are not delegated to a separate `StepExecutor`（步骤执行器） in the core design.

## 3. MainAgentNode Prompt Design

`MainAgentNode`（主智能体节点） must not rely on one huge prompt.

Prompt assembly:

```text
Core System Prompt（核心系统提示词）
+ Runtime Policy Fragment（运行时策略片段）
+ StateView JSON（状态视图 JSON）
+ Capability Summary（能力摘要）
+ Output Contract（输出契约）
```

Core prompt responsibilities:

- Define `MainAgentNode`（主智能体节点） as the main decision maker.
- Use only data provided in `StateView`（状态视图）.
- Treat `RAG`（检索增强生成） facts as valid only when backed by retrieved chunks and citations.
- Treat tool actions as valid only when backed by real tool receipts or callback records.
- Treat cross-turn reusable content as `Artifact`（产物）, not raw chat text.
- Use `MemoryPack`（记忆包） to understand user intent and preferences, not as external factual evidence.
- Prioritize `VerificationState`（验收状态） blocking issues.
- Do not expose internal nodes, traces, JSON, route decisions, verifier text, or tool internals in final answers.

Dynamic policy fragments include:

- `DIRECT_POLICY`（直接回答策略）
- `RAG_QUERY_POLICY`（RAG 查询策略）
- `RAG_ANSWER_POLICY`（RAG 回答策略）
- `TOOL_POLICY`（工具策略）
- `AFTER_TOOL_POLICY`（工具后续策略）
- `AFTER_VERIFIER_FAIL_POLICY`（验收失败策略）
- `ASK_USER_POLICY`（询问用户策略）

## 4. AgentState / DynamicContext Redesign

`DynamicContext`（动态上下文） should be redesigned as `AgentState`（智能体状态） with separated state areas:

- `GoalState`（目标状态）: raw user input, normalized goal, success criteria.
- `RouteState`（路由状态）: task mode, enabled capabilities, route reason, confidence.
- `MemoryState`（记忆状态）: instruction memory, preference memory, conversation summary, relevant history.
- `ArtifactState`（产物状态）: referenced artifacts, working artifacts, accepted artifacts, latest draft, final answer.
- `RagState`（RAG 状态）: knowledge name, retrieval query, retrieved chunks, citations, grounding verdict.
- `ToolState`（工具状态）: tool intent, selected tools, tool receipts, callback records, side effect status.
- `LoopState`（循环状态）: iteration, max iterations, last action, last observation, compact trace, pending user question, done.
- `VerificationState`（验收状态）: verifier results, blocking issues, warnings, retry advice.
- `TraceState`（轨迹状态）: user visible events, debug events, token usage.

Rules:

- Nodes and components read a narrow `StateView`（状态视图）, not the full state.
- Memory helps intent understanding but is not external factual evidence.
- RAG facts come from `RagState`（RAG 状态）.
- Tool facts come from `ToolState`（工具状态） and `EvidenceState`（证据状态）.
- Reusable cross-turn content comes from `ArtifactState`（产物状态）.
- Final answer comes only from `ArtifactState.finalAnswer`（最终回答） or a verified final output path.

## 5. Memory Lifecycle

Memory is split into separate categories:

- `ConversationRecord`（会话原文记录）: user and assistant final messages.
- `ConversationSummary`（会话摘要）: turn summary, rolling summary, topic summary, memory pack snapshot.
- `TopicStack`（主题栈）: current session topic chain.
- `ArtifactMemory`（产物记忆）: articles, drafts, code diffs, files, URLs, reports.
- `RunTrace`（运行轨迹）: internal actions, observations, verifier results.
- `CompactTrace`（压缩轨迹）: short internal loop summary for the next iteration.
- `LongTermMemory`（长期记忆）: stable preferences, project rules, long-term topic index.

Memory lifecycle:

```text
read lightweight indexes
-> MemoryProbe（记忆探测）
-> MemoryRetrieval（记忆召回）
-> ArtifactResolver（产物解析器）, if needed
-> MemoryPackBuilder（记忆包构建器）
-> inject MemoryPack（记忆包） into StateView（状态视图）
-> after final answer, persist message, summary, topic, artifacts, evidence, trace, and long-term memory candidates
```

`MemoryProbe`（记忆探测） should use LLM semantic understanding plus rule fallback.

`MemoryRetrieval`（记忆召回） is mainly Java/database retrieval, with optional LLM rerank or compression.

`MemoryPack`（记忆包） is the only memory package injected into `MainAgentNode`（主智能体节点）.

## 6. Memory Persistence Tables

Agreed table set:

- `conversation_message`（会话消息表）
- `conversation_summary`（会话摘要表）
- `conversation_topic`（会话主题表）
- `agent_artifact`（智能体产物表）
- `agent_run_trace`（运行轨迹表）
- `agent_evidence`（证据表）
- `user_long_term_memory`（用户长期记忆表）

The design should not use a simplified temporary persistence model. These tables should be part of the final design.

## 7. Artifact Handling

Generated reusable outputs must be stored as artifacts, not only as chat text.

Examples:

- article（文章）
- draft（草稿）
- file（文件）
- URL（链接）
- report（报告）
- code diff（代码差异）
- plan（计划）

User references such as "这个", "刚才那篇", "上一版", "把它发布" must go through `ArtifactResolver`（产物解析器）.

If no unique artifact is found, the system should `ASK_USER`（询问用户）, not guess.

## 8. Capability Registry

Capabilities（能力） should be registered through a simple configuration model first, likely `application-*.yml`（环境配置文件）, with possible MySQL（数据库） migration later.

Sources:

- Built-in capabilities（内置能力）: LLM, memory, artifact store, token usage.
- MCP capabilities（MCP 工具能力）: discovered from Spring AI MCP tool schemas, completed by YAML policy.
- RAG capabilities（RAG 能力）: enabled by `knowledgeName`（知识库名称） and policy.

Policy categories:

- `READ_ONLY_TOOL`（只读工具）
- `SIDE_EFFECT_TOOL`（副作用工具）
- `DANGEROUS_TOOL`（危险工具）

Users should not need to know which node loads a capability. Capabilities are enabled for `MainAgentNode`（主智能体节点） through `CapabilityPolicy`（能力策略）.

## 9. RAG and Tool Execution

RAG（检索增强生成） should be explicit:

```text
MainAgentNode -> RETRIEVE_RAG（检索 RAG）
Runtime executes retrieval
retrievedChunks（检索片段） write RagState / Evidence
MainAgentNode answers in the next loop
RagVerifier validates grounding
```

MCP tools（MCP 工具） should initially keep Spring AI automatic tool calling:

```text
Runtime attaches allowed MCP tools to ChatClient（聊天客户端）
MainAgentNode decides tool and parameters during LLM call
Spring AI executes actual tool call
RecordingToolCallback records callbackRecords（回调记录）
Runtime writes ToolState / Evidence
ToolVerifier validates
MainAgentNode finalizes in the next loop
```

Runtime does not replace Spring AI tool calling. It wraps it with capability selection, safety policy, receipt recording, verification, and event emission.

## 10. Runtime Responsibilities

`Runtime`（运行时） is Java orchestration code, not an LLM node.

Responsibilities:

- Build `StateView`（状态视图）.
- Build and call `MainAgentNode`（主智能体节点）.
- Attach allowed capabilities.
- Execute explicit actions like `RETRIEVE_RAG`（检索 RAG）, `ASK_USER`（询问用户）, `CREATE_ARTIFACT`（创建产物）, future `SPAWN_SUBAGENT`（启动子智能体）.
- Observe automatic Spring AI tool calls.
- Persist observations and evidence.
- Run verifiers.
- Emit user-visible and debug events.
- Enforce max iterations and context budget.

## 11. ASK_USER and User Interaction

`ASK_USER`（询问用户） must be a first-class action.

Use cases:

- Ambiguous artifact reference.
- Missing required parameter.
- High-risk side-effect tool.
- Multiple options requiring user selection.
- Conflicting task constraints.

Frontend should support:

- 2-5 clickable options（选项）.
- Free-form custom answer.
- Resume pending run（待恢复运行） on next user message.

`ASK_USER` state must be persisted so execution can resume after the current SSE（服务端事件流） ends.

## 12. Code Capability

Full coding agent（代码智能体） architecture is deferred.

For future development:

- File/code operations can be exposed as file system MCP tools.
- `MainAgentNode` decides how to use them.
- Runtime handles approval, receipt recording, and fact-level verification.
- Full project-wide context planning, code quality verification, and deep repository understanding are future work.

This is also recorded in `docs/superpowers/future-dev-tasks.md`.

## 13. Frontend Events and Trace

Events must be split:

- `UserVisibleEvent`（用户可见事件）: concise status for normal UI.
- `DebugTraceEvent`（调试轨迹事件）: full structured debugging information.

Default UI should only show user-visible events.

Examples:

- 正在理解你的问题
- 正在检索知识库
- 正在调用工具：CSDN 发布
- 正在确认工具结果
- 正在生成最终回答
- 需要你确认一个选项
- 已完成

Debug mode may show:

- `StateView`（状态视图）
- prompt（提示词）
- action（动作）
- observation（观察结果）
- verifier result（验收结果）
- state delta（状态增量）
- token usage（Token 用量）

Final answers must never be generated from debug trace, raw result, verifier assessment, or execution summary.

## 14. Error Handling and Recovery

Unified error categories:

- `ROUTE_UNCLEAR`（路由不清）
- `MEMORY_AMBIGUOUS`（记忆不明确）
- `ARTIFACT_NOT_FOUND`（产物未找到）
- `RAG_NO_HIT`（RAG 未命中）
- `RAG_UNGROUNDED`（RAG 回答无依据）
- `TOOL_NOT_CALLED`（工具未调用）
- `TOOL_SCHEMA_ERROR`（工具参数结构错误）
- `TOOL_FAILED`（工具失败）
- `TOOL_UNVERIFIED`（工具未验收）
- `USER_APPROVAL_REQUIRED`（需要用户批准）
- `MODEL_FORMAT_ERROR`（模型格式错误）
- `CONTEXT_OVER_BUDGET`（上下文超限）
- `MAX_ITERATION_REACHED`（达到最大迭代次数）

Recovery should be handled by Runtime（运行时） plus `MainAgentNode`（主智能体节点） retries, not by leaking raw internal errors to the user.

## 15. Context Budget and Compression

Before building each `StateView`（状态视图）, run `ContextBudgetCheck`（上下文预算检查）.

Budget sections:

- Core prompt（核心提示词）
- Policy fragment（策略片段）
- MemoryPack（记忆包）
- Artifacts（产物）
- Evidence（证据）
- Tool schemas（工具结构）
- Recent observation（最近观察）
- Output contract（输出契约）

If over budget:

1. Remove debug trace（调试轨迹）.
2. Compress compact trace（压缩轨迹）.
3. Use artifact summary before full content.
4. Limit retrieved chunks（检索片段）.
5. Limit visible tool schemas（工具结构） to enabled tools.
6. Compress MemoryPack（记忆包）.
7. Escalate to future ContextPlanner（上下文规划器） if still over budget.

Context auto-planning is recorded as future development work.

## 16. Future Development Tasks

Tracked separately in `docs/superpowers/future-dev-tasks.md`:

- Context auto-planning（上下文自动规划）
- Subagent scheduling（子智能体调度）
- Coding agent capability（代码智能体能力）


## 17. Runtime, ContextPlanner, Artifact Resolution

Runtime（运行时） remains deterministic Java orchestration code, not an LLM brain. It creates runs, loads state, calls context planning, builds `StateView`（状态视图）, invokes `MainAgentNode`（主智能体节点）, executes actions, records evidence, runs verifiers, persists events/traces/artifacts, and controls the loop.

`ContextPlanner`（上下文规划器） is a lightweight LLM-assisted node used before `MainAgentNode`. It decides what memory, artifact, evidence, and context granularity the main node should receive for the current user turn. It does not generate final answers and does not execute tools.

The preferred strategy is LLM-first with cheap Java pre-selection:

1. Runtime uses Java rules and retrieval to prepare candidate recent messages, artifact metadata, session summaries, long-term memory candidates, and pending run facts.
2. ContextPlanner receives only these compact candidates, not the full history.
3. ContextPlanner returns a structured context plan, including needed memories, artifact references, context level, ambiguity, and whether user confirmation is required.
4. Runtime validates the plan, applies token budget constraints, loads required payloads, and builds the final `StateView`.

Java pre-selection is only rough recall and ordering, not final semantic judgment. It uses stable signals:

- `recencyScore`（最近性分数）: recent messages and recently mentioned artifacts rank higher.
- `explicitReferenceScore`（明确指代分数）: words like "刚才", "上一轮", "这个", "那个", "第二版" increase related candidate priority.
- `keywordOverlapScore`（关键词重合分数）: title, summary, alias, and user input overlap.
- `typeHintScore`（类型暗示分数）: verbs like 发布, 修改, 润色, 总结, 运行 hint candidate types.
- `sessionScopeScore`（会话范围分数）: current session candidates rank above older or global candidates.

The scorer should be implemented as small pluggable scorer classes, not scattered if/else logic. Each scorer returns a numeric score plus reasons. Java only classifies results into `HIGH_CONFIDENCE`（高置信）, `CANDIDATES_ONLY`（只有候选）, or `NO_CANDIDATE`（无候选）. In most cases the candidates still go to ContextPlanner for semantic confirmation.

Artifact IDs are internal. Users never need to know them. `ArtifactResolver`（产物解析器） resolves references such as "这个 RAG 八股文", "刚才那版", "第二个方案", and "那篇文章" by combining candidate scoring, recency, aliases, summaries, and ContextPlanner judgment. If multiple artifacts remain plausible, Runtime triggers `ASK_USER`（询问用户）.

Artifact content injection is controlled by `ArtifactContextPolicy`（产物上下文策略）:

- `METADATA_ONLY`（只给元信息）: for publish/upload/archive/delete style tasks. MainAgentNode sees artifact id, title, type, and summary; Runtime loads full payload only for the tool.
- `SUMMARY_PLUS_SNIPPET`（摘要加片段）: for overview, title suggestion, light evaluation, or routing decisions.
- `FULL_TEXT`（全文）: for short article/code review, polishing, structure edits, or detailed comparison.
- `CHUNKED_CONTEXT`（分块上下文）: for long artifacts that exceed budget. Runtime provides outline/summary first, then loads requested chunks in later loop iterations.

The important rule is that full artifact text is loaded into the current `StateView` only when required by the task and budget. It is not permanently copied into conversation history.

## 18. MainAgentNode Output Actions and Runtime Handling

`MainAgentNode`（主智能体节点） outputs a structured action. Runtime handles the action with deterministic code.

- `FINAL`（最终回答）: Runtime runs `FinalResponseGuard`（最终响应保护器）, stores `FinalResponse`, emits completed event, and ends the run.
- `CREATE_ARTIFACT`（创建产物）: Runtime stores artifact metadata and payload, records evidence, then either returns a final message or continues the loop.
- `UPDATE_ARTIFACT`（更新产物）: Runtime validates target artifact, stores a new version or child artifact, records lineage, and continues or finalizes.
- `RETRIEVE_RAG`（检索知识库）: Runtime executes retrieval, stores RAG evidence, emits retrieval events, and starts the next loop with evidence summary.
- `CALL_TOOL`（调用工具）: Runtime executes explicit tools or observes Spring AI automatic MCP tool calls, records tool receipts as evidence, runs tool verification, and continues the loop.
- `ASK_USER`（询问用户）: Runtime stores waiting state, emits options to the frontend, pauses the run, and resumes when the user answers.
- `PLAN`（计划）: Runtime stores the plan in state, does not show it as final answer, and asks MainAgentNode for the next concrete action in a later loop.
- `CONTINUE`（继续）: Runtime records trace, checks loop limits, and continues. This should be rare to avoid empty loops.
- `REPAIR_FINAL`（修复最终回答）: Runtime asks MainAgentNode to rewrite only the final user-facing answer, then re-runs final guard.
- `FAIL`（失败）: Runtime stores failure details, emits a user-safe failure response, and keeps developer trace for diagnosis.

The boundary is: ContextPlanner decides what the main node should see; MainAgentNode decides what should happen next; Runtime executes, stores, verifies, and controls loop safety.
## 19. Consolidated Design Checkpoint 2026-04-29

This checkpoint records agreed decisions that must survive context loss.

### Harness Problem Statement

The current fixed multi-node harness has several structural issues:

- Final answers can leak intermediate reasoning, verification summaries, or node traces.
- Node prompts and node-to-node contracts are too verbose and ambiguous.
- Frontend displays raw JSON-like internal objects instead of user-facing progress.
- The chain can reject otherwise good answers because a verifier checks formatting too rigidly.
- Dynamic context can grow too fast and hit context length limits.
- The design is too coupled to current Node1-4 responsibilities and should be redesigned around clearer orchestration boundaries.

### Target Architecture

Use a main-loop architecture:

- `Runtime`（运行时）: deterministic Java orchestration and safety boundary.
- `ContextPlanner`（上下文规划器）: lightweight LLM-assisted context planner before the main node.
- `MainAgentNode`（主智能体节点）: primary LLM brain for decision and generation.
- `Verifier`（验收器）: specialized validators for RAG, tools, and final response quality.
- `Memory`（记忆）: session, summary, long-term, run-level, and artifact memory.
- `Evidence`（证据）: facts from RAG, tools, memory, user confirmation, and artifacts.
- `Artifact`（产物）: reusable long-form outputs such as articles, code, files, or structured drafts.

The old Node1-4 design does not need to be preserved. Existing xfg-style chain scaffolding can be reused only if it fits the new design.

### Runtime Boundary

Runtime does not think in natural language and does not generate final answers. It:

1. Creates run/session/message records.
2. Loads recent state and candidate context.
3. Calls ContextPlanner when needed.
4. Builds `StateView` for MainAgentNode.
5. Invokes MainAgentNode.
6. Executes or observes actions.
7. Stores events, traces, evidence, artifacts, and audit records.
8. Runs verifiers and final guards.
9. Controls loop continuation, waiting, failure, and completion.

### ContextPlanner Boundary

ContextPlanner is allowed to use an LLM because user language is often ambiguous. It should be lightweight and operate on compact candidates, not the full history.

Inputs:

- Current user input.
- Recent message candidates.
- Session summary candidates.
- Artifact metadata candidates.
- Long-term memory candidates.
- Pending run facts and evidence summaries.
- Token budget.

Outputs:

- Whether memory is needed.
- Which artifact candidates are relevant.
- Required artifact context level: metadata, snippet, full text, or chunked.
- Whether RAG is likely needed.
- Whether tool use is likely needed.
- Whether ambiguity requires `ASK_USER`.

### Java Candidate Preselection

Java preselection is rough recall, not final semantic judgment. It should avoid complex brittle rules.

Stable scorer signals:

- Recency: recent messages and artifacts rank higher.
- Explicit references: words like 刚才, 上一轮, 这个, 那个, 第二版.
- Keyword overlap: user input against title, summary, alias.
- Type hints: 发布, 修改, 润色, 总结, 运行, 删除.
- Session scope: current session before older sessions or global memory.

Each scorer returns score plus reason. Aggregation only produces high confidence, candidates-only, or no-candidate. ContextPlanner resolves most semantic ambiguity.

### Artifact Resolution

Users never provide artifact IDs. Runtime resolves them before MainAgentNode sees the state.

Examples:

- "把这个 RAG 八股文发布到 CSDN" resolves recent article artifact matching RAG/八股文.
- "修改一下第二版结构" resolves the second version artifact and likely requires full text.
- "说说这个文章的亮点" resolves the target article and loads full text or relevant chunks.

If multiple artifacts are plausible, Runtime emits `ASK_USER` with selectable options.

### Artifact Context Policy

Context levels:

- `METADATA_ONLY`: publish/upload/archive/delete style operations.
- `SUMMARY_PLUS_SNIPPET`: overview, title suggestion, light evaluation.
- `FULL_TEXT`: short article/code review, polish, restructuring, detailed comparison.
- `CHUNKED_CONTEXT`: long artifact over budget; load outline first, then specific chunks across loops.

Full artifact text is loaded into current `StateView` only when needed and budget allows. It is not copied permanently into conversation history.

### MainAgentNode Actions

MainAgentNode outputs structured actions only. Runtime switches on action type.

- `FINAL`: validate with FinalResponseGuard, store final answer, finish.
- `CREATE_ARTIFACT`: store metadata and payload, then final or continue.
- `UPDATE_ARTIFACT`: validate target, store new version or child artifact, then final or continue.
- `RETRIEVE_RAG`: run retrieval, store evidence, continue next loop.
- `CALL_TOOL`: execute explicit tool or observe Spring AI MCP automatic tool call, store receipt evidence, verify, continue.
- `ASK_USER`: store waiting state and frontend options, pause run.
- `PLAN`: store plan internally, do not expose as final, continue toward concrete action.
- `CONTINUE`: continue loop with loop-limit guard; should be rare.
- `REPAIR_FINAL`: rewrite only user-facing answer after guard failure.
- `FAIL`: store failure and return user-safe error.

### Display and Logging Boundary

Normal frontend view uses only:

- User-visible events.
- Final response.
- Artifact list when relevant.
- User selection options when waiting.

Debug view may show:

- StateView summaries.
- Node input/output summaries.
- Actions.
- RAG/tool evidence.
- Verifier results.
- Token usage and latency.

Final answers must never be assembled from debug trace, raw output, verifier summary, tool receipt, memory summary, or runtime status.

### DDD Direction

Keep the project DDD style. A single top-level `agent` domain remains acceptable, but split internals by responsibility:

- execute/runtime orchestration.
- memory.
- artifact.
- evidence.
- verification.
- capability/tool/rag integration.
- event/trace/audit.
- armory for client/model/advisor/MCP assembly.

Armory remains infrastructure-like assembly for model clients and capabilities, not business orchestration.
## 20. Confirmed Tool Execution Separation

This design decision is confirmed.

`MainAgentNode`（主智能体节点） must not mount MCP tools directly. Direct Spring AI automatic tool calling inside MainAgentNode risks breaking the action contract because the model may mix tool-use behavior, natural-language final answers, and structured action JSON.

All tool use must be represented as a `CALL_TOOL`（调用工具） action emitted by MainAgentNode. Runtime validates the action and then delegates tool execution to `ToolExecutionNode`（工具执行节点）.

`ToolExecutionNode` is a narrow LLM-assisted execution node. It may mount MCP tools through Spring AI and use tool-use mode because it does not need to preserve the MainAgentNode action contract. Its job is only to translate tool intent into real tool calls and return structured execution results.

Responsibilities:

- MainAgentNode: decide that a tool is needed and produce `CALL_TOOL` intent.
- Runtime: validate intent, apply risk approval, provide artifacts/payloads, invoke ToolExecutionNode, capture receipts, run verification, and control lifecycle.
- ToolExecutionNode: select tool, build arguments, call MCP tool through Spring AI, and return execution summary.
- ToolVerifier: verify actual captured receipt and detect tool-not-called, tool-failed, or fake success.

ToolExecutionNode must not generate final user answers, update plans, or control run status. If it claims success but Runtime captures no real tool receipt, Runtime records `TOOL_NOT_CALLED`（工具未调用） and does not allow final completion.

High-risk tools such as publish, delete, overwrite, command execution, network write, install, or risky git operations must pass `ASK_USER`（询问用户） approval before ToolExecutionNode executes them.

This keeps MainAgentNode clean, preserves unified action contracts, and makes tool execution testable and auditable.
## 21. Confirmed Runtime Loop and Detail Design Rule

Runtime loop state machine direction is confirmed.

Confirmed loop model:

- Runtime controls lifecycle states with deterministic Java state machine.
- MainAgentNode only emits semantic actions and state deltas.
- MainAgentNode must not write `runStatus`, `nextState`, `loopIndex`, or lifecycle control fields.
- Tool use is a dedicated path: `CALL_TOOL` action -> Runtime validation and approval -> ToolExecutionNode -> ToolVerifier -> evidence -> next MainAgentNode loop.
- ToolExecutionNode may mount MCP tools, but it must not generate final answers or control the run lifecycle.
- Final answers remain centralized through MainAgentNode and FinalResponseGuard.

Confirmed detail design rule:

When a module enters refined design, all meaningful cases must be covered. For example, when designing MainAgentNode output actions, every action JSON shape must be specified, not just selected examples. Future sections should be exhaustive at the level currently being designed.
## 22. Confirmed Persistence and Repository Direction

Phase 3 persistence and repository direction is accepted with delegated detail design.

Storage groups:

- Conversation（会话）: user-visible session and message records.
- Run（运行）: lifecycle state for one user request.
- Memory（记忆）: session summaries, topic summaries, long-term user/project memory, memory events.
- Artifact（产物）: reusable generated content such as articles, code, files, plans, summaries, versions, and aliases.
- Evidence（证据）: facts used by the agent, including RAG, tool, memory, artifact, and user confirmation evidence.
- Tool（工具）: tool intent, approval, call facts, receipts, and verification results.
- RAG（检索增强生成）: per-run retrieval queries and hits.
- Event/Trace/Audit（事件/轨迹/审计）: user-visible progress, debug trace, token/latency statistics.
- Payload（载荷）: large text, JSON, raw tool receipts, RAG chunks, prompts, model outputs, and artifact bodies.

Repository direction:

- Domain defines repository interfaces.
- Infrastructure implements MyBatis/DAO/storage adapters.
- Runtime, managers, and verifiers access persistence through repositories.
- ContextPlanner, MainAgentNode, and ToolExecutionNode do not directly read or write database tables; Runtime builds their views and stores their outputs.

Artifact persistence rule:

- Persist long-form, reusable, tool-dependent, versioned, or user-referenceable outputs.
- Do not persist ordinary short answers as artifacts unless they become referenced or reusable.
- Long content is loaded from payload into StateView or ToolExecutionView only when the context policy and token budget allow it.

This phase will be expanded into concrete SQL, indexes, enums, and repository method signatures in the final spec.
## 23. Confirmed DDD Layout and Configuration Layering

Phase 4 DDD module/package layout is confirmed.

Top-level domain remains `agent`（智能体领域）. Do not split memory, tool, artifact, runtime, and evidence into separate top-level domains for the first redesign, because they all serve the same agent run lifecycle.

Internal domain submodules:

- `execute`（执行）: runtime loop, state machine, loop policy, state view builder.
- `context`（上下文）: ContextPlanner, candidate preselection, context budget, artifact context policy, artifact resolver.
- `node`（节点）: MainAgentNode and ToolExecutionNode wrappers.
- `memory`（记忆）: memory retrieval, summary, long-term memory management.
- `artifact`（产物）: artifact persistence, versioning, aliases, payload delivery.
- `evidence`（证据）: evidence recording and retrieval.
- `tool`（工具）: tool intent preparation, risk approval, receipt recording.
- `rag`（检索增强生成）: RAG query execution and evidence building.
- `verification`（验收）: final response guard, tool verifier, RAG verifier.
- `event`（事件）: user-visible events, developer trace, audit recording.
- `armory`（装配库）: model/client/advisor/MCP assembly.

Layering:

- Domain defines entities, value objects, services, and repository interfaces.
- Infrastructure implements repositories, DAOs, payload storage, RAG adapters, and MCP receipt capture.
- App assembles Spring beans and loads configuration.
- Trigger exposes HTTP/API and frontend event endpoints.

Configuration layering is confirmed:

- Java Config（Java 配置类） assembles beans and dependency graph.
- yml（配置文件） provides defaults, thresholds, model defaults, loop limits, context limits, and feature toggles.
- Database provides agent-level runtime overrides: model selection, enabled capabilities, MCP tool bindings, RAG knowledge bases, system prompts, advisors, and user/project-specific configuration.
- Resolution priority: database agent config > yml defaults > Java fallback constants.
## 24. Confirmed ContextPlanner Position

`ContextPlannerNode`（上下文规划节点） is confirmed as a lightweight LLM node before `MainAgentNode`（主智能体节点）.

Flow position:

User input -> Runtime creates run -> Runtime loads state and database candidates -> Java candidate preselection -> ContextPlannerNode -> Runtime validates context plan and loads selected content -> MainAgentStateView -> MainAgentNode.

Boundary:

- Runtime（运行时） is deterministic Java orchestration. It queries data, preselects candidates, calls nodes, validates outputs, loads payloads, persists records, and controls lifecycle.
- ContextPlannerNode（上下文规划节点） is an LLM node. It judges semantic dependency between user input and candidate messages, memories, artifacts, evidence, and capabilities.
- ContextPlannerNode does not answer users, call tools, write database state, or control lifecycle.
- ContextPlannerInput is produced by Runtime and consumed by ContextPlannerNode.
- ContextPlannerOutput is produced by ContextPlannerNode and consumed by Runtime.
- MainAgentNode never calls ContextPlannerNode directly; MainAgentNode only receives the final Runtime-built StateView.
## 25. Confirmed Recovery and Repair Design

Recovery design is confirmed.

Runtime（运行时） has no LLM and does not invent natural-language repair instructions. It builds repair views through deterministic template filling.

Sources of recovery information:

- Java guards（Java 保护器） produce structured failure codes and fields, such as `INTERNAL_PROCESS_LEAK`, `FORMAT_VIOLATION`, `CONTRACT_ERROR`, `TOOL_NOT_CALLED`, and `CONTEXT_OVER_BUDGET`.
- LLM verifiers（大语言模型验收器） may produce structured semantic failure results and repair hints for cases such as RAG grounding and tool-result satisfaction.
- RecoveryTemplate（恢复模板） stores fixed repair instructions such as "only rewrite the final user-facing answer" and "do not mention agent, trace, verification, or internal process".

Runtime combines:

- original user request,
- invalid model output,
- guard failures,
- verifier hints,
- allowed repair scope,
- recovery template,

then calls the appropriate node for targeted repair.

Structural validation failure flow:

1. Strict JSON parse and schema validation.
2. Safe extraction only for low-risk formatting issues, such as markdown code fence removal or extracting the only JSON object.
3. Contract repair with validation errors if the output is still invalid.
4. Fail safely after retry limits.

Runtime must not infer missing semantic fields or convert natural language into actions by guessing. Recovery is bounded by counters such as max contract repair, max final repair, max tool retry, max RAG retry, max compression, and max loop.
## 26. Confirmed ExpectedOutcome and Capability-Based Verification

`expectedResult` is renamed conceptually to `expectedOutcome`（预期结果）.

Confirmed rule:

- `expectedOutcome` is task-level and verifier-readable.
- It must be structured enough for `ToolVerifier`（工具验收器） to understand what success means.
- It must not hard-code one specific MCP tool return shape into Java code.
- Tool-specific schemas, required fields, success signals, risk level, approval policy, and verifier rules come from `CapabilityRegistry`（能力注册表）.

Short-term source of capability configuration: yml（配置文件）.
Long-term source: MySQL-backed admin configuration.

Tool flow:

1. MainAgentNode emits `CALL_TOOL` with task-level `expectedOutcome`.
2. Runtime resolves capability configuration by `capabilityCode`.
3. Runtime builds ToolExecutionInput with both `expectedOutcome` and `capabilitySpec`.
4. ToolExecutionNode calls the bound MCP tool.
5. Runtime captures real receipt.
6. ToolVerifier validates receipt against expectedOutcome plus capabilitySpec.

This keeps the system extensible while preserving reliable verification.
## 27. Confirmed FinalResponseGuard MVP Scope

`FinalResponseGuard`（最终响应保护器） should be designed as an extensible pipeline, but MVP uses Java rule-based guards first.

MVP guard pipeline:

- EmptyAnswerGuard（空回答检查）
- InternalLeakGuard（内部信息泄漏检查）
- FormatGuard（格式检查）
- EvidenceReferenceGuard（证据引用检查）
- ToolClaimGuard（工具声明检查）
- LengthGuard（长度检查）

Later extensions:

- SafetyPolicyGuard（安全策略检查） with LLM or external moderation service.
- QualityGuard（质量检查） with LLM.
- Domain-specific compliance guards for sensitive scenarios.

All guards, rule-based or LLM-based, must output unified VerificationResult and must not directly modify final answers or control run lifecycle.
## 28. Confirmed Prompt and Contract Architecture Scope

Phase 5 prompt/contract architecture is accepted as a boundary and storage design, not yet the final prompt text or complete JSON schemas.

Confirmed now:

- Java owns harness protocol, contract schemas, parsers, state write scopes, recovery rules, and runtime state transitions.
- Database owns editable role, behavior, style, business, and domain prompt content.
- yml owns defaults and feature/runtime thresholds.
- Runtime assembles database prompts, Java contract envelopes, StateView, and output instructions for each node invocation.
- Final prompt text and complete field-level JSON schemas will be produced during final spec consolidation after state, persistence, API, error recovery, and testing designs are aligned.
## 29. Confirmed Frontend API and SSE Event Streaming

Phase 6 frontend/API direction is confirmed.

Normal frontend consumes only:

- chat messages（聊天消息）
- run status（运行状态）
- SSE user-visible events（服务器发送事件用户可见事件）
- final response（最终响应）
- pending input / ASK_USER options（等待用户输入/询问用户选项）
- artifact summaries and content（产物摘要和正文）

Debug data is isolated behind debug APIs and must not be used by normal UI.

Event streaming must use SSE emitter（服务器发送事件推送器） as the primary design, not polling. Polling endpoints may exist only as fallback or historical query APIs.

Frontend must not display raw node outputs, prompt text, verifier details, raw tool receipts, ContextPlanner output, ToolExecutionNode output, or trace payloads in normal mode.
## 30. Confirmed Verification and Testing Strategy

Phase 7 testing strategy is confirmed.

Principle:

- Minimize backend tests during development.
- Test only critical protocol, lifecycle, and safety boundaries.
- Provide frontend mock scenarios and SSE event streams so UI behavior can be verified without running full LLM/tool workflows.

Required backend tests:

- MainAgentActionContractTest（主智能体动作契约测试）
- RuntimeStateMachineTest（运行时状态机测试）
- FinalResponseGuardTest（最终响应保护测试）
- ToolVerificationTest（工具验收测试）
- ArtifactContextPolicyTest（产物上下文策略测试）

Avoid excessive tests for simple CRUD, DTO mapping, getters/setters, unstable prompt text, and low-risk wiring.

Frontend mock scenarios:

- simple_final（简单最终回答）
- rag_progress（RAG 检索进度）
- tool_publish_progress（工具发布进度）
- ask_user_confirm（用户确认）
- ask_user_choose_artifact（选择产物）
- artifact_created（产物生成）
- tool_failed（工具失败）
- final_guard_repair（最终回答修复）
- debug_trace（调试轨迹）

Mock endpoints should include SSE emitter event stream so frontend can test loading, timeline, waiting user choice, tool progress, final answer, and artifact panel behavior.
## 31. Pre-Spec Consistency Audit

Audit result before final spec consolidation:

1. Tool path correction.
   Older draft notes mentioned direct Spring AI automatic tool calling inside MainAgentNode or Runtime observing MainAgentNode tool calls. This is superseded. Confirmed final direction: MainAgentNode does not mount MCP tools. All tool use is `CALL_TOOL` -> Runtime validation/risk approval -> ToolExecutionNode with MCP tools -> Runtime receipt capture -> ToolVerifier -> evidence -> next MainAgentNode loop.

2. ContextPlanner boundary is consistent.
   Runtime is deterministic Java orchestration. ContextPlannerNode is a lightweight LLM node before MainAgentNode. Runtime produces ContextPlannerInput and consumes ContextPlannerOutput. MainAgentNode never calls ContextPlannerNode directly.

3. Lifecycle control is consistent.
   MainAgentNode emits semantic actions and StateDelta only. RuntimeStateMachine controls run lifecycle. Nodes cannot write runStatus, nextState, loopIndex, trace, audit, verifier result, or tool receipt facts.

4. Final answer boundary is consistent.
   Final answer can only come from FINAL or REPAIR_FINAL finalAnswerCandidate after FinalResponseGuard passes. Trace, verifier result, tool receipt, memory summary, and execution summary must never be assembled as final answer.

5. Persistence direction is consistent.
   Conversation, run, memory, artifact, evidence, tool, RAG, event/trace/audit, and payload are separate storage groups. LLM nodes do not access repositories directly. Runtime/managers/verifiers persist through domain repository interfaces.

6. Prompt/contract boundary is consistent.
   Java owns harness protocol, schema, parser, write scope, recovery, and state machine. Database owns editable prompt role/behavior/style/business content. yml owns defaults and thresholds.

7. Frontend boundary is consistent.
   Normal UI consumes messages, run status, SSE user-visible events, final response, pending input, and artifacts. Debug data is isolated. SSE emitter is required as primary event streaming design.

8. Testing direction is consistent.
   MVP testing is minimal and focuses on contract validation, runtime state machine, final guard, tool verification, artifact context policy, plus frontend mock/SSE scenarios.

Known item for final spec:

- AgentState/StateView/StateDelta have a direction but still need final field-level schema. This is not a contradiction; it is a pending detailed spec task.
- Earlier chronological notes remain as design history. Final spec must use the confirmed sections and this audit as the source of truth where older notes differ.

## 32. Section 5 Review Correction

User review identified three spec risks:

1. ContractValidator might be interpreted as a single generic validator instead of a node-specific contract pipeline.
2. ContextPlannerInput references might be selected but not clearly materialized into MainAgentStateView.
3. Node prompts were too role-level and could lack enough operating context for reliable LLM behavior.

Confirmed correction:

- Every LLM node invocation must go through NodeInvocationPipeline.
- ContractRegistry maps each node code and contract version to a node-specific contract.
- Prompt assembly must use layered prompt envelopes: role prompt, operating context, input field guide, task procedure, decision policy, output contract, examples, anti-examples, and output-only instruction.
- ContextPlannerOutput is not passed directly to MainAgentNode. Runtime validates refs, loads allowed summaries/snippets/full payloads/evidence summaries, applies budget policy, and only then builds MainAgentStateView.
- MainAgentNode and ToolExecutionNode prompts must include operational procedure, input field guide, decision policy, positive examples, and anti-examples.
- Chinese review sample Section 5 must stay synchronized with the canonical English spec Section 5.

## 33. Confirmed Testing Strategy Spec

Section 12 of the canonical English spec is filled.

Confirmed testing direction:

- Tests must be minimal but real.
- Do not test trivial DTO/getter/setter behavior, unstable prompt wording, or broad DAO CRUD unrelated to harness behavior.
- Required backend tests focus on contracts, Runtime state machine, context materialization, artifact context policy, tool execution and verification, RAG execution and verification, final response guard, and repository/API boundaries.
- API and SSE tests must prove normal frontend only receives user-visible messages/events and never raw node output, prompt, trace, verifier details, or raw receipts.
- Frontend mock scenarios are required for simple final, artifact creation, RAG progress, tool publish progress, ASK_USER confirmation/choice, tool failure, final guard repair, context over budget, and debug trace.
- Deterministic fixtures must avoid real credentials and external endpoints.
- MVP completion should run the app module test command when feasible; if full tests are slow or environment-dependent, targeted tests plus documented skipped tests are acceptable.

## 34. Completed Final Spec Tail

The canonical English spec now includes sections 13 and 14.

Section 13 Implementation Plan is organized as staged implementation phases:

1. Spec lock and scaffolding.
2. Domain model and contract layer.
3. Persistence and repository adapters.
4. Prompt assembly and node invocation pipeline.
5. Context and artifact runtime.
6. Runtime state machine.
7. MainAgentNode action handlers.
8. RAG runtime and verification.
9. Tool runtime, MCP execution, and verification.
10. FinalResponseGuard and repair.
11. API, SSE, and frontend mock mode.
12. Old harness isolation and cleanup.
13. MVP verification and review.

Section 14 Backlog includes:

- context planning enhancements
- subagent scheduling
- coding agent capability
- skill/capability marketplace
- advanced guardrails
- prompt/contract management UI
- observability/debug UI
- distributed runtime and reliability
- RAG enhancements
- tool capability enhancements
- frontend enhancements
- test/evaluation enhancements
- migration backlog
- explicit non-goals

Chinese review sample now includes detailed sections 12-14 corresponding to the completed English tail. Earlier Chinese sections 7-11 remain summarized by prior user acceptance unless the user requests full translation later.

## 35. First Spec Review Correction Pass

After a full consistency review, the user approved a correction set and the canonical English spec was updated.

Confirmed corrections:

1. MVP ToolVerifier is Java-only execution proof verification. It proves real tool invocation, allowed/bound tool, approval, receipt existence, and call-level error state. It does not prove full business completion.
2. Complex tool business verification, per-tool success signals, required result schemas, optional LLM semantic tool verification, and external result checks are backlog work.
3. All user interruptions use `agent_pending_input`. This includes clarification, context selection, tool approval, user-side tool action, and verifier/guard clarification.
4. `WAITING_USER` pauses the same run. User cancellation, rejection, expiration, or no answer ends the current run as `CANCELLED` or user-safe `FAILED`.
5. Pending input includes structured options, `answerContract`, and `continuation`. Exact option selection uses stored `option.value`; free-form text goes to `UserInputResolverNode`.
6. Added `UserInputResolverNode`, `UserInputResolverInput`, and `UserInputResolution`.
7. ContextPlanner non-ready statuses are handled by Runtime through `ContextPlannerStatus Handling`, not graph-style node routing.
8. Artifact modification tasks may load `FULL_TEXT` into MainAgentNode when budget allows; over-budget tasks use chunking/compression or ask the user.
9. RagVerifier is an LLM verifier node for grounding honesty. It does not force use of irrelevant RAG evidence.
10. Any `finalAnswerCandidate`, including artifact create/update acknowledgements, must pass FinalResponseGuard before becoming a chat final answer.
11. Real SSE and mock SSE now use one `UserVisibleEvent` DTO with `phase`, `title`, `summary`, `artifactRefs`, and `pendingInputId`.
12. Added `agent_pending_input` and `agent_node_prompt` persistence tables.
13. CapabilityRegistry manages external tool capability metadata only in MVP. RAG is internal `RagRuntime` configured under `auto-agent.rag`.
14. English examples were corrected to avoid mojibake.

Chinese review sample first received targeted conflict fixes and a new section 15 summarizing these corrections. It was then updated again so the main review chapters also reflect the correction set:

- Section 3 now includes `ContextPlannerStatus Handling` and `Pending Input And User Reply Handling`.
- Section 4 now includes `UserInputResolverInput`.
- Section 5 now includes `UserInputResolverNode Prompt` and `UserInputResolution Contract`.
- Chinese action routing now matches the canonical rule that RAG-used final answers run `RagVerifier` before `FinalResponseGuard`, and artifact action `finalAnswerCandidate` must pass the same final guard path.
- ToolExecutionNode wording now treats `expectedOutcome` as intent context rather than a strong business-completion contract.
