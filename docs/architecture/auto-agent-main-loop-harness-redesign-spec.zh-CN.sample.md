# AutoAgent 主循环 Harness 重设计规格说明（中文审查样本）

状态：草稿规格说明，审查通过后以英文正式版为准。

英文正式版：`docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`

历史草稿：`docs/superpowers/specs/2026-04-28-auto-agent-main-loop-harness-working-notes.md`

说明：这份中文文件只用于人工审查口径。后续长期维护和代码实现以英文正式 spec 为唯一参考。


## 本轮同步修正摘要

本中文审查版已按英文正式 spec 同步以下关键变更：

- Tool（工具）执行不再使用额外的 LLM 节点。`MainAgentNode` 只输出 `CALL_TOOL`，`Runtime` 通过 `ToolRuntime`、`McpClientRegistry` 和 `McpToolRegistry` 调用 Spring AI MCP Client（MCP 客户端）。
- 每个 MCP 服务可以有独立的 SSE 或 stdio MCP client。系统不手写每个外部工具，只实现通用 MCP 调用、权限、参数校验、回执和证据管线。
- 新增 typed transcript（类型化运行记录），用于 run replay（运行重放）、resume（恢复）和 compaction（上下文压缩）安全。它和普通聊天消息、debug trace（调试轨迹）、audit（审计）分离。
- MainAgentNode prompt（主节点提示词）已补强为硬约束：单轮只输出一个 action；不可信内容不能覆盖系统规则；不能编造工具/RAG/测试/发布结果；高风险外部动作必须审批；最终回答不能泄露 Runtime、node、trace、verifier、contract、prompt 等内部词。
- RAG（检索增强生成）继续作为内置核心能力保留，使用 `RETRIEVE_RAG`，不降级为普通外部 MCP tool。

## 最新细节修正摘要

本中文查阅版同步英文正式 spec 的最新四项修正。后续代码实现仍必须以英文正式 spec 为唯一施工蓝图：

- `agent_tool_call` 不再使用旧工具节点执行字段，改为 `tool_invocation_id`，并补充 `mcp_server_code`、`transport`、`input_schema_ref`、权限、审批、workspace（工作区）和 destructive（破坏性操作）字段。
- 工具权限模型补齐为 `PermissionMode`、`RequiredPermission`、`ApprovalPolicy` 和 `PermissionDecision`。权限决策只能由 Java Runtime / `PermissionEnforcer` 做确定性判断，不能交给 prompt 自行发挥。
- `ToolArgumentMaterializer` 的引用格式统一为 `contentSource` / `evidenceSource`。工具参数中不再混用 `contentRef` 作为正文引用格式。
- Phase 1 需要实现的 value object（值对象）和 enum（枚举）补齐，包括 `RunTranscriptBlock`、`PermissionDecision`、`ToolArgumentSource`、`TranscriptBlockType`、`PermissionMode`、`RequiredPermission`、`ApprovalPolicy`、`McpTransportType`、`ToolArgumentSourceType` 等。

注意：中文文件是人工查阅版，不是代码实现依据。若中文内容和英文正式 spec 存在差异，以英文正式 spec 为准。
## 0. Spec 治理规则

### 0.1 权威性

本 spec 是 AutoAgent harness（运行框架）重设计的事实源。

审查通过后：

- 任何影响 AutoAgent 运行流程、节点契约、提示词组装、工具执行、RAG、记忆、产物、证据、前端事件流、持久化的代码修改，都必须符合本 spec。
- 历史 working notes（工作草稿）只解释设计来源，不再作为实现依据。
- 如果代码、数据库提示词、yml 配置或旧文档与本 spec 冲突，以本 spec 为准。

### 0.2 语言和读者

英文正式 spec 面向 Codex 后续实现工作。

中文版本只作为临时审查样本，不作为长期实现参考。

### 0.3 MVP 范围

MVP（最小可行版本）必须实现：

- Java Runtime（运行时）确定性编排。
- `ContextPlannerNode`（上下文规划节点）在主节点前选择上下文。
- `MainAgentNode`（主智能体节点）负责动作决策和最终回答生成。
- `ToolRuntime`（工具运行时）通过 Spring AI MCP Client（MCP 客户端）确定性执行 `CALL_TOOL`。
- 显式 RAG 检索动作。
- 产物持久化和产物解析。
- 基于证据的工具和 RAG 验收。
- Java 规则型最终响应保护流水线。
- 基于 SSE emitter（服务器发送事件推送器）的用户可见事件流。
- 隔离的 debug（调试）接口。
- 最小必要后端测试和前端 mock/SSE 场景。

### 0.4 不属于 MVP 的内容

以下是 backlog（后续待办），不能混进 MVP 要求：

- 子 Agent 调度。
- 完整代码智能体能力。
- LLM 安全、策略、质量护栏扩展。
- 动态能力和提示词后台管理 UI。
- 用数据库完全替代所有 yml 能力默认配置。
- 高级项目级代码上下文规划。

### 0.5 硬性规则

- `Runtime`（运行时）是 Java 确定性编排器，不带 LLM。
- `MainAgentNode` 不直接挂 MCP 工具。
- 所有工具调用都必须走 `CALL_TOOL` 和 `ToolRuntime`。
- 节点输出必须使用 Java 拥有的契约。
- JSON schema、解析规则、动作枚举、状态写入范围、恢复策略和状态机必须放在 Java 中。
- 数据库提示词只能定义角色、行为、风格、业务指导，不能定义运行协议。
- 普通前端不能展示节点原始输出、提示词、验收详情、原始工具回执、轨迹载荷或内部状态。
- 最终用户回答只能来自经过保护器校验的 `FinalResponse`。

## 1. 问题定义

### 1.1 当前 Harness 问题

当前固定多节点 harness 存在结构问题：

- 节点职责不清，协作不稳定。
- 中间思考、验收摘要、运行过程文本可能泄漏到最终回答。
- 节点之间传递内容太重，容易上下文超限。
- 验收器可能因为格式问题误判已经完成的任务。
- 前端可能展示 JSON 形式的内部节点数据，而不是用户可见进度。
- DynamicContext（动态上下文）过于松散，容易成为巨大非结构化对象。
- 旧设计很难扩展为支持工具、RAG、产物、用户确认和未来代码能力的通用智能体。

### 1.2 必须修复的行为

新 harness 必须防止：

- 最终回答出现“根据已验收成果”或描述节点执行过程。
- 工具没有真实回执却被认为成功。
- 使用 RAG 时，回答没有绑定检索证据。
- 长产物反复塞进聊天历史，而不是通过 artifact identity（产物身份）引用。
- “这篇文章”“上一版”“第二个选项”等模糊指代被盲猜。
- 高风险工具动作没有用户审批。
- 普通前端展示内部调试内容。

### 1.3 设计目标

用主循环架构替换固定 Node1-4 链：

- Runtime 控制生命周期和状态流转。
- ContextPlanner 选择主节点要看的上下文。
- MainAgentNode 决定下一步语义动作。
- Runtime 执行或委派动作。
- Verifier 和 Guard 验收事实和最终输出。
- 前端只消费干净事件和最终响应。

## 2. 目标架构

### 2.1 主要组件

| 组件 | 类型 | 职责 |
|---|---|---|
| `Runtime` | Java 服务 | 控制运行生命周期、循环状态、持久化、节点调用、动作处理、恢复和事件发送。 |
| `ContextPlannerNode` | LLM 节点 | 选择消息、记忆、产物、证据和内容粒度。 |
| `MainAgentNode` | LLM 节点 | 读取主节点状态视图并输出一个结构化语义动作。 |
| `ToolRuntime` | Java 确定性服务 | 解析工具能力、校验参数、执行权限策略、通过 Spring AI MCP Client 调用真实 MCP 工具、持久化工具回执并发送进度事件。 |
| `RagRuntime` | Java 服务 | 执行 RAG 检索并记录 RAG 证据。 |
| `UserInputResolverNode` | LLM 节点 | 按 pending input 的回答契约解析用户自由文本回复。 |
| `ToolVerifier` | Java 验收器 | 只验收真实工具调用证明、审批、回执存在和基础调用错误状态。 |
| `RagVerifier` | 偏 LLM 验收器 | 验证 RAG 回答是否基于证据。 |
| `FinalResponseGuard` | Java 保护器流水线 | 阻止最终回答泄漏内部信息、格式错误、工具伪成功等。 |
| `MemoryManager` | Java 服务 | 维护会话摘要、长期记忆和记忆事件。 |
| `ArtifactManager` | Java 服务 | 保存可复用产物、版本、别名和载荷引用。 |
| `EvidenceManager` | Java 服务 | 记录 RAG、工具、记忆、产物和用户确认事实。 |
| `RunEventPublisher` | Java 服务 | 通过 SSE emitter 推送用户可见事件并保存事件历史。 |

### 2.2 LLM 节点边界

MVP 中允许使用 LLM 的组件：

- `ContextPlannerNode`
- `MainAgentNode`
- `ToolRuntime`
- `UserInputResolverNode`
- 需要语义 grounding（证据支撑）判断时的 `RagVerifier`
- 通过恢复策略路由的契约修复和最终回答修复调用

不允许内部使用 LLM 的组件：

- `Runtime`
- `RuntimeStateMachine`
- `ContractValidator`
- MVP 版 `FinalResponseGuard`
- `ContextBudgetManager`
- `ArtifactManager`
- `MemoryManager`
- repository 实现
- 前端 controller

### 2.3 Runtime 流程

标准 run 流程：

```text
用户输入
  -> Runtime 创建 run 和用户消息
  -> Runtime 加载当前状态和数据库候选
  -> Java 候选预筛
  -> ContextPlannerNode 选择必要上下文
  -> Runtime 校验上下文计划并构造 MainAgentStateView
  -> MainAgentNode 输出一个 action
  -> Runtime 校验 action 和 StateDelta
  -> Runtime 处理 action
  -> Runtime 运行必要 verifier 或 guard
  -> Runtime 继续循环、等待用户、完成或失败
```

### 2.4 工具流程

标准工具流程：

```text
MainAgentNode 输出 CALL_TOOL
  -> Runtime 校验 toolIntent
  -> Runtime 解析能力配置
  -> Runtime 检查风险和用户审批
  -> Runtime 加载所需产物正文或证据摘要
  -> Runtime 构造 ToolInvocationRequest
  -> ToolRuntime 通过 Spring AI MCP Client 调用真实 MCP 工具
  -> Runtime 捕获真实 ToolReceipt
  -> ToolVerifier 验收真实工具调用证明和基础回执状态
  -> Runtime 记录 ToolEvidence
  -> 下一轮 MainAgentNode 基于证据回答
```

`ToolRuntime` 不能生成最终用户回答。最终回答必须回到 `MainAgentNode` 和 `FinalResponseGuard`。

### 2.5 RAG 流程

标准 RAG 流程：

```text
MainAgentNode 输出 RETRIEVE_RAG
  -> Runtime 校验 ragRequest
  -> RagRuntime 执行检索
  -> Runtime 保存 RAG query、hits 和 evidence
  -> 本 run 使用过 RAG 时，最终回答前由 RagVerifier 验证 grounding honesty（证据诚实性）
  -> 下一轮 MainAgentNode 基于 RAG evidence 回答
```

### 2.6 数据归属

LLM 节点不直接读写数据库。

Runtime 和领域管理器负责构造节点视图并持久化节点输出：

- `ContextPlannerNode` 接收 `ContextPlannerInput`，返回 `ContextPlannerOutput`。
- `MainAgentNode` 接收 `MainAgentStateView`，返回 `MainAgentAction`。
- `ToolRuntime` 接收 `ToolInvocationRequest`，返回 `ToolInvocationResult`。
- Runtime 负责校验输出、应用状态变更、保存载荷并发送事件。

### 2.7 最终回答归属

唯一合法最终回答路径：

```text
MainAgentNode FINAL 或 REPAIR_FINAL
  -> finalAnswerCandidate
  -> FinalResponseGuard
  -> FinalResponse
  -> assistant message
  -> frontend
```

trace、原始输出、验收摘要、工具回执、记忆摘要、运行状态、debug 载荷都不能被拼成最终回答。

## 3. Runtime 状态机

### 3.1 Runtime 术语

`session`（会话）表示用户可见的一段聊天。

`message`（消息）表示会话中的一条用户或助手可见消息。

`run`（运行实例）表示后端为了处理一次用户请求创建的一次执行过程。run 从 Runtime 接收用户消息开始，到完成、失败、取消或等待用户输入为止。

一个 session 包含多条 message。一个用户 message 通常创建一个 run。一个 run 可以产生多个事件、轨迹、证据、工具调用、RAG 查询、产物，以及一个最终助手消息。

### 3.2 Run 状态

`RunStatus` 必须包括：

| 状态 | 含义 |
|---|---|
| `CREATED` | run 记录已创建，但尚未开始执行。 |
| `RUNNING` | Runtime 正在执行。 |
| `WAITING_USER` | Runtime 暂停，等待用户输入或审批。 |
| `COMPLETED` | run 已通过最终响应保护并完成。 |
| `FAILED` | run 以用户安全失败信息结束。 |
| `CANCELLED` | run 被用户或系统取消。 |

### 3.3 Runtime 阶段

`RuntimePhase` 必须包括：

| 阶段 | 含义 |
|---|---|
| `CREATED` | 创建 run 和用户消息。 |
| `PREPARING_CONTEXT` | Runtime 加载候选和当前状态。 |
| `PLANNING_CONTEXT` | 必要时调用 ContextPlannerNode。 |
| `BUILDING_STATE_VIEW` | 构建 MainAgentStateView。 |
| `CALLING_MAIN_NODE` | 调用 MainAgentNode。 |
| `VALIDATING_ACTION` | 解析并校验 MainAgentAction。 |
| `HANDLING_ACTION` | 将 action 路由给对应处理器。 |
| `EXECUTING_RAG` | 执行 RAG 检索。 |
| `PREPARING_TOOL` | 校验工具意图、解析能力、审批和载荷。 |
| `INVOKING_TOOL_RUNTIME` | 调用 ToolRuntime。 |
| `VERIFYING_TOOL` | 验收真实工具回执。 |
| `VERIFYING_RAG` | 必要时验收 RAG grounding。 |
| `VERIFYING_FINAL` | 运行最终响应保护流水线。 |
| `REPAIRING_CONTRACT` | 执行有界契约修复。 |
| `REPAIRING_FINAL` | 执行有界最终回答修复。 |
| `RESOLVING_USER_INPUT` | 当用户自由输入无法精确匹配 option 时，Runtime 调用 UserInputResolverNode。 |
| `WAITING_USER` | 保存 pending input 并暂停执行。 |
| `COMPLETED` | 保存最终响应和助手消息。 |
| `FAILED` | 保存失败和用户安全错误。 |
| `CANCELLED` | 保存取消状态。 |

### 3.4 标准循环

Runtime 必须作为确定性 Java 编排器执行循环：

```text
创建 run
追加用户消息
发送 RECEIVED 事件
while run is RUNNING:
  准备上下文
  必要时调用 ContextPlannerNode
  构建 MainAgentStateView
  调用 MainAgentNode
  解析并校验 MainAgentAction
  处理 action
  运行必要 verifier 或 guard
  继续、等待、完成、失败或取消
```

Runtime 控制生命周期。节点不控制生命周期。

### 3.5 Main Action 路由

Runtime 必须按 action 路由：

| Action | Runtime 处理 |
|---|---|
| `FINAL` | 如果本 run 使用过 RAG，先运行 RagVerifier；再运行 FinalResponseGuard。通过则保存最终响应和助手消息，完成 run；失败则在预算允许时进入 REPAIRING_FINAL。 |
| `CREATE_ARTIFACT` | 保存产物元信息和载荷，记录产物证据。如果带有 `finalAnswerCandidate`，必须走同一条最终保护路径后才能返回；否则继续循环或使用固定受保护模板。 |
| `UPDATE_ARTIFACT` | 校验目标产物，保存新版本或子产物，记录关系和证据。如果带有 `finalAnswerCandidate`，必须走同一条最终保护路径后才能返回；否则继续循环或使用固定受保护模板。 |
| `RETRIEVE_RAG` | 执行 RAG 检索，保存 query、hits 和 evidence，下一轮带证据摘要继续。 |
| `CALL_TOOL` | 校验工具意图，解析能力配置，必要时审批，调用 ToolRuntime，捕获回执，验收工具结果，记录工具证据。 |
| `ASK_USER` | 保存等待输入请求，发送用户可见选项，设置 run 为 WAITING_USER。 |
| `PLAN` | 内部保存计划并继续循环，计划不能成为最终回答。 |
| `CONTINUE` | 仅在循环预算允许时继续，必须防止空转。 |
| `REPAIR_FINAL` | 只在最终回答修复阶段有效，校验修复后的候选答案并再次运行 FinalResponseGuard。 |
| `FAIL` | 保存失败详情，返回用户安全失败信息。 |

### 3.6 ContextPlannerStatus Handling（上下文规划状态处理）

`ContextPlannerNode` 不控制图路由。它只输出上下文规划状态，Runtime 按确定性规则消费该状态：

| ContextPlannerStatus | Runtime 处理 |
|---|---|
| `READY` | 按选择结果实体化上下文，构建 `MainAgentStateView`。 |
| `NO_RELEVANT_CONTEXT` | 构建最小 `MainAgentStateView`，不强行注入无关记忆、产物或 RAG 证据。 |
| `NEEDS_USER_CLARIFICATION` | 创建 `agent_pending_input`，保存结构化 options、`answerContract` 和 `continuation`，发送 `ASKING_USER` 事件，将 run 置为 `WAITING_USER` 并停止当前循环。 |
| `CONTEXT_OVER_BUDGET` | 先在 `maxContextCompression` 预算内压缩或分块；仍超预算时创建 `agent_pending_input`，要求用户缩小范围；否则实体化上下文并继续。 |
| `ERROR` | 进入契约恢复；恢复耗尽后安全失败。 |

当用户任务需要修改、重写、审查、对比或深入处理既有短产物时，ContextPlannerNode 必须优先选择 `FULL_TEXT`。如果全文超过预算，Runtime 使用摘要、分块和用户澄清组合处理。

### 3.7 Pending Input 和用户回答处理

所有需要等待用户的情况都必须统一落 `agent_pending_input`：

```text
Runtime creates pending input
persist agent_pending_input
emit UserVisibleEvent
set runStatus = WAITING_USER
stop loop

User submits reply
  -> if cancelled: mark pending CANCELLED, mark run CANCELLED or FAILED safely
  -> if optionId exact match: use option.value
  -> otherwise invoke UserInputResolverNode with pending input, answerContract, options, and free text
  -> validate UserInputResolution
  -> call continuation handler
  -> set runStatus = RUNNING
  -> resume from continuation phase
```

`pending input`（挂起输入）不能靠“重新创建一个新 run”恢复。它必须恢复同一个 run 和同一个 continuation（继续点）。如果用户取消、超时或无法解析，Runtime 必须安全结束当前 run，不能卡死。

自由文本必须在无法精确匹配 `optionId` 时交给 `UserInputResolverNode` 解析。

### 3.8 工具子流程

`CALL_TOOL` 必须使用：

```text
VALIDATING_ACTION
  -> PREPARING_TOOL
  -> 审批检查
  -> 如果缺少审批则 WAITING_USER
  -> INVOKING_TOOL_RUNTIME
  -> 捕获真实 ToolReceipt
  -> VERIFYING_TOOL
  -> 写 ToolEvidence
  -> PREPARING_CONTEXT 进入下一轮
```

MainAgentNode 不能直接挂 MCP 工具。MVP 中没有 LLM 节点负责挂载并执行 MCP 工具；ToolRuntime 通过 `McpClientRegistry` 调用 MCP 工具。

### 3.9 恢复上限

Runtime 必须按 run 维护恢复计数：

| 计数器 | MVP 默认值 |
|---|---:|
| `maxLoop` | 6 |
| `maxContractRepair` | 1 |
| `maxFinalRepair` | 2 |
| `maxToolRetry` | 1 |
| `maxRagRetry` | 2 |
| `maxContextCompression` | 2 |

当计数耗尽，Runtime 必须停止重试该路径，并询问用户、安全失败，或只在真实且通过保护时返回部分结果。

### 3.10 禁止节点写入生命周期字段

LLM 节点不能输出或修改：

- `runStatus`
- `nextState`
- `runtimePhase`
- `loopIndex`
- `maxLoop`
- `toolReceipt`
- `verifierResult`
- `developerTrace`
- `auditRecord`

如果这些字段出现在节点输出中，ContractValidator 必须拒绝。

## 4. AgentState、StateView 和 StateDelta

### 4.1 目的

harness 必须区分后端全量状态、LLM 可见状态、节点写回数据：

| 结构 | 目的 |
|---|---|
| `AgentState` | 某次 run 和相关 session 的后端全量事实账本，不直接发送给 LLM。 |
| `StateView` | 给某一次 LLM 节点调用的最小必要视图。 |
| `StateDelta` | 节点输出的结构化写回请求，由 Runtime 校验并应用。 |

### 4.2 AgentState 区域

`AgentState` 是持久化记录和运行中状态的聚合视图，包含：

| 区域 | 内容 |
|---|---|
| `RunMeta` | run id、session id、user id、agent id、状态、阶段、循环轮次、限制、时间戳。 |
| `UserRequest` | 当前用户消息 id、内容、输入类型、规范化目标。 |
| `ConversationContext` | 最近消息、会话摘要、主题摘要。 |
| `MemoryState` | 召回记忆引用、长期记忆候选、记忆更新候选。 |
| `ArtifactState` | 产物候选、已解析产物、活跃产物引用、新建和更新产物。 |
| `EvidenceState` | RAG、工具、记忆、产物、用户确认等证据。 |
| `ActionState` | 上次 action、action 历史、待处理 action、等待用户输入。 |
| `ToolState` | 工具意图、工具调用、工具回执、工具验收结果、审批状态。 |
| `RagState` | RAG 请求、查询、命中、验收结果。 |
| `PlanState` | 当前计划、当前步骤、已完成步骤、阻塞步骤。 |
| `FinalState` | 最终回答候选、最终响应、最终保护结果、引用、后续选项。 |
| `TraceState` | 用户可见事件、开发者轨迹、审计摘要、token 用量、错误。 |
| `RecoveryState` | 恢复计数、最近错误码、错误历史。 |

### 4.3 StateView 类型

MVP 必须定义：

| View | 消费者 |
|---|---|
| `ContextPlannerInput` | `ContextPlannerNode` |
| `MainAgentStateView` | `MainAgentNode` |
| `ToolInvocationRequest` | `ToolRuntime` |
| `UserInputResolverInput` | `UserInputResolverNode` |
| `RepairStateView` | 指定节点的修复调用 |
| `VerifierInput` | `ToolVerifier` 或 `RagVerifier` |

### 4.4 ContextPlannerInput

`ContextPlannerInput` 只包含压缩候选：

```json
{
  "runMeta": {
    "runId": "run_001",
    "sessionId": "sess_001",
    "loopIndex": 1
  },
  "userInput": {
    "messageId": "msg_001",
    "content": "把这个 RAG 八股文发布到 CSDN",
    "inputType": "TEXT"
  },
  "recentMessages": [],
  "sessionSummaries": [],
  "artifactCandidates": [],
  "memoryCandidates": [],
  "pendingAction": null,
  "availableCapabilities": [],
  "tokenBudget": {
    "maxStateViewTokens": 12000,
    "reservedOutputTokens": 2000,
    "currentCandidateTokens": 0
  }
}
```

它不能包含产物全文、原始工具回执、完整 trace、完整 prompt、模型原始输出。

### 4.5 MainAgentStateView

`MainAgentStateView` 只包含被选择且通过预算检查的上下文：

```json
{
  "runMeta": {
    "runId": "run_001",
    "sessionId": "sess_001",
    "loopIndex": 1
  },
  "userInput": {
    "messageId": "msg_001",
    "content": "把这个 RAG 八股文发布到 CSDN"
  },
  "conversation": {
    "recentMessages": [],
    "selectedSummaries": []
  },
  "memoryPack": [],
  "resolvedArtifacts": [],
  "artifactContent": [],
  "evidencePack": [],
  "availableCapabilities": [],
  "pendingAction": null,
  "currentPlan": null,
  "lastVerifierFeedback": [],
  "outputContractVersion": "main-agent-action-v1"
}
```

### 4.6 ToolInvocationRequest

`ToolInvocationRequest` 由 Runtime 在 `CALL_TOOL` 校验后构造：

```json
{
  "runMeta": {
    "runId": "run_001",
    "sessionId": "sess_001",
    "loopIndex": 2,
    "toolNodeRunId": "tool_run_001"
  },
  "toolIntent": {},
  "expectedOutcome": {},
  "capabilitySpec": {},
  "artifacts": [],
  "evidence": [],
  "mcpTool": {
    "serverCode": "csdn",
    "toolName": "publish_article",
    "transport": "SSE",
    "inputSchemaRef": "payload_schema_001"
  },
  "constraints": {
    "mustCallRealTool": true,
    "mustUseOneOfAvailableTools": true,
    "doNotAnswerUser": true,
    "doNotInventResult": true,
    "maxToolCalls": 1
  }
}
```

### 4.7 UserInputResolverInput

`UserInputResolverInput` 只在 pending input 收到自由文本且无法精确匹配 `optionId` 时由 Runtime 构建。

```json
{
  "runMeta": {
    "runId": "run_001",
    "sessionId": "sess_001",
    "pendingId": "pending_001"
  },
  "pendingInput": {
    "pendingType": "CONTEXT_CLARIFICATION",
    "question": "你要发布哪一篇文章？",
    "options": [
      {
        "optionId": "opt_latest",
        "label": "最新的一篇",
        "value": {
          "artifactId": "art_001",
          "contextLevel": "METADATA_ONLY"
        }
      }
    ],
    "allowFreeText": true
  },
  "answerContract": {
    "expectedShape": "CONTEXT_SELECTION",
    "requiredFields": ["artifactId", "contextLevel"],
    "allowedValues": {
      "contextLevel": ["METADATA_ONLY", "SUMMARY_ONLY", "SUMMARY_PLUS_SNIPPET", "FULL_TEXT", "CHUNKED_CONTEXT"]
    }
  },
  "userReply": {
    "rawText": "就发最新那篇",
    "submittedOptionId": null
  }
}
```

`UserInputResolverNode` 不能回答用户、调用工具、加载上下文或控制生命周期。它只把用户回复归一化为 `UserInputResolution`。

### 4.8 StateDelta 写入范围

每个 action 都有严格允许的 `StateDelta`：

| Action | 允许字段 |
|---|---|
| `FINAL` | `finalAnswerCandidate` |
| `CREATE_ARTIFACT` | `artifactDraft`，可选 `finalAnswerCandidate` |
| `UPDATE_ARTIFACT` | `artifactPatch`，可选 `finalAnswerCandidate` |
| `RETRIEVE_RAG` | `ragRequest` |
| `CALL_TOOL` | `toolIntent` |
| `ASK_USER` | `askUserRequest` |
| `PLAN` | `planDraft` |
| `CONTINUE` | `nextActionHint` |
| `REPAIR_FINAL` | `finalAnswerCandidate` |
| `FAIL` | `failure` |

如果节点输出了当前 action 不允许的字段，Runtime 必须拒绝。

### 4.9 StateDelta 不是 State

节点只提交想要的变更，Runtime 才真正应用变更。

例如：

- MainAgentNode 输出 `artifactDraft`。
- Runtime 保存产物元信息和正文载荷。
- Runtime 记录产物证据。
- Runtime 发送用户可见事件。

节点不能直接写产物表、证据表、事件、轨迹或 run 状态。

## 5. 节点契约和提示词

### 5.1 契约归属

Java 拥有所有节点契约。

数据库提示词可以描述节点角色、行为、风格和业务指导，但不能定义 JSON schema（结构定义）、解析规则、action 路由、状态写入范围、生命周期流转或恢复限制。

每次节点调用由 Runtime 组装：

```text
数据库角色/行为提示词
+ Java 固定运行规则
+ Java 契约包
+ 当前 StateView
+ Java 只输出 JSON 指令
```

所有 LLM 节点必须只输出一个 JSON 对象，不能输出 markdown、解释文本或代码块。

### 5.2 NodeInvocationPipeline（节点调用流水线）

每一个 LLM 节点调用都必须经过同一条调用流水线：

```text
Runtime 构建 StateView
  -> PromptAssembler 构建分层 prompt envelope
  -> ChatClient 调用模型
  -> RawOutputParser 提取 JSON
  -> ContractRegistry 解析节点契约
  -> ContractValidator 校验输出
  -> ContractRepairPolicy 在允许时执行修复
  -> Runtime 接收已经校验通过的类型化输出
```

任何 LLM 输出都不能绕过这条流水线。

### 5.3 ContractRegistry（契约注册表）

`ContractRegistry` 是 Java 注册表，用来把 node code（节点编码）和 contract version（契约版本）映射到预期契约。

必须包含的映射：

| Component Code | Contract |
|---|---|
| `CONTEXT_PLANNER` | `ContextPlannerOutputContract` |
| `MAIN_AGENT` | `MainAgentActionContract` |
| `TOOL_RUNTIME` | `ToolInvocationResultContract`，用于 Java-only 组件的结构化校验 |
| `USER_INPUT_RESOLVER` | `UserInputResolutionContract` |
| `RAG_VERIFIER` | `VerificationResultContract` |
| `TOOL_VERIFIER` | `VerificationResultContract` |
| `FINAL_REPAIR` | 限制为 `REPAIR_FINAL` 的 `MainAgentActionContract` |
| `CONTRACT_REPAIR` | 正在被修复的原始节点契约 |

契约不能散落在各个节点或 prompt 文本里。Runtime 必须通过 `ContractRegistry` 解析它们。

### 5.4 Layered Prompt Envelope（分层提示词外壳）

节点 prompt 必须组装为分层 prompt envelope。这样既能让 prompt 足够深入，让 LLM 行为稳定，又能保持可维护。

分层如下：

| Layer | Owner | Purpose |
|---|---|---|
| `RolePrompt` | Database | 可编辑的节点角色和高层职责。 |
| `OperatingContext` | Java | 解释节点位于 Runtime 的哪个位置，以及输出会被谁消费。 |
| `InputFieldGuide` | Java | 解释每个 StateView 字段和引用语义。 |
| `TaskProcedure` | Java | 节点执行任务的分步骤流程。 |
| `DecisionPolicy` | Java | 选择 action 或上下文粒度的规则。 |
| `OutputContract` | Java | JSON schema、枚举、允许字段、禁止字段。 |
| `FewShotExamples` | Java 或版本化资源 | 困难决策的正例。 |
| `AntiExamples` | Java 或版本化资源 | 节点必须避免的反例。 |
| `OutputOnlyInstruction` | Java | 最后一层只输出 JSON 指令。 |

数据库 prompt 文本故意不能单独承担全部工作。Java 管理的 prompt envelope 层提供可靠节点行为所需的运行语境和契约深度。

### 5.5 通用节点输出规则

所有 LLM 节点输出必须满足：

- 合法 JSON 对象
- 没有 markdown 包裹
- 没有隐藏推理
- 没有思维链
- 没有生命周期字段
- 没有原始工具回执
- 没有开发者轨迹
- 没有审计字段

如果解析或校验失败，Runtime 按第 6 章执行有界修复。

### 5.6 ContextPlannerNode 提示词

数据库角色提示词：

```text
你是 ContextPlannerNode。你的唯一职责是决定哪些候选上下文应该加载给下一次 MainAgentNode 调用。
你不回答用户，不调用工具，不创建产物，不控制运行生命周期。
```

Java 固定规则：

```text
只能使用 ContextPlannerInput 提供的候选。
优先选择紧凑上下文。只有当用户任务需要阅读或改写内容时，才选择产物全文。
如果用户指代模糊且多个候选都可能成立，请请求用户澄清。
只输出 ContextPlannerOutput JSON 对象。
```

运行指令：

```text
你是上下文选择规划器，不是任务执行器。

你的工作是检查当前用户输入和 Runtime 准备好的紧凑候选列表，然后决定下一次 MainAgentNode 调用必须看到什么。

按以下步骤执行：

1. 判断用户输入是否依赖前文。
   把“这个”“那个”“上一个”“继续”“第二版”“这篇文章”和很短的追问视为依赖历史的信号。

2. 判断是否引用了某个产物候选。
   使用产物标题、摘要、别名、最近度分数、候选原因和用户措辞判断。
   如果只有一个产物高度可能，选择它。
   如果多个产物仍然都可能成立，把 status 设为 NEEDS_USER_CLARIFICATION。

3. 判断产物内容粒度。
   如果 MainAgentNode 只需要产物身份，例如发布、上传、归档、删除或移动，使用 METADATA_ONLY。
   如果是概览、标题建议、轻量评价或路由判断，使用 SUMMARY_PLUS_SNIPPET。
   如果用户要求审查、重写、润色、重构、对比或修改短产物，使用 FULL_TEXT。
   如果产物过长并超出 token budget，使用 CHUNKED_CONTEXT。

4. 判断记忆需求。
   只有当记忆会影响当前回答、用户偏好、项目背景或追问理解时才选择记忆。

5. 判断证据需求。
   当用户询问之前的工具结果、RAG 结果、发布状态或 run 内已经产生的事实时，选择证据。

6. 估算上下文预算。
   如果选择的上下文太大，优先使用摘要、片段或分块上下文。

7. 不要解决用户任务。
   不要草拟答案。
   不要调用工具。
   不要直接请求 RAG。
   只输出上下文规划 JSON。
```

输入字段指南：

```text
ContextPlannerInput 只包含紧凑候选，不包含完整后端状态。

runMeta 标识当前 run 和循环轮次。
userInput 是当前用户消息。
recentMessages 是紧凑的近期可见消息或摘要。
sessionSummaries 是压缩后的会话摘要。
artifactCandidates 是候选产物，包含 id、title、summary、aliases、contentRef、tokenCount、score 和 reasons。
memoryCandidates 是候选记忆，包含 id、type、summary、score 和 reasons。
pendingAction 描述存在的暂停或未完成动作。
availableCapabilities 只列出能力元数据摘要，不是可执行工具 schema。
tokenBudget 描述当前上下文预算。

contentRef、payloadRef、evidenceId、memoryId 和 artifactId 都是引用。你不加载它们。Runtime 读取你的输出后负责加载被引用的内容。
```

决策策略：

```text
当主节点只需要识别产物身份时选择 METADATA_ONLY。
当紧凑摘要足够时选择 SUMMARY_ONLY。
当需要轻量查看内容时选择 SUMMARY_PLUS_SNIPPET。
当用户要求修改、润色、审查、对比或深入推理一个短产物时选择 FULL_TEXT。
当产物过长但又必须检查内容时选择 CHUNKED_CONTEXT。
当目标身份或意图不应该猜测时选择 NEEDS_USER_CLARIFICATION。
当候选对当前请求没有帮助时选择 NO_RELEVANT_CONTEXT。
```

正例：

```text
例 A：
用户：“把这篇 RAG 文章发布到 CSDN。”
候选：art_001，标题为“RAG article”，最近刚创建。
输出：选择 art_001，contextLevel 为 METADATA_ONLY，likelyNeedsTool=true。
原因：MainAgentNode 只需要目标身份；ToolRuntime 稍后会拿到全文。

例 B：
用户：“帮我优化这篇文章的结构。”
候选：art_001，标题为“RAG article”，tokenCount 为 700。
输出：选择 art_001，contextLevel 为 FULL_TEXT。
原因：MainAgentNode 必须阅读并重写内容。

例 C：
用户：“发布第二个。”
候选：多个产物，排序含义不明确。
输出：NEEDS_USER_CLARIFICATION，并给出候选选项。
原因：目标不能安全推断。
```

反例：

```text
错误：直接回答用户。
错误：发布类任务只需要元数据时仍选择 FULL_TEXT。
错误：编造候选中不存在的产物内容。
错误：返回工具 action，而不是上下文计划。
```

### 5.7 ContextPlannerOutput 契约

```json
{
  "status": "READY",
  "contextIntent": {
    "dependsOnHistory": true,
    "dependsOnArtifact": true,
    "dependsOnMemory": false,
    "likelyNeedsRag": false,
    "likelyNeedsTool": true,
    "requiresUserClarification": false
  },
  "selectedMessages": [
    {
      "messageId": "msg_001",
      "useLevel": "SUMMARY_ONLY",
      "reason": "用户引用了前文内容。"
    }
  ],
  "selectedSummaries": [],
  "selectedMemories": [],
  "selectedArtifacts": [
    {
      "artifactId": "art_001",
      "contextLevel": "METADATA_ONLY",
      "reason": "工具执行稍后需要全文；主节点只需要目标产物身份。",
      "confidence": 0.94
    }
  ],
  "requestedEvidence": [],
  "contextBudgetPlan": {
    "estimatedInputTokens": 1800,
    "artifactLoadingStrategy": "LOAD_METADATA_ONLY",
    "compressionRequired": false
  },
  "ambiguity": {
    "level": "LOW",
    "candidates": [],
    "askUserQuestion": null
  },
  "warnings": []
}
```

允许的 `status`：

- `READY`
- `NEEDS_USER_CLARIFICATION`
- `NO_RELEVANT_CONTEXT`
- `CONTEXT_OVER_BUDGET`
- `FAILED`

允许的上下文粒度：

- `NONE`
- `METADATA_ONLY`
- `SUMMARY_ONLY`
- `SUMMARY_PLUS_SNIPPET`
- `FULL_TEXT`
- `CHUNKED_CONTEXT`

### 5.8 Runtime Context Materialization（运行时上下文实体化/装载）

`ContextPlannerOutput` 不能直接传给 `MainAgentNode`。

Runtime 必须先实体化被选择的引用，然后再构建 `MainAgentStateView`。

实体化流程：

```text
ContextPlannerOutput
  -> Runtime 校验选中的 id 和 context level
  -> Runtime 解析消息摘要、记忆摘要、产物元数据、产物 payload、证据摘要和选中的 payload 片段
  -> ContextBudgetManager 检查预算
  -> Runtime 在需要时执行压缩或分块
  -> Runtime 构建 MainAgentStateView
  -> MainAgentNode 只接收实体化后的 StateView
```

规则：

- `METADATA_ONLY` 加载 artifact id、title、type、summary、aliases、version 和 payload reference，不加载产物正文。
- `SUMMARY_ONLY` 加载已保存的摘要和引用 id。
- `SUMMARY_PLUS_SNIPPET` 加载摘要和 Runtime 选择的有界片段。
- `FULL_TEXT` 只有在 payload 位于配置预算内时才加载全文。
- `CHUNKED_CONTEXT` 加载 chunk descriptor（分块描述）和选中的 chunk（内容块），不加载整个 payload。
- 工具回执不能作为无界原始 JSON 加载。Runtime 只把回执摘要和必要结果字段加载到 `evidencePack`。
- 原始 prompt、原始模型输出、完整 trace（轨迹）和 debug payload（调试载荷）不得实体化到 `MainAgentStateView`。
- 如果压缩后仍然超过预算，Runtime 必须调用上下文修复/压缩流程，或通过已定义恢复路径返回 `ASK_USER`。

这里明确所有权边界：`ContextPlannerNode` 选择应该加载什么；Runtime 决定如何安全加载；`MainAgentNode` 接收最终 `MainAgentStateView`。

### 5.9 MainAgentNode 提示词

数据库角色提示词：

```text
你是 MainAgentNode。你是主要语义决策和生成节点。
你读取 MainAgentStateView，并选择且只选择一个下一步动作。
你不直接调用工具。如果需要工具，输出 CALL_TOOL。
你不访问数据库，不控制运行生命周期，不写 trace、audit、tool receipt、verifier result、run status、next state 或 loop index。
```

Java 固定规则：

```text
只能使用 MainAgentStateView 中提供的事实。
只输出一个 MainAgentAction JSON 对象。
action 必须是允许的枚举。
stateDelta 字段必须匹配所选 action。
不能包含超出 StateDelta 写入范围的字段。
信息不足时输出 ASK_USER。
需要外部知识时输出 RETRIEVE_RAG。
需要外部动作时输出 CALL_TOOL。
任务完成时输出 FINAL。
```

运行指令：

```text
你是一次 agent loop iteration（智能体循环轮次）的主要语义控制器。

你不执行整个 run。Runtime 控制 run 的生命周期。
你在本次调用中的唯一工作，是根据给定 StateView 决定下一步语义动作，并为该动作输出精确 JSON。

按以下步骤执行：

1. 理解用户当前意图。
   判断用户需要直接回答、可复用产物、产物更新、知识检索、外部工具动作、澄清、计划，还是失败说明。

2. 检查已提供上下文。
   只能使用 MainAgentStateView。不要假设缺失的产物、工具结果、RAG 证据或记忆。

3. 选择且只选择一个 action：
   - FINAL：当已有足够上下文可以直接回答时使用。
   - CREATE_ARTIFACT：当用户要求生成可复用文章、代码、文件类内容、表格、计划文档或长文本时使用。
   - UPDATE_ARTIFACT：当用户要求修改现有产物时使用。
   - RETRIEVE_RAG：当回答前需要知识库证据时使用。
   - CALL_TOOL：当需要外部副作用或外部系统查询/动作时使用。
   - ASK_USER：当歧义、缺少审批、缺少目标或缺少必要用户信息阻塞安全执行时使用。
   - PLAN：只在复杂多步任务中，保存内部计划有助于执行时使用。
   - CONTINUE：只在 Runtime 需要基于已写入状态再跑一轮，且没有更具体 action 适用时使用。
   - REPAIR_FINAL：只在 Runtime 要求你修复最终回答时使用。
   - FAIL：当任务无法继续且没有安全恢复路径时使用。

4. 生成正确的 StateDelta。
   StateDelta 结构必须匹配 action。不要包含额外状态字段。

5. 最终回答必须面向用户。
   对 FINAL 和 REPAIR_FINAL，只写用户应该看到的回答。
   不要提到 Runtime、node、verification、trace、contract、prompt 或内部流程。

6. 工具动作必须干净。
   对 CALL_TOOL，只描述工具意图和 expectedOutcome。
   不要声称工具已经成功。
   不要在 CALL_TOOL 中包含 finalAnswerCandidate。

7. RAG 动作必须干净。
   对 RETRIEVE_RAG，只写检索 query 和 purpose。
   不要编造知识库结果。

8. 只输出一个 JSON 对象。
```

输入字段指南：

```text
MainAgentStateView 是本次调用唯一事实来源。

userInput 是当前用户请求。
conversation 包含被选择的近期消息和摘要。
memoryPack 包含被选择的用户偏好或长期事实。
resolvedArtifacts 标识 Runtime 已解析的产物。
artifactContent 只包含 Runtime 根据上下文策略选中的产物内容。
evidencePack 包含来自 RAG、工具、记忆、产物或用户确认的摘要事实。
availableCapabilities 描述 Runtime 支持的动作；它不是工具调用接口。
pendingAction 描述从 ASK_USER 或上一轮恢复的工作。
currentPlan 是存在时的内部计划状态。
lastVerifierFeedback 包含 verifier（验收器）或 guard（保护器）给出的结构化失败或警告。
outputContractVersion 表示当前 Java 契约外壳版本。
```

决策策略：

```text
只有信息充足且不需要外部动作时，才优先 FINAL。
当用户要求可复用内容时，优先 CREATE_ARTIFACT。
当已解析产物需要被修改时，优先 UPDATE_ARTIFACT。
当回答需要 StateView 中不存在的私有知识库证据时，优先 RETRIEVE_RAG。
当需要外部副作用或外部服务交互时，优先 CALL_TOOL。
当目标、审批、凭证或意图存在歧义时，优先 ASK_USER。
只有在多步任务需要保存计划状态时，才优先 PLAN。
只有在没有更具体 action 且需要下一轮时，才优先 CONTINUE。
只有在没有安全恢复路径时，才优先 FAIL。
```

正例：

```text
例 A：
用户：“RAG 是什么？”
StateView 已经有足够通用上下文，并且不需要私有知识库。
输出：FINAL。

例 B：
用户：“生成一篇 200 字 RAG 面试八股文。”
输出：CREATE_ARTIFACT，包含文章草稿和简短 finalAnswerCandidate。

例 C：
用户：“把这篇 RAG 文章发布到 CSDN。”
StateView 已解析 art_001。
输出：CALL_TOOL，capabilityCode 为 content_publish，requiredArtifactIds 为 ["art_001"]。
不要声称已经发布成功。

例 D：
用户：“修改这篇文章的结构。”
StateView 包含 art_001 全文。
输出：UPDATE_ARTIFACT。

例 E：
用户：“发布第二版。”
StateView 报告目标歧义。
输出：ASK_USER。
```

反例：

```text
错误：CALL_TOOL 同时包含 finalAnswerCandidate 并声称工具成功。
错误：FINAL 提到 Runtime、node、trace、verifier 或内部流程。
错误：相关证据已经提供时仍然 RETRIEVE_RAG。
错误：写入 runStatus 或 nextState。
错误：编造 artifact id、tool receipt、URL 或 RAG evidence。
```

### 5.10 MainAgentAction 外壳

每个 MainAgentNode 输出必须使用：

```json
{
  "action": "FINAL",
  "confidence": 0.9,
  "userVisibleThought": null,
  "reasonCode": "READY_TO_ANSWER",
  "stateDelta": {},
  "safety": {
    "needsUserApproval": false,
    "riskLevel": "LOW"
  }
}
```

允许的 action：

- `FINAL`
- `CREATE_ARTIFACT`
- `UPDATE_ARTIFACT`
- `RETRIEVE_RAG`
- `CALL_TOOL`
- `ASK_USER`
- `PLAN`
- `CONTINUE`
- `REPAIR_FINAL`
- `FAIL`

### 5.11 FINAL Action

```json
{
  "action": "FINAL",
  "confidence": 0.94,
  "userVisibleThought": null,
  "reasonCode": "READY_TO_ANSWER",
  "stateDelta": {
    "finalAnswerCandidate": {
      "content": "最终给用户看的回答。",
      "format": "PLAIN_TEXT",
      "citations": [
        {
          "evidenceId": "evd_001",
          "usage": "USED_AS_SUPPORT"
        }
      ],
      "followUpOptions": []
    }
  },
  "safety": {
    "needsUserApproval": false,
    "riskLevel": "LOW"
  }
}
```

Runtime 将 `stateDelta.finalAnswerCandidate.content` 视为候选最终回答。只有通过 `FinalResponseGuard` 后才能给用户展示。

### 5.12 CREATE_ARTIFACT Action

```json
{
  "action": "CREATE_ARTIFACT",
  "confidence": 0.9,
  "userVisibleThought": null,
  "reasonCode": "USER_REQUESTED_REUSABLE_CONTENT",
  "stateDelta": {
    "artifactDraft": {
      "type": "ARTICLE",
      "title": "RAG 面试八股文",
      "summary": "一篇简洁的 RAG 面试回答。",
      "content": "产物正文。",
      "format": "PLAIN_TEXT",
      "suggestedAliases": ["RAG 八股文", "刚才那篇 RAG 文章"]
    },
    "finalAnswerCandidate": {
      "content": "已创建这篇文章。",
      "format": "PLAIN_TEXT",
      "citations": [],
      "followUpOptions": []
    }
  },
  "safety": {
    "needsUserApproval": false,
    "riskLevel": "LOW"
  }
}
```

Runtime 保存产物正文到 payload 存储，并保存产物元信息。

### 5.13 UPDATE_ARTIFACT Action

```json
{
  "action": "UPDATE_ARTIFACT",
  "confidence": 0.88,
  "userVisibleThought": null,
  "reasonCode": "USER_REQUESTED_ARTIFACT_REVISION",
  "stateDelta": {
    "artifactPatch": {
      "targetArtifactId": "art_001",
      "updateMode": "REPLACE_FULL",
      "title": "RAG 面试八股文优化版",
      "summary": "调整结构后的版本。",
      "content": "更新后的产物正文。",
      "changeSummary": "改为定义、原理、优点和应用场景结构。"
    },
    "finalAnswerCandidate": {
      "content": "已更新这篇文章。",
      "format": "PLAIN_TEXT",
      "citations": [],
      "followUpOptions": []
    }
  },
  "safety": {
    "needsUserApproval": false,
    "riskLevel": "LOW"
  }
}
```

允许的 `updateMode`：

- `REPLACE_FULL`
- `PATCH_TEXT`
- `APPEND`
- `CREATE_VERSION`

Runtime 必须校验目标产物并保存新版本或子产物。

### 5.14 RETRIEVE_RAG Action

```json
{
  "action": "RETRIEVE_RAG",
  "confidence": 0.86,
  "userVisibleThought": null,
  "reasonCode": "NEEDS_KNOWLEDGE_BASE_EVIDENCE",
  "stateDelta": {
    "ragRequest": {
      "query": "RAG 的定义、流程、优点和面试回答",
      "knowledgeBaseScope": "AUTO",
      "filters": {
        "tags": ["RAG", "LLM"],
        "sourceTypes": ["DOC", "NOTE"]
      },
      "topK": 5,
      "purpose": "SUPPORT_FINAL_ANSWER"
    }
  },
  "safety": {
    "needsUserApproval": false,
    "riskLevel": "LOW"
  }
}
```

Runtime 执行 RAG 检索，并在下一轮把 RAG evidence 给主节点。

### 5.15 CALL_TOOL Action

```json
{
  "action": "CALL_TOOL",
  "confidence": 0.91,
  "userVisibleThought": null,
  "reasonCode": "USER_REQUESTED_EXTERNAL_ACTION",
  "stateDelta": {
    "toolIntent": {
      "goal": "将已生成的 RAG 文章发布到 CSDN。",
      "capabilityCode": "content_publish",
      "requiredArtifactIds": ["art_001"],
      "requiredEvidenceIds": [],
      "inputRequirements": {
        "needsArtifactFullText": true,
        "needsUserCredential": true
      },
      "expectedOutcome": {
        "outcomeType": "PUBLISH_CONTENT",
        "desiredResultHints": [
          "如果工具返回发布链接，后续回答可以使用。",
          "如果工具返回发布编号，后续回答可以使用。"
        ]
      }
    }
  },
  "safety": {
    "needsUserApproval": true,
    "riskLevel": "HIGH"
  }
}
```

`expectedOutcome` 是任务级意图上下文。MVP 的 `ToolVerifier` 不根据它强验收业务完成度。如果真实回执包含 URL、发布编号、路径或状态等有用字段，Runtime 可以把它们摘要为工具 evidence，交给下一轮 `MainAgentNode` 诚实回答。

### 5.16 ASK_USER Action

```json
{
  "action": "ASK_USER",
  "confidence": 0.82,
  "userVisibleThought": null,
  "reasonCode": "NEEDS_USER_CLARIFICATION",
  "stateDelta": {
    "askUserRequest": {
      "question": "你想发布哪一篇 RAG 文章？",
      "inputMode": "SINGLE_CHOICE",
      "options": [
        {
          "optionId": "opt_001",
          "label": "约 200 字版本",
          "value": {
            "artifactId": "art_001"
          }
        },
        {
          "optionId": "opt_002",
          "label": "详细面试版",
          "value": {
            "artifactId": "art_002"
          }
        }
      ],
      "allowFreeText": true,
      "resumeHint": {
        "afterUserResponse": "RESOLVE_ARTIFACT_AND_CONTINUE"
      }
    }
  },
  "safety": {
    "needsUserApproval": false,
    "riskLevel": "LOW"
  }
}
```

允许的 `inputMode`：

- `CONFIRM`
- `SINGLE_CHOICE`
- `MULTI_CHOICE`
- `FREE_TEXT`

### 5.17 PLAN Action

```json
{
  "action": "PLAN",
  "confidence": 0.84,
  "userVisibleThought": null,
  "reasonCode": "COMPLEX_MULTI_STEP_TASK",
  "stateDelta": {
    "planDraft": {
      "goal": "修改 RAG 文章并发布。",
      "steps": [
        {
          "stepId": "step_001",
          "title": "确认文章内容",
          "status": "PENDING"
        },
        {
          "stepId": "step_002",
          "title": "发布内容",
          "status": "PENDING"
        }
      ],
      "nextStepId": "step_001"
    }
  },
  "safety": {
    "needsUserApproval": false,
    "riskLevel": "LOW"
  }
}
```

计划是内部状态，不能作为最终回答展示。

### 5.18 CONTINUE Action

```json
{
  "action": "CONTINUE",
  "confidence": 0.72,
  "userVisibleThought": null,
  "reasonCode": "NEEDS_NEXT_LOOP_WITH_UPDATED_STATE",
  "stateDelta": {
    "nextActionHint": {
      "focus": "使用最新工具证据生成最终回答。",
      "requiredState": ["TOOL_EVIDENCE"]
    }
  },
  "safety": {
    "needsUserApproval": false,
    "riskLevel": "LOW"
  }
}
```

Runtime 必须对这个 action 执行循环上限检查。

### 5.19 REPAIR_FINAL Action

```json
{
  "action": "REPAIR_FINAL",
  "confidence": 0.89,
  "userVisibleThought": null,
  "reasonCode": "FINAL_GUARD_FAILED",
  "stateDelta": {
    "finalAnswerCandidate": {
      "content": "修复后的最终用户可见回答。",
      "format": "PLAIN_TEXT",
      "citations": [],
      "followUpOptions": [],
      "repairNotes": "移除了内部过程描述。"
    }
  },
  "safety": {
    "needsUserApproval": false,
    "riskLevel": "LOW"
  }
}
```

该 action 只允许在 `REPAIRING_FINAL` 阶段使用。

### 5.20 FAIL Action

```json
{
  "action": "FAIL",
  "confidence": 0.8,
  "userVisibleThought": null,
  "reasonCode": "MISSING_REQUIRED_INFORMATION",
  "stateDelta": {
    "failure": {
      "userMessage": "我无法完成发布，因为当前没有可用的 CSDN 登录状态。",
      "technicalCode": "MISSING_CREDENTIAL",
      "retryable": true,
      "suggestedRecovery": "请先完成 CSDN 登录后再重试。"
    }
  },
  "safety": {
    "needsUserApproval": false,
    "riskLevel": "LOW"
  }
}
```

Runtime 只能向普通前端展示 `failure.userMessage`。

### 5.21 ToolRuntime 契约

ToolRuntime 是 Java 确定性组件，不是 LLM 节点，因此没有数据库角色提示词。

它按以下步骤执行：

```text
1. 接收 Runtime 构造的 ToolInvocationRequest。
2. 通过 McpClientRegistry 按 mcpServerCode 解析 Spring AI MCP Client。
3. 通过 McpToolRegistry 按 mcpServerCode + toolName 解析工具元数据和 input schema。
4. 校验 capability 是否启用，且是否绑定到请求中的 MCP 工具。
5. 执行 PermissionEnforcer，检查权限、风险、工作区范围和用户审批。
6. 如果缺少审批，返回 NEEDS_USER_ACTION，由 Runtime 创建 pending input。
7. 将 artifact/evidence 引用实体化为有界工具参数。
8. 在 schema 可用时校验最终 arguments。
9. 通过 Spring AI MCP Client 调用真实 MCP 工具。
10. 将原始 receipt 保存为 payload，并生成 compact receipt summary 给 StateView。
11. 返回 ToolInvocationResult。
```

硬规则：

- ToolRuntime 不调用 LLM。
- ToolRuntime 不回答用户，不创建 artifact，不控制 run lifecycle。
- ToolRuntime 必须失败关闭：server、tool、client、schema 或 permission 无法解析时不能假装成功。
- Runtime 以真实 ToolReceipt 为事实来源，不以任何模型生成摘要作为事实来源。
- ToolRuntime 只通过 RunEventPublisher 发送用户可见进度事件。

### 5.22 ToolInvocationResult 契约

```json
{
  "status": "SUCCESS",
  "toolCallId": "tool_call_001",
  "capabilityCode": "content_publish",
  "mcpServerCode": "csdn",
  "toolName": "publish_article",
  "transport": "SSE",
  "argumentsRef": "payload_tool_args_001",
  "receiptRef": "payload_receipt_001",
  "callLevelSuccess": true,
  "receiptSummary": {
    "statusText": "工具返回成功。",
    "returnedFields": ["url", "publishId"],
    "userVisibleSummary": "发布工具返回了成功结果。"
  },
  "needsUserAction": false,
  "error": null
}
```

允许状态：

- `SUCCESS`
- `FAILED`
- `NO_TOOL_CALLED`
- `NEEDS_USER_ACTION`
- `INVALID_TOOL_INTENT`
- `TOOL_NOT_AVAILABLE`
- `PARTIAL_SUCCESS`

Runtime 必须以捕获到的真实 `ToolReceipt` 为事实源，而不是 `ToolInvocationResult.summary`。

### 5.23 UserInputResolverNode 提示词

数据库角色提示词：

```text
你是 UserInputResolverNode。你的唯一职责是根据 pending input 的问题、选项和 answerContract，解析用户的自由文本回复。
你不回答用户，不调用工具，不创建产物，不控制运行生命周期。
```

Java 固定规则：

```text
只能使用 UserInputResolverInput。
如果用户回复能明确映射到某个 option，则输出 MATCHED_OPTION。
如果用户回复能按 answerContract 解析成结构化值，则输出 RESOLVED_VALUE。
如果用户取消、拒绝或表达不继续，则输出 USER_CANCELLED。
如果无法确定，输出 NEEDS_CLARIFICATION，不要猜测。
只返回 UserInputResolution JSON。
```

### 5.24 UserInputResolution 契约

```json
{
  "status": "MATCHED_OPTION",
  "matchedOptionId": "opt_latest",
  "normalizedValue": {
    "artifactId": "art_001",
    "contextLevel": "METADATA_ONLY"
  },
  "confidence": 0.86,
  "needsClarification": false,
  "clarificationQuestion": null,
  "reason": "用户说“最新那篇”，与 opt_latest 的 label 和 value 匹配。"
}
```

允许状态：

- `MATCHED_OPTION`
- `RESOLVED_VALUE`
- `NEEDS_CLARIFICATION`
- `USER_CANCELLED`
- `INVALID_REPLY`

Runtime 只能通过 pending input continuation handler 应用 `normalizedValue`。`UserInputResolverNode` 不能直接更新 run 状态。

### 5.25 修复提示词契约

修复调用必须是定向修复。

Runtime 使用原始用户请求、非法输出、校验错误、保护器/验收器失败、允许修复范围、Java 契约包和恢复模板构造 `RepairStateView`。

稳定修复规则：

```text
只修复指定输出结构。
不要重新规划任务。
不要调用工具。
不要增加生命周期字段。
只输出契约要求的修正 JSON 对象。
```

## 6. 验收器和保护器流水线

### 6.1 VerificationResult 契约

所有 verifier（验收器）和 guard（保护器）必须输出或转换为：

```json
{
  "verifier": "ToolVerifier",
  "targetType": "TOOL_CALL",
  "targetId": "tool_call_001",
  "passed": false,
  "status": "FAILED",
  "failureCode": "TOOL_RECEIPT_MISSING",
  "severity": "BLOCKING",
  "summary": "ToolRuntime 没有捕获到真实工具回执。",
  "repairHints": [
    "重试工具一次，或询问用户稍后重试。"
  ],
  "evidenceRefs": ["evd_tool_001"],
  "detailRef": "payload_verification_001"
}
```

允许状态：

- `PASSED`
- `FAILED`
- `NEEDS_RETRY`
- `NEEDS_USER`
- `SKIPPED`

允许严重程度：

- `INFO`
- `WARNING`
- `BLOCKING`
- `FATAL`

### 6.2 失败码

MVP 失败码包括：

- `CONTRACT_INVALID`
- `CONTRACT_PARSE_FAILED`
- `FINAL_EMPTY`
- `FINAL_INTERNAL_LEAK`
- `FINAL_FORMAT_VIOLATION`
- `FINAL_INVALID_CITATION`
- `FINAL_FALSE_TOOL_CLAIM`
- `FINAL_TOO_LONG`
- `TOOL_NOT_CALLED`
- `TOOL_RECEIPT_MISSING`
- `TOOL_FAILED`
- `TOOL_RESULT_MISMATCH`（预留；MVP `ToolVerifier` 不产出）
- `TOOL_SCHEMA_ERROR`
- `TOOL_APPROVAL_REQUIRED`
- `RAG_NO_EVIDENCE`
- `RAG_NO_HIT`
- `RAG_UNGROUNDED`
- `RAG_CONTRADICTION`
- `CONTEXT_OVER_BUDGET`
- `MAX_LOOP_REACHED`

### 6.3 ContractValidator

`ContractValidator` 是纯 Java。

它必须校验：

- JSON 解析成功。
- 节点输出外壳。
- action 枚举合法。
- 必填字段。
- action 对应的 StateDelta 写入范围。
- 禁止生命周期字段。
- 枚举值。
- yml 控制的字段最大长度。

处理流程：

1. 严格 JSON 解析和 schema 校验。
2. 只对低风险格式问题做安全提取。
3. 有预算时进行契约修复。
4. 修复耗尽后安全失败。

安全提取只能：

- 移除 markdown 代码块。
- 提取唯一 JSON 对象。
- 去掉前后解释文本。
- 清理 BOM 或编码噪声。

不能猜测语义字段，不能把自然语言转换成 action。

### 6.4 FinalResponseGuard MVP 流水线

`FinalResponseGuard` MVP 是 Java 规则型，最终回答返回前必须执行。

流水线：

1. `EmptyAnswerGuard`
2. `InternalLeakGuard`
3. `FormatGuard`
4. `EvidenceReferenceGuard`
5. `ToolClaimGuard`
6. `LengthGuard`

LLM 安全、策略、质量护栏是 backlog，不属于 MVP。

### 6.5 ToolVerifier

`ToolVerifier` MVP 只验收工具执行证明，不验收完整业务成功。

输入：

- `toolIntent`
- `expectedOutcome`
- `capabilitySpec`
- 捕获到的 `ToolReceipt`
- 工具执行输出
- 相关产物和证据摘要

必须检查：

- 真实回执存在。
- MCP/Spring AI 工具框架确实捕获到至少一次真实工具调用。
- 被调用的 MCP server/tool 必须匹配解析后的 capability 和 McpToolRegistry。
- 配置了 bound tool name 时，工具名匹配绑定能力。
- 高风险工具已有审批。
- 回执不是由 `ToolInvocationResult.summary` 编造出来的。
- 工具框架没有报告调用级错误。
- 如果回执明确失败，则返回 `TOOL_FAILED`。

MVP 不做：

- 不要求每个工具配置复杂 successSignals。
- 不要求每个工具配置 requiredResultFields。
- 不判断外部业务目标是否完全完成。
- 不使用 LLM。

`expectedOutcome` 只作为意图和证据上下文，交给下一轮 `MainAgentNode` 参考。复杂工具业务验收进入 Backlog。

### 6.6 RagVerifier

`RagVerifier` 是 LLM 验收节点，在本 run 使用过 RAG 且最终回答返回前，验收 grounding honesty（证据诚实性）。

必须检查：

- 用户要求基于知识库时，回答必须使用 RAG evidence，或者诚实说明没有找到相关资料。
- 回答声称“根据知识库/文档/检索结果”时，必须有 RAG evidence 支撑。
- 如果回答有 citation，每个 citation 必须引用存在且能支撑对应声明的 evidence。
- 回答不能与检索证据矛盾。
- RAG 结果不相关且回答未声称基于知识库时，可以通过但记录 warning。

`RagVerifier` 不评价写作质量，也不重写回答，只输出 `VerificationResult`。

### 6.7 RecoveryPolicy

Runtime 必须把失败映射为确定性恢复动作：

| 失败 | 恢复 |
|---|---|
| `CONTRACT_INVALID` | 有界契约修复 |
| `CONTRACT_PARSE_FAILED` | 允许时先安全提取，再有界契约修复；否则安全失败 |
| `FINAL_EMPTY` | `REPAIR_FINAL`；修复预算耗尽后安全失败 |
| `FINAL_INTERNAL_LEAK` | `REPAIR_FINAL` |
| `FINAL_FORMAT_VIOLATION` | `REPAIR_FINAL` |
| `FINAL_INVALID_CITATION` | `REPAIR_FINAL`，移除或修正缺失的 citation evidence id |
| `FINAL_FALSE_TOOL_CLAIM` | 阻止最终回答，基于工具证据继续或失败 |
| `FINAL_TOO_LONG` | 按配置长度限制进入 `REPAIR_FINAL` |
| `TOOL_NOT_CALLED` | 重试 ToolRuntime 一次，然后把失败证据交给 MainAgentNode |
| `TOOL_RECEIPT_MISSING` | 重试 ToolRuntime 一次，然后把失败证据交给 MainAgentNode 或安全失败 |
| `TOOL_FAILED` | 根据回执错误询问用户、重试一次或失败 |
| `TOOL_RESULT_MISMATCH` | 预留给未来业务完成度验收；MVP ToolVerifier 不产出该失败码 |
| `TOOL_SCHEMA_ERROR` | 拒绝工具 action，并把校验反馈交给 MainAgentNode；重复失败后安全失败 |
| `TOOL_APPROVAL_REQUIRED` | 创建 `agent_pending_input`，设置 run 为 `WAITING_USER`，审批后恢复工具准备流程 |
| `RAG_NO_EVIDENCE` | 预算允许时请求 RAG 检索；否则修复最终回答，移除知识库依据声明 |
| `RAG_NO_HIT` | 允许重写 query 一次，或说明没有知识库资料 |
| `RAG_UNGROUNDED` | 基于证据修复最终回答 |
| `RAG_CONTRADICTION` | 基于证据修复最终回答；无法解决矛盾时安全失败 |
| `CONTEXT_OVER_BUDGET` | 压缩、分块或询问用户 |
| `MAX_LOOP_REACHED` | 安全失败或真实的部分结果 |

Runtime 不能无限重试。

## 7. 持久化设计

### 7.1 持久化原则

持久化必须支持运行恢复、上下文规划、产物复用、基于证据的验收、前端事件展示和调试。

不能把所有运行数据都塞进一个非结构化 context 字段。数据必须按职责分离：

- 会话可见消息
- run 生命周期
- 记忆
- 产物
- 证据
- 工具执行
- RAG 执行
- 事件、轨迹、审计
- 大内容 payload

### 7.2 存储分组

MVP 必须包含：

| 分组 | 目的 |
|---|---|
| Conversation | 用户可见会话和消息。 |
| Run | 一次用户请求的后端执行实例。 |
| Memory | 会话摘要、长期记忆、记忆事件。 |
| Artifact | 可复用生成内容和版本。 |
| Evidence | 来自 RAG、工具、记忆、产物和用户确认的事实。 |
| Tool | 工具意图、审批、真实调用、回执、验收。 |
| RAG | 每次 run 的检索请求和命中记录。 |
| Event/Trace/Audit | 用户进度、开发者调试、统计。 |
| Payload | 大文本、JSON、提示词、原始输出、工具回执、RAG chunk、产物正文。 |

### 7.3 到 7.11 数据表

英文正式 spec 第 7.3-7.11 已给出完整表设计，包括：

- `agent_session`
- `agent_message`
- `agent_run`
- `agent_run_state_snapshot`
- `agent_conversation_summary`
- `agent_long_term_memory`
- `agent_memory_event`
- `agent_artifact`
- `agent_artifact_alias`
- `agent_artifact_relation`
- `agent_payload`
- `agent_evidence`
- `agent_tool_call`
- `agent_tool_approval`
- `agent_tool_verification`
- `agent_rag_query`
- `agent_rag_hit`
- `agent_run_event`
- `agent_run_trace`
- `agent_run_audit`

中文审查要点：这些表按职责分开，避免把状态、消息、证据、回执、产物和调试信息混在一个大字段里。后续实现以英文正式 spec 的字段和索引为准。

### 7.12 Repository 接口

领域层定义接口，基础设施层实现。

必须包含：

```text
IRunRepository
IConversationRepository
IMemoryRepository
IArtifactRepository
IEvidenceRepository
IToolRepository
IRagExecutionRepository
IEventTraceRepository
IPayloadRepository
INodePromptRepository
```

## 8. DDD 目录结构

### 8.1 顶层规则

保留一个顶层 `agent` domain。MVP 不把 memory、artifact、tool、RAG、verification、runtime 拆成独立顶层 domain，因为它们共同服务一次 agent run 生命周期。

### 8.2 Domain 目录

英文正式 spec 第 8.2 给出完整包结构，核心包括：

```text
yhx.com.domain.agent
  adapter.repository
  model.entity
  model.valobj
  model.valobj.enums
  service.execute
  service.context
  service.node
  service.contract
  service.memory
  service.artifact
  service.evidence
  service.tool
  service.rag
  service.verification
  service.event
  service.armory
```

### 8.3 Infrastructure 目录

基础设施层负责 repository 实现、DAO、PO、RAG 适配、MCP 回执捕获、payload 存储。

### 8.4 App 目录

app 负责 Spring Bean 装配：

```text
AutoAgentRuntimeConfig
AgentRepositoryConfig
AgentNodeConfig
AgentToolConfig
AgentRagConfig
AgentSseConfig
```

### 8.5 Trigger 目录

trigger 只暴露 API，不直接执行节点逻辑。

## 9. 能力和工具配置

### 9.1 配置分层

| 层 | 职责 |
|---|---|
| Java Config | Bean 装配和依赖图。 |
| yml | 系统默认值、阈值、能力默认配置、节点模型默认值。 |
| Database | agent 级运行覆盖配置和可编辑提示词。 |

优先级：

```text
database agent config > yml defaults > Java fallback constants
```

### 9.2 CapabilityRegistry

`CapabilityRegistry` 在 MVP 中只管理 external tool capability（外部工具能力）元数据。RAG 是 `RagRuntime` 内置能力，单独通过 `auto-agent.rag` 配置。

MVP 工具能力配置只需要 capabilityCode、capabilityType、boundToolName、riskLevel、approvalRequired、enabled 等基础字段。复杂 successSignals 和 requiredResultFields 属于后续工具业务验收增强。

### 9.3 工具意图和验收

MainAgentNode 输出任务级 `expectedOutcome`，不硬编码某个工具返回格式。

Runtime 组合：

```text
toolIntent.expectedOutcome
+ capabilitySpec
+ real ToolReceipt
```

MVP `ToolVerifier` 只验收真实调用证明、允许/绑定工具、审批、回执存在和基础调用错误状态。`expectedOutcome` 会作为下一轮 `MainAgentNode` 的意图上下文，不作为强业务验收依据。

### 9.4 yml 默认值

英文正式 spec 第 9.4 给出完整 yml 结构，包括 runtime、context、nodes、capabilities。

### 9.5 Prompt 存储

数据库表：`agent_node_prompt`。

该表只存可编辑提示词内容，不存 Java contract schema。

## 10. 前端 API 和 SSE

### 10.1 前端边界

普通前端只能消费：

- 聊天消息
- run 状态
- SSE 用户可见事件
- 最终响应
- 等待输入
- 产物摘要和正文

普通前端不能消费节点原始输出、提示词、验收详情、原始工具回执、ContextPlanner 输出、ToolRuntime 调用结果、trace payload、Runtime 内部状态。

### 10.2 Chat API

`POST /agent/chat`

创建 run，返回 runId、sessionId、userMessageId、status。

`GET /agent/sessions/{sessionId}/messages`

只返回用户可见消息。

### 10.3 Run API

`GET /agent/runs/{runId}`

返回 run 状态摘要。

`GET /agent/runs/{runId}/final`

返回最终响应；未完成时 finalAnswer 为 null。

### 10.4 SSE Event API

SSE emitter 是强制主设计。

`GET /agent/runs/{runId}/events/stream`

用于实时事件。

`GET /agent/runs/{runId}/events`

只作为历史/兜底查询。

### 10.5 ASK_USER API

`GET /agent/runs/{runId}/pending-input`

返回问题、选项、输入模式、是否允许自由输入。

`POST /agent/runs/{runId}/user-input`

提交用户选择或自由输入并恢复 run。

### 10.6 Artifact API

- `GET /agent/sessions/{sessionId}/artifacts`
- `GET /agent/artifacts/{artifactId}`
- `GET /agent/artifacts/{artifactId}/versions`

### 10.7 Debug API

debug 接口必须隔离并受权限或开关控制。

### 10.8 Mock API

必须支持前端 mock 场景和 mock SSE：

```text
GET /mock/agent/runs/{scenario}/events/stream
```

## 11. 日志、轨迹和审计

### 11.1 日志分层

| 层 | 受众 | 存储 |
|---|---|---|
| `UserVisibleEvent` | 普通前端 | `agent_run_event` |
| `DeveloperTrace` | 调试面板和开发者 | `agent_run_trace` |
| `AuditRecord` | 诊断和统计 | `agent_run_audit` |
| `Payload` | 大型原始数据 | `agent_payload` |

### 11.2 UserVisibleEvent

用户可见事件必须简短、干净、可读。

不能包含 raw JSON、prompt、node output、stack trace、verifier detail、raw receipt、internal contract。

### 11.3 DeveloperTrace

开发者轨迹可以包含 StateView 摘要、节点输入输出 payload 引用、action、RAG 结果摘要、工具调用观察、验收结果、恢复动作、token 用量、错误。

大型详情必须通过 payload 引用保存。

### 11.4 AuditRecord

审计记录必须包含 run id、模型名、token 用量、延迟、循环次数、工具调用次数、RAG 查询次数、最终状态、错误码。

### 11.5 最终回答隔离

最终回答绝不能来自 developer trace、模型原始输出、验收摘要、工具回执、记忆摘要、运行状态或执行摘要。只能来自 guarded `FinalResponse`。

## 12. 测试策略

### 12.1 测试原则

MVP（最小可用版本）测试必须少，但必须真实有效。

项目不能为了“看起来测试很多”而加入大量低价值测试。测试重点应放在 protocol boundary（协议边界）、lifecycle transition（生命周期流转）、final-answer safety（最终回答安全）、evidence correctness（证据正确性）和 frontend event behavior（前端事件行为）上。

不要为以下内容增加测试：

- 简单 getter/setter。
- 没有逻辑的 DTO 映射。
- prompt wording snapshot（提示词措辞快照）。
- 不稳定的自然语言模型输出。
- 已经能通过 compile（编译）覆盖的低风险 Spring bean wiring（Bean 装配）。
- 与新 harness（运行框架）行为无关的 DAO CRUD。

必要测试必须是 deterministic（确定性）的。尽量使用 fake node client（模拟节点客户端）、fake RAG service（模拟 RAG 服务）、fake tool executor（模拟工具执行器）和 in-memory repository（内存仓储）。

### 12.2 测试层次

MVP 必须包含这些测试层：

| 层次 | 目的 |
|---|---|
| Contract tests（契约测试） | 校验节点输出解析、schema、action 字段和恢复行为。 |
| Runtime state-machine tests（运行时状态机测试） | 校验 run 生命周期、phase 流转、循环上限和恢复路由。 |
| Context tests（上下文测试） | 校验候选预筛、ContextPlanner 实体化、预算处理和产物加载策略。 |
| Tool/RAG evidence tests（工具/RAG 证据测试） | 校验工具/RAG 声明必须有真实回执或证据记录支撑。 |
| Final guard tests（最终保护器测试） | 校验最终回答不能泄漏内部过程文本或虚假工具声明。 |
| API/SSE tests（接口和事件流测试） | 校验用户可见 API 和事件流行为。 |
| Frontend mock scenarios（前端模拟场景） | 让前端在不调用真实 LLM/工具/RAG 的情况下验证进度、等待、产物和最终回答渲染。 |

### 12.3 必需后端测试

#### 12.3.1 MainAgentActionContractTest

必须覆盖：

- 每一种 `MainAgentAction`：
  - `FINAL`
  - `CREATE_ARTIFACT`
  - `UPDATE_ARTIFACT`
  - `RETRIEVE_RAG`
  - `CALL_TOOL`
  - `ASK_USER`
  - `PLAN`
  - `CONTINUE`
  - `REPAIR_FINAL`
  - `FAIL`
- 非法 JSON 拒绝。
- 安全情况下从 markdown 包裹中提取 JSON。
- 拒绝 `runStatus`、`nextState`、`loopIndex`、`trace`、`audit` 等生命周期字段。
- 拒绝所选 action 写入范围外的字段。
- 配置允许时执行有界修复。
- 修复次数耗尽后 fail closed（安全失败）。

#### 12.3.2 ContextPlannerContractTest

必须覆盖：

- 合法 `ContextPlannerOutput`。
- 非法 context level 拒绝。
- Runtime 校验时拒绝未知 artifact id。
- 明确歧义时返回 `NEEDS_USER_CLARIFICATION`。
- budget warning（预算警告）和 `CONTEXT_OVER_BUDGET`。
- planner 输出中不能包含原始 payload body（载荷正文）。

#### 12.3.3 RuntimeStateMachineTest

必须覆盖：

- 普通直接回答：

```text
CREATED -> RUNNING -> COMPLETED
```

- RAG 路径：

```text
PREPARING_CONTEXT -> PLANNING_CONTEXT -> CALLING_MAIN_NODE -> EXECUTING_RAG -> CALLING_MAIN_NODE -> VERIFYING_RAG -> VERIFYING_FINAL -> COMPLETED
```

- 带审批的工具路径：

```text
CALLING_MAIN_NODE -> PREPARING_TOOL -> WAITING_USER -> INVOKING_TOOL_RUNTIME -> VERIFYING_TOOL -> CALLING_MAIN_NODE -> VERIFYING_FINAL -> COMPLETED
```

- `ASK_USER` 暂停和恢复。
- pending input（挂起输入）取消和过期会安全结束当前 run。
- contract repair（契约修复）次数上限。
- final repair（最终回答修复）次数上限。
- tool retry（工具重试）次数上限。
- RAG retry（RAG 重试）次数上限。
- `MAX_LOOP_REACHED`。
- 不可恢复错误后的安全失败响应。

#### 12.3.4 ContextMaterializationTest

必须覆盖：

- `METADATA_ONLY` 不加载产物正文。
- `SUMMARY_ONLY` 只加载摘要。
- `SUMMARY_PLUS_SNIPPET` 加载有界片段。
- `FULL_TEXT` 只在预算内加载全文。
- `CHUNKED_CONTEXT` 只加载选中的 chunk（内容块）。
- 原始工具回执被摘要进 `evidencePack`。
- prompt、原始模型输出、trace payload 不会被加载进 `MainAgentStateView`。

#### 12.3.5 ArtifactContextPolicyTest

必须覆盖：

- 发布类任务对 MainAgentNode 只加载产物 id 和 metadata（元数据）。
- 改写类任务在预算允许时加载全文。
- 长产物改写使用 chunking（分块）。
- 模糊产物引用进入澄清路径。
- 产物 alias（别名）参与候选排序。

#### 12.3.6 ToolRuntimeAndVerificationTest

必须覆盖：

- `CALL_TOOL` 没有匹配 capability（能力）时不能执行。
- 高风险 capability 必须审批。
- 没有捕获真实 receipt（回执）时，不能接受 ToolRuntime 的成功声明。
- 捕获到真实回执且没有调用级错误时，通过执行证明验收。
- 回执明确报告调用级错误时，验收失败。
- `expectedOutcome` 作为 evidence context（证据上下文）保留，但在 MVP 不驱动强业务验收。
- tool summary（工具摘要）不能当作事实。
- 工具失败会作为 evidence（证据）交给下一轮 MainAgentNode。

#### 12.3.7 PendingInputResolutionTest

必须覆盖：

- 每个 pending input 都持久化 options、`answerContract` 和 continuation。
- 精确 `optionId` 匹配时，直接使用已保存的结构化 `option.value`，不调用 LLM。
- 用户自由文本会调用 `UserInputResolverNode`。
- 非法 `UserInputResolution` 会被 `ContractValidator` 拒绝。
- 用户取消会把 run 标记为 `CANCELLED` 或用户安全 `FAILED`。
- 上下文选择类 pending input 会恢复同一个 run，并实体化用户选择的上下文。
- 工具审批类 pending input 会恢复工具准备流程。

#### 12.3.8 RagExecutionAndVerificationTest

必须覆盖：

- `RETRIEVE_RAG` 创建 RAG query 记录。
- RAG hits（命中结果）变成 evidence。
- 要求知识库 grounding（基于知识库证据支撑）的最终回答必须使用 evidence，或者诚实说明没有找到相关资料。
- 声称基于知识库的回答必须有 evidence 支撑。
- citation（引用）必须指向合法且能支撑对应声明的 evidence id。
- 不相关 RAG evidence 在回答未声称基于知识库时可以 warning 通过。
- no-hit（无命中）结果不能导致知识库事实幻觉。
- grounded answer（有证据支撑的回答）通过验收。
- contradiction（矛盾）或 unsupported claim（无支撑声明）验收失败。

#### 12.3.9 FinalResponseGuardTest

必须覆盖：

- 空最终回答被拦截。
- 用户没有询问内部实现时，包含 node、Runtime、verifier、trace、contract、prompt 等内部词的回答被拦截。
- 非法 citation id 被拦截。
- 没有工具证据却声称工具成功的最终回答被拦截。
- 超长回答按策略拦截或修复。
- 合法最终回答通过。

#### 12.3.10 RepositoryBoundaryTest

必须覆盖：

- LLM node service class 不能直接依赖 repository，只能通过 Runtime 或 domain manager 间接使用。
- 普通消息 API 只读取用户可见消息。
- debug trace API 与普通 chat API 分离。
- 大 payload 字段通过 payload reference 保存。

该测试可以用 package dependency test（包依赖测试）或轻量 Spring context test 实现。

### 12.4 必需 API 和 SSE 测试

API/SSE 测试必须覆盖：

- `POST /agent/chat` 创建 run 和用户消息。
- `GET /agent/runs/{runId}` 返回 run 状态。
- `GET /agent/runs/{runId}/final` 只在完成后返回最终响应。
- `GET /agent/runs/{runId}/events` 返回用户可见事件历史。
- SSE emitter 按顺序推送用户可见事件。
- 普通事件 payload 不能包含原始节点输出、prompt、trace、verifier detail 或 raw receipt。
- `ASK_USER` 选项可以被获取和回答。
- 提交用户回答后恢复暂停的 run。
- artifact summary API 和 artifact content API 保持分离。
- debug API 必须是显式 debug endpoint，不能喂给普通 chat UI。

### 12.5 前端 Mock 场景

trigger 层必须提供 mock endpoint 或 mock mode，方便前端开发。

mock mode 不能调用真实 LLM、RAG 或 MCP 工具。

必需场景：

| 场景 | 目的 |
|---|---|
| `simple_final` | 直接回答，带进度和最终响应。 |
| `artifact_created` | 产物创建事件和产物面板更新。 |
| `rag_progress` | RAG 检索进度和基于证据的最终回答。 |
| `tool_publish_progress` | 工具审批、执行进度、验收和最终结果。 |
| `ask_user_confirm` | 确认/拒绝用户交互。 |
| `ask_user_choose_artifact` | 多选产物澄清。 |
| `tool_failed` | 工具失败后的用户安全失败或重试选项。 |
| `final_guard_repair` | 最终回答在用户可见前被修复。 |
| `context_over_budget` | 上下文压缩/分块进度，不暴露内部细节。 |
| `debug_trace` | debug 面板使用 debug endpoint，不使用普通事件流。 |

mock SSE 事件示例：

```json
[
  {
    "eventId": "evt_001",
    "runId": "run_mock_001",
    "seq": 1,
    "phase": "RECEIVED",
    "title": "Request received",
    "summary": "Started processing your request.",
    "status": "RUNNING",
    "artifactRefs": [],
    "pendingInputId": null,
    "createdAt": "..."
  },
  {
    "eventId": "evt_002",
    "runId": "run_mock_001",
    "seq": 2,
    "phase": "RAG_RETRIEVING",
    "title": "Searching knowledge base",
    "summary": "Searching the knowledge base.",
    "status": "RUNNING",
    "artifactRefs": [],
    "pendingInputId": null,
    "createdAt": "..."
  },
  {
    "eventId": "evt_003",
    "runId": "run_mock_001",
    "seq": 3,
    "phase": "COMPOSING",
    "title": "Preparing answer",
    "summary": "Preparing the answer.",
    "status": "RUNNING",
    "artifactRefs": [],
    "pendingInputId": null,
    "createdAt": "..."
  },
  {
    "eventId": "evt_004",
    "runId": "run_mock_001",
    "seq": 4,
    "phase": "COMPLETED",
    "title": "Completed",
    "summary": "Completed.",
    "status": "COMPLETED",
    "artifactRefs": [],
    "pendingInputId": null,
    "createdAt": "..."
  }
]
```

用户可见 mock message 必须干净、自然，不能暴露内部 action JSON。

### 12.6 测试数据

必需 deterministic fixtures（确定性测试夹具）：

- 一个没有历史记录的 session。
- 一个最近生成过 RAG 文章产物的 session。
- 一个有两个模糊文章产物的 session。
- 一个超过 `max-state-view-tokens` 的长产物。
- 一个成功的 CSDN-like 发布回执。
- 一个失败的发布回执。
- 一个缺少必需 `url` 的发布回执。
- 一个有命中的 RAG 查询。
- 一个无命中的 RAG 查询。
- 一个包含内部过程泄漏的最终回答。

fixtures 必须使用合成内容，不能依赖真实凭证或外部 endpoint。

### 12.7 测试执行策略

开发过程中：

- 针对改动边界运行 targeted tests（定向测试）。
- 除非改动共享 Runtime、contract 或 persistence，否则避免全量测试。
- 结构性重构后 compile 受影响模块。
- 使用 fake client 模拟 LLM 和工具行为。

MVP 完成前尽量执行：

```text
mvn -q -pl ai-agent-station-study-app -am -DskipTests=false test
```

如果全量测试太慢或依赖环境，运行必需 targeted tests，并在最终实现报告中说明跳过项和原因。

### 12.8 测试必须证明的安全性质

MVP 测试必须证明：

- 最终回答只来自 guarded `FinalResponse`。
- MainAgentNode 不能直接调用 MCP 工具。
- ToolRuntime 不能创建最终回答。
- 工具成功必须有真实回执。
- RAG 支撑的回答必须有 evidence。
- ContextPlanner 不能把原始 payload 直接注入 MainAgentNode。
- Runtime 拥有生命周期流转控制权。
- 节点输出不能写入其他组件的状态。
- 普通前端不能展示内部 harness payload。
- 上下文预算溢出有确定性恢复。

## 13. 实施计划

### 13.1 实施治理

本实现必须作为 staged refactor（分阶段重构）推进。

规则：

- 不要把旧 Node1-4 harness 修修补补成新设计。
- 不要通过 prompt-only workaround（只改提示词的绕路方案）实现新行为。
- 普通前端不能消费内部 debug payload。
- MainAgentNode 不能直接挂载 MCP 工具。
- 每个阶段尽量保持可编译。
- 阶段边界优先运行窄范围 targeted tests。
- 如果临时兼容需要，可以保留旧执行路径，但新代码必须进入新的 main-loop Runtime 包结构。

每个阶段结束时必须记录：

- 修改文件摘要。
- compile 或 targeted test 证据。
- 已知缺口。
- 下一阶段入口。

### 13.2 目标分支和迁移边界

实现继续基于当前重构分支，除非用户另建分支。

包根保持：

```text
yhx.com
```

旧 fixed harness 的类可以在新 Runtime 编译后删除、替换或隔离。

兼容规则：

- public API DTO 可以临时保留兼容字段。
- 旧内部 node 类不能继续作为语义权威。
- 旧 trace UI payload 不能通过新普通前端 API 暴露。

### 13.3 Phase 0：Spec 锁定和脚手架

目标：先建立骨架，让后续开发机械化。

任务：

1. 保持本文档为实现参考。
2. 添加第 8 章定义的包目录。
3. 添加 managers、repositories、contracts、node clients 的空接口或最小接口。
4. 添加 status、phase、action、event type、evidence type、payload type、context level、failure code 等 enum。
5. 添加 `auto-agent.runtime`、`auto-agent.context`、`auto-agent.nodes`、`auto-agent.capabilities` 配置属性类。

验收：

- 项目可编译。
- 旧 node 行为尚未改变。
- 包结构匹配第 8 章。

### 13.4 Phase 1：Domain Model 和 Contract Layer

目标：先让 Java contract 成为事实源，再写编排。

任务：

1. 实现领域值对象：
   - `AgentRun`
   - `AgentMessage`
   - `AgentState`
   - `MainAgentStateView`
   - `ContextPlannerInput`
   - `ContextPlannerOutput`
   - `MainAgentAction`
   - `ToolInvocationRequest`
   - `ToolInvocationResult`
   - `VerificationResult`
   - `FinalResponse`
2. 实现 enum：
   - `RunStatus`
   - `RuntimePhase`
   - `MainAgentActionType`
   - `StateDeltaField`
   - `ContextLevel`
   - `EvidenceType`
   - `ToolInvocationStatus`
   - `VerificationStatus`
   - `FailureCode`
   - `RecoveryAction`
3. 实现 `ContractRegistry`。
4. 实现 `RawOutputParser`。
5. 实现 `ContractValidator`。
6. 实现 `ContractRepairPolicy` 接口和固定 retry counter。
7. 为所有节点输出实现 JSON schema 或类型化校验规则。

验收：

- `MainAgentActionContractTest` 通过。
- `ContextPlannerContractTest` 通过。
- 非法生命周期字段被拒绝。
- 每个 action 都有明确允许的 `StateDelta` 字段。

### 13.5 Phase 2：Persistence 和 Repository Adapter

目标：创建 Runtime 所需存储，同时避免节点直接耦合数据库。

任务：

1. 添加或迁移第 7 章数据表。
2. 实现领域 repository 接口：
   - `IRunRepository`
   - `IConversationRepository`
   - `IMemoryRepository`
   - `IArtifactRepository`
   - `IEvidenceRepository`
   - `IToolRepository`
   - `IRagExecutionRepository`
   - `IEventTraceRepository`
   - `IPayloadRepository`
   - `INodePromptRepository`
3. 实现 infrastructure DAO 和 repository adapter。
4. 实现大字符串和 JSON 的 payload storage。
5. 为 run state、message、artifact、evidence、event 实现基础事务边界。

验收：

- 项目可编译。
- repository 接口在 domain，实现在 infrastructure。
- 长产物正文和原始回执通过 payload reference 保存。
- 普通 message repository 只返回用户可见消息。

### 13.6 Phase 3：Prompt Assembly 和 Node Invocation Pipeline

目标：所有 LLM 节点都走同一条调用路径。

任务：

1. 实现 `PromptAssembler`。
2. 实现 prompt layer builder：
   - `RolePromptProvider`
   - `OperatingContextBuilder`
   - `InputFieldGuideBuilder`
   - `TaskProcedureBuilder`
   - `DecisionPolicyBuilder`
   - `OutputContractBuilder`
   - `FewShotExampleProvider`
   - `AntiExampleProvider`
   - `OutputOnlyInstructionBuilder`
3. 实现 `NodeInvocationPipeline`。
4. 围绕 Spring AI `ChatClient` 实现 `NodeClient` 抽象。
5. 为测试实现 fake node client。
6. 将 `agent_node_prompt` 的角色/行为内容接入 prompt envelope。

验收：

- 每个 LLM 节点调用都经过 `NodeInvocationPipeline`。
- 数据库 prompt 不能覆盖 Java contract。
- contract repair 有界，并能在 trace 中观察。

### 13.7 Phase 4：Context 和 Artifact Runtime

目标：在完整 loop 前，先让上下文选择和产物复用可靠。

任务：

1. 实现 Java candidate preselection：
   - recent messages
   - summaries
   - artifact candidates
   - memory candidates
   - evidence candidates
2. 实现 `ContextPlannerNode` 调用。
3. 实现 `ContextBudgetManager`。
4. 实现 `ArtifactResolver`。
5. 实现 `ArtifactContextPolicy`。
6. 实现第 5.8 节 Runtime Context Materialization。
7. 实现产物创建、版本、别名和关系记录。
8. 实现 MVP 所需的记忆摘要和召回 stub。

验收：

- `ContextMaterializationTest` 通过。
- `ArtifactContextPolicyTest` 通过。
- 发布类任务对 MainAgentNode 只加载 metadata。
- 改写类任务按预算加载 full text 或 chunked context。

### 13.8 Phase 5：Runtime State Machine

目标：实现确定性的 Java 生命周期控制。

任务：

1. 实现 `RuntimeStateMachine`。
2. 实现 `AutoAgentRuntimeService`。
3. 实现 run 创建和 message 创建。
4. 实现 loop 编排：
   - prepare context
   - plan context
   - build state view
   - call main node
   - validate action
   - handle action
   - verify or guard
   - continue、wait、complete、fail 或 cancel
5. 实现 loop limit 和 retry counter。
6. 根据 `VerificationResult` 和 `FailureCode` 实现 recovery routing。
7. 实现安全失败响应。

验收：

- `RuntimeStateMachineTest` 通过。
- Runtime 控制所有 status 和 phase 变化。
- 节点不能写 lifecycle state。
- `MAX_LOOP_REACHED` 有确定性恢复。

### 13.9 Phase 6：MainAgentNode Actions

目标：逐个实现 action handler。

实现顺序：

1. `FINAL`
2. `CREATE_ARTIFACT`
3. `UPDATE_ARTIFACT`
4. `ASK_USER`
5. `RETRIEVE_RAG`
6. `CALL_TOOL`
7. `PLAN`
8. `CONTINUE`
9. `REPAIR_FINAL`
10. `FAIL`

每个 action 必须：

- 校验 StateDelta。
- 持久化允许的状态变更。
- 必要时发送用户可见事件。
- 写入 developer trace。
- 需要时调用 verifier 或 guard。
- 路由到下一个 Runtime phase。

验收：

- 所有 action 都被 contract test 覆盖。
- 不支持的字段被拒绝。
- 最终回答路径始终经过 guard。

### 13.10 Phase 7：RAG Runtime 和 Verification

目标：加入显式 RAG 检索和证据 grounding（基于证据）。

任务：

1. 实现 `RagRuntime`。
2. 持久化 RAG query 和 hits。
3. 将 hits 转为 evidence record。
4. 添加用户可见 RAG progress event。
5. 实现 MVP 语义 grounding 的 `RagVerifier`。
6. 添加 no-hit 和 contradiction 恢复处理。

验收：

- `RagExecutionAndVerificationTest` 通过。
- no-hit 路径不会编造知识库事实。
- 最终回答中使用的 RAG evidence id 合法。

### 13.11 Phase 8：Tool Runtime、MCP Execution 和 Verification

目标：实现工具使用，同时不污染 MainAgentNode。

任务：

1. 实现 `CapabilityRegistry`。
2. 从 yml 加载 MVP capability 默认值。
3. 实现风险和审批策略。
4. 实现 `ToolInvocationRequest` builder。
5. 实现 `ToolRuntime`，通过 `McpClientRegistry` 调用 Spring AI MCP Client，不引入额外 LLM 工具执行节点。
6. 捕获真实工具回执。
7. 持久化工具调用、审批、回执和验收结果。
8. 实现 `ToolVerifier`。
9. 将工具结果和失败转为 evidence。

验收：

- `ToolRuntimeAndVerificationTest` 通过。
- MainAgentNode 不挂载 MCP 工具。
- 工具成功必须有真实回执。
- 高风险工具等待用户审批。

### 13.12 Phase 9：FinalResponseGuard 和 Repair

目标：确保最终用户回答干净。

任务：

1. 实现 guard pipeline：
   - `EmptyAnswerGuard`
   - `InternalLeakGuard`
   - `FormatGuard`
   - `EvidenceReferenceGuard`
   - `ToolClaimGuard`
   - `LengthGuard`
2. 实现 `FinalResponseGuard`。
3. 使用 `REPAIR_FINAL` 实现最终回答修复调用。
4. 持久化 guard result。
5. 只有 guard 通过后才能创建最终 assistant message。

验收：

- `FinalResponseGuardTest` 通过。
- 普通最终回答不泄漏内部过程文本。
- 虚假工具成功声明被拦截。

### 13.13 Phase 10：API、SSE 和 Frontend Mock Mode

目标：通过干净前端边界暴露新 Runtime。

任务：

1. 实现第 8.5 节 controller。
2. 实现 chat API。
3. 实现 run status API。
4. 实现 final response API。
5. 实现 pending input API。
6. 实现 artifact API。
7. 在显式 debug path 和开关后实现 debug API。
8. 实现 `RunEventPublisher`。
9. 实现 SSE emitter event stream。
10. 实现第 12.5 节 mock API 和必需场景。

验收：

- API/SSE tests 通过。
- 普通事件不包含原始内部 payload。
- 前端无需真实 LLM/工具即可测试 ASK_USER 和 progress state。

### 13.14 Phase 11：旧 Harness 隔离和清理

目标：新 Runtime 可用后，移除或隔离旧行为。

任务：

1. 识别旧 Node1-4 类和旧 trace payload 路径。
2. 从普通 AutoAgent 执行中移除旧 route。
3. 只有明确需要对比或迁移时才保留旧类。
4. 删除与新 contract 冲突的旧 prompt contract 和 parser。
5. 更新文档引用，从旧 harness 指向新 main-loop Runtime。

验收：

- 普通 API 路径不能调用旧 Node1-4 flow。
- 旧 trace output 不能变成最终回答。
- compile 通过。

### 13.15 Phase 12：MVP 验证和审查

目标：证明实现已经达到 MVP 可用。

任务：

1. 运行必需 targeted tests。
2. 可行时运行 app module tests。
3. 手工执行前端 mock 场景。
4. 使用 fake client 手工执行：
   - 直接回答
   - 产物创建
   - 产物更新
   - RAG 回答
   - 带审批的工具发布
   - 模糊产物澄清
   - 最终回答修复
   - 上下文超预算
5. 记录已知缺口和 backlog 映射。

验收：

- 第 12.8 节关键安全性质得到证明。
- 用户可见输出干净。
- debug data 只通过 debug path 可见。

### 13.16 Codex Session 推荐实施顺序

推荐拆分：

1. Domain contracts 和 enums。
2. Repository interfaces、persistence tables 和 payload storage。
3. Prompt assembly 和 node invocation pipeline。
4. Context planning、materialization、artifact policy。
5. Runtime state machine skeleton。
6. MainAgentNode action handlers。
7. RAG runtime 和 verifier。
8. Tool execution node 和 verifier。
9. Final response guard 和 repair。
10. API/SSE 和 mock mode。
11. 清理旧 harness。
12. 最终 targeted verification。

后续如果使用 parallel worker（并行工作者），不能让两个 worker 写同一块代码。必须按模块或包拆分成互不冲突的写入范围。

## 14. Backlog

### 14.1 Backlog 治理

Backlog item（后续任务项）明确不属于 MVP，除非用户提升优先级。

Backlog 工作不能削弱 MVP 边界：

- Runtime 仍然是确定性的 Java 编排。
- MainAgentNode 仍然不能直接挂载 MCP 工具。
- 最终回答仍然经过 final guard。
- 内部 trace 仍然与普通前端隔离。

### 14.2 Context Planning 增强

未来工作：

- 超大任务的自动上下文规划。
- 工具执行前的长产物拆解。
- project-level code context planning（项目级代码上下文规划）。
- 产物改写的语义 chunk selection（语义分块选择）。
- 更强排序的跨 session memory recall（跨会话记忆召回）。
- 用户可配置的记忆保留和删除。
- run 执行前的上下文成本估算。
- 长 run 后的自适应摘要。

保留用户原始需求：

- 如果未来需要发布一篇很长的 CSDN 文章，但 prompt 已包含大量其他内容，系统应自动规划、拆分、压缩或重组上下文。

### 14.3 Subagent Scheduling（子智能体调度）

未来工作：

- 将 subagent capability 作为特殊 delegated node type（委派节点类型）。
- 支持 subagent task contract。
- 支持 subagent result evidence。
- 支持父 Runtime 监督委派工作。
- 为委派进度添加前端事件。
- 高风险委派前要求用户审批。

该项不属于 MVP。

### 14.4 Coding Agent Capability（编程智能体能力）

未来工作：

- file-system MCP capability 集成。
- 项目扫描和代码上下文规划。
- edit proposal（修改提案）和用户审批流。
- 破坏性文件操作审批策略。
- patch application（补丁应用）和验证循环。
- test command planning（测试命令规划）。
- code review guard（代码审查保护器）。
- patch 和生成文件的 artifact model。

这应该作为后续 capability family（能力族）设计，不能硬编码进 MVP Runtime。

### 14.5 Skill 和 Capability Marketplace

未来工作：

- skill registry（技能注册表）。
- skill metadata 和 activation rules。
- skill-specific prompt overlays（技能专用提示词覆盖层）。
- skill-specific tools 和 verifiers。
- skill 配置管理 UI。
- 数据库驱动的 capability 配置。
- 每个 agent 动态启用/禁用 capability。

### 14.6 Advanced Guardrails（高级护栏）

未来工作：

- 基于 LLM 的最终质量 guard。
- 基于 LLM 的安全和策略 guard。
- 敏感词和合规 guard。
- 平台特定发布策略 guard。
- 用户定义输出风格 guard。
- citation quality guard（引用质量保护器）。
- 跨记忆和新证据的 contradiction guard。

MVP 保持 `FinalResponseGuard` 为 Java 规则型。

### 14.7 Prompt 和 Contract 管理 UI

未来工作：

- prompt version management UI。
- prompt diff 和 rollback。
- contract version dashboard。
- node prompt test playground。
- prompt evaluation dataset。
- prompt activation audit。
- 将 yml 默认值迁移到托管配置。

即使存在 UI，Java contract 仍然是事实源。

### 14.8 Observability 和 Debug UI

未来工作：

- run timeline debug panel。
- token 和 cost dashboard。
- context budget visualization。
- trace filtering。
- verifier result inspector。
- 带脱敏的 payload viewer。
- 单节点 latency dashboard。
- 使用 fake node client 重放失败 run。

普通前端仍然与 debug data 隔离。

### 14.9 Distributed Runtime 和 Reliability

未来工作：

- async run queue（异步运行队列）。
- run cancellation（运行取消）。
- run timeout policy。
- resumed run 的 distributed lock（分布式锁）。
- idempotent event emission（幂等事件发送）。
- 进程重启后的恢复。
- 外部工具失败的延迟重试。
- 失败工具调用的 dead-letter storage（死信存储）。

### 14.10 RAG 增强

未来工作：

- hybrid retrieval（混合检索）。
- reranking（重排序）。
- metadata filtering UI。
- source citation rendering（来源引用渲染）。
- 文档摄入进度事件。
- 每个 agent 的知识库 scope。
- RAG evaluation dataset。
- contradiction-aware retrieval（感知矛盾的检索）。

### 14.11 Tool Capability 增强

未来工作：

- capability metadata 存储到 MySQL。
- MCP tool binding 管理 UI。
- capability health check。
- tool result schema inference support（工具结果 schema 推断支持）。
- tool dry-run mode。
- 高风险 action 审批模板。
- 每个 agent 的 tool permission profile。
- tool receipt redaction（工具回执脱敏）。

### 14.12 前端增强

未来工作：

- ASK_USER 用户选择卡片。
- artifact side panel（产物侧边栏）。
- debug mode toggle。
- run timeline visualization。
- retry 和 cancel 控件。
- pending approval banner。
- SSE reconnect behavior。
- 开发用 mock scenario selector。

### 14.13 Test 和 Evaluation 增强

未来工作：

- golden scenario suite。
- fake LLM behavior library。
- prompt regression evaluation。
- tool verifier scenario corpus。
- context planner ranking evaluation。
- frontend visual regression for mock scenarios。
- long context run performance benchmark。

### 14.14 Migration Backlog

未来工作：

- 如有需要，将旧 trace log 迁移到新 debug trace model。
- 如有需要，为旧 API 消费方提供 compatibility adapter。
- 归档旧 Node1-4 prompt data。
- 记录 breaking changes。
- 迁移窗口结束后删除废弃数据库列。

### 14.15 明确非目标

除非用户明确改变范围，否则不能添加：

- MainAgentNode 直接挂载 MCP 工具。
- 普通模式前端展示 raw node output。
- 通过 prompt-only 方式定义 Runtime protocol。
- 无界自动重试。
- 把所有 run state 存进一个非结构化 dynamic context 字段。
- 把模型摘要当作工具回执。
- 把 verifier summary 当作最终回答。

## 15. 本轮一致性审查修正

本节用于同步英文正式 spec 的最新一致性修正，避免中文审查时沿用旧理解。英文正式 spec 仍然是后续代码实现的唯一参考。

### 15.1 ToolVerifier 职责收窄

MVP 的 `ToolVerifier` 是 Java 确定性验收器，只证明工具真实调用过，并且存在基础回执事实。它不验收完整业务目标是否完成。

必须验收：

- 真实 `ToolReceipt` 存在。
- MCP/Spring AI 工具框架捕获到了真实工具调用。
- 被调用的 MCP server/tool 必须匹配解析后的 capability 和 McpToolRegistry。
- 高风险工具调用前已有用户审批。
- 回执没有明确调用级错误。

不做：

- 不强制每个工具配置 `successSignals`。
- 不强制每个工具配置 `requiredResultFields`。
- 不使用 LLM 判断工具业务是否完成。

复杂工具业务验收放入 Backlog。

### 15.2 Pending Input 统一模型

所有用户问答统一落 `agent_pending_input`：

- 普通澄清。
- 上下文选择。
- 工具审批。
- 用户自由输入。
- 工具执行中需要用户侧动作。
- RAG/最终回答保护器需要用户确认的情况。

`WAITING_USER` 表示同一个 run 暂停，而不是创建新 run 或重置流程。用户取消、拒绝、超时或不回答时，当前 run 默认终止为 `CANCELLED` 或用户安全 `FAILED`。

### 15.3 用户回答解析

每个 pending input 必须保存：

- `sourceComponent`
- `pendingType`
- `inputMode`
- structured `options`
- `answerContract`
- `continuation`

如果用户点击选项，Runtime 直接读取该 option 的结构化 `value`。如果用户输入自由文本，Runtime 调用 `UserInputResolverNode`，按 `answerContract` 解析成结构化 `UserInputResolution`，再通过 continuation handler 恢复同一个 run。

### 15.4 ContextPlannerStatus Handling

`ContextPlannerNode` 不控制生命周期，但 Runtime 必须处理它的 status：

- `READY`：按 context level 实体化上下文并调用 MainAgentNode。
- `NO_RELEVANT_CONTEXT`：构建最小 StateView 后调用 MainAgentNode。
- `NEEDS_USER_CLARIFICATION`：创建 `agent_pending_input` 并进入 `WAITING_USER`。
- `CONTEXT_OVER_BUDGET`：先压缩/分块，仍失败则询问用户缩小范围。
- `FAILED`：能安全降级就用最小 StateView，否则安全失败。

修改、润色、重写、结构调整等任务必须允许 `FULL_TEXT` 给 MainAgentNode；超预算时才分块或询问用户。

### 15.5 RagVerifier 修正

`RagVerifier` 是 LLM verifier node，用于检查 grounding honesty，而不是强制每次 RAG 都必须被使用。

它要阻止：

- 用户要求基于知识库回答，但最终回答没有使用证据也没有说明无资料。
- 回答声称来自知识库/文档/检索结果，但没有证据支撑。
- citation 引用不存在或不能支撑对应声明。
- 回答与 RAG evidence 矛盾。

如果 RAG evidence 不相关，且最终回答没有声称基于知识库，可以通过但记录 warning。

### 15.6 finalAnswerCandidate 统一保护

任何要返回给用户的 `finalAnswerCandidate` 都必须走 `FinalResponseGuard`。这包括：

- `FINAL`
- `REPAIR_FINAL`
- `CREATE_ARTIFACT`
- `UPDATE_ARTIFACT`

artifact 正文落库为 artifact，不等于聊天最终回答。

### 15.7 UserVisibleEvent 统一

真实 SSE 和 mock SSE 都使用同一个 `UserVisibleEvent` DTO：

```json
{
  "eventId": "evt_001",
  "runId": "run_001",
  "seq": 1,
  "phase": "RAG_RETRIEVING",
  "title": "Searching knowledge base",
  "summary": "Searching the knowledge base.",
  "status": "RUNNING",
  "artifactRefs": [],
  "pendingInputId": null,
  "createdAt": "..."
}
```

不再使用 `eventType/message` 另一套结构。

### 15.8 Prompt 表补入持久化

`agent_node_prompt` 属于正式持久化表。`agent_id=GLOBAL` 表示全局默认 prompt。该表只保存 role、behavior、style、business、repair prompt，不保存 Java contract schema、解析规则、runtime 路由或状态写入范围。

### 15.9 CapabilityRegistry 和 RAG

MVP 的 `CapabilityRegistry` 只管理外部工具能力。RAG 是 `RagRuntime` 内置能力，通过 `auto-agent.rag` 配置。未来如果需要多 RAG provider，再把 RAG provider capability 放入 Backlog。
