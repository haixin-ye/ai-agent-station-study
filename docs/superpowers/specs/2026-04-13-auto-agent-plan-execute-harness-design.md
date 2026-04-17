# Auto Agent Plan-and-Execute Harness 重构设计

## 一、背景

当前 auto-agent 的执行链路是四节点循环：

- `Node1 -> Node2 -> Node3 -> Node1 ... -> Node4`

这个拓扑本身可以保留，问题不在节点数量，而在于节点之间的契约太弱，过度依赖自然语言总结，缺少对下列几个环节的清晰边界：

- 规划
- 执行
- 验证
- 最终交付

当前最典型的故障是“假成功”：

- `Node2` 可能没有真正调用 MCP，或者调用失败，但仍然输出“已完成”。
- `Node3` 可能把自然语言中的“完成”误当作真实完成。
- `Node4` 可能基于前面未验证的内容，生成一个看起来很完整、但事实错误的最终回答。

因此，这次重构不是增加补丁规则，而是把现有四节点架构重构为一套更稳定的：

- `带验证层与最终汇总层的 Plan-and-Execute Harness`

## 二、目标

本次重构目标如下：

- 保留现有四节点执行拓扑。
- 让 `Node1` 成为唯一的规划者与调度者。
- 让 `Node2` 成为唯一的实际执行者。
- 让 `Node3` 成为唯一的验收入口。
- 让 `Node4` 只基于已验收成果生成最终回答。
- 将运行时能力装配与业务编排状态彻底分离。
- 用结构化状态对象替代自由文本式的节点间交接。
- 消除“工具并未成功执行，但系统认为已经完成”的问题。

## 三、非目标

本次重构不做以下事情：

- 不重构 Spring AI 的 client 装配机制。
- 不把 MCP、advisor、RAG 这些运行时能力对象直接塞进 `DynamicContext` 作为业务流转字段。
- 不新增新的运行时节点。
- 不改变既有物理执行拓扑。
- 不允许 `Node3` 直接跳转到 `Node2`。

## 四、总体原则

系统必须明确区分两层：

### 4.1 运行时能力层

这部分由 Spring AI 和数据库配置完成自动装配，包括：

- chat client
- MCP tools
- advisors
- RAG
- memory

这些属于“环境能力”，节点可以在运行时使用它们，但它们本身不是业务状态，不属于节点之间要交换的核心数据。

### 4.2 业务编排层

这部分才是真正由 harness 管理的数据，例如：

- 会话目标
- 总体计划
- 当前轮任务
- 执行记录
- 验证结果
- 已验收成果
- 总体完成状态

这部分应由 `DynamicContext` 承载。

## 五、目标执行形态

物理拓扑保持不变：

- `Node1 -> Node2 -> Node3 -> Node1 ... -> Node4`

语义职责定义如下：

- `Node1`：首轮总规划 + 每轮派工
- `Node2`：本轮执行
- `Node3`：本轮验证 + 下一轮指令生成
- `Node4`：最终结果汇总

这意味着当前架构可以定义为：

- `Plan-and-Execute with Verification and Final Composition`

也可以理解为：

- `带验收层的多轮计划执行架构`

## 六、Node 职责设计

### 6.1 Node1

`Node1` 具有双身份，但仍是同一个节点。

#### 模式 A：Bootstrap Planner

触发条件：

- 会话刚开始
- `roundIndex = 1`
- `masterPlan` 为空

职责：

- 理解用户原始问题
- 判断任务是简单任务还是复杂任务
- 生成整个任务的主步骤列表
- 定义每个主步骤的完成标准
- 产出第一轮的 `currentRound`

#### 模式 B：Round Planner

触发条件：

- 非首轮，或者 `masterPlan` 已存在

职责：

- 读取上一轮 `Node3` 的验证结果
- 查看 `taskBoard`、`roundArchive`、`overallStatus`
- 判断应该继续推进同一个主步骤，还是进入下一个主步骤
- 重新生成当前轮的 `currentRound`

约束：

- `Node1` 是唯一规划者与调度者
- `Node1` 不负责具体执行
- `Node1` 不声明执行是否成功
- `Node1` 不能替 `Node2` 编写具体 MCP 参数 JSON
- `Node1` 只能给出任务目标、推进策略、建议工具和期望证据

### 6.2 Node2

`Node2` 是唯一执行者。

职责：

- 读取 `Node1` 为当前轮生成的 `currentRound`
- 结合用户原始问题、advisor 注入内容、RAG 内容进行执行
- 根据当前轮任务自行决定是否调用 MCP、调用哪个 MCP、如何构造参数
- 输出本轮执行过程与候选结果
- 记录本轮真实的工具调用日志

约束：

- `Node2` 只对当前轮任务负责，不做全局规划
- `Node2` 可以使用工具，但是否使用、如何使用，由它自己决定
- `Node2` 的自然语言描述不是事实真相源
- `Node2` 必须输出执行记录，但最终是否采信由 `Node3` 决定

### 6.3 Node3

`Node3` 是唯一验收节点。

职责：

- 读取本轮任务要求
- 读取 `Node2` 的执行结果与真实工具记录
- 判断本轮任务是否完成
- 判断当前主步骤是否完成
- 判断总体任务是否完成
- 将可信成果写入 `acceptedResults`
- 输出下一轮给 `Node1` 的指令

约束：

- `Node3` 不能直接把流程送回 `Node2`
- `Node3` 只能给出验证结论和下一轮指令
- 只有 `Node3` 可以把结果提升为“已验收成果”
- 缺乏真实工具证据时，不能放行为完成

### 6.4 Node4

`Node4` 是最终汇总节点。

职责：

- 读取用户原始输入
- 读取归一化后的目标定义
- 读取 `acceptedResults`
- 读取 `taskBoard`
- 读取 `overallStatus`
- 生成面向用户的最终回答

约束：

- `Node4` 可以参考原始问题决定回答方式
- `Node4` 不能重新自由推理事实
- `Node4` 的事实来源只能是已验收成果
- 如果任务部分完成或失败，必须明确说明未完成项和原因

## 七、DynamicContext 结构设计

新的 `DynamicContext` 不再是杂项文本容器，而应是一个结构化编排状态容器。建议至少包含以下部分。

### 7.1 sessionGoal

用于保存本次会话级目标。

字段建议：

- `rawUserInput`：用户原始提问
- `sanitizedGoal`：系统归一化后的目标
- `successCriteria`：成功标准
- `maxRounds`：最大轮次
- `failurePolicy`：失败策略

说明：

- 会话开始时初始化
- 整个会话期间保持稳定
- `Node4` 需要读取其中的 `rawUserInput`

### 7.2 masterPlan

用于保存总体任务主步骤。

字段建议：

- `planVersion`
- `mainSteps[]`

每个 `mainStep` 建议包含：

- `stepId`
- `title`
- `goal`
- `completionCriteria`
- `status`
- `dependencies[]`

说明：

- 首轮由 `Node1` 创建
- 采用“混合模式”：主步骤总体稳定，但每个主步骤下的子任务可在每轮动态调整

### 7.3 taskBoard

用于按任务项维度维护执行状态。

每个 `stepId` 对应一条记录，建议包含：

- `status`
- `lastRoundTask`
- `acceptedOutputs[]`
- `lastFailureReason`
- `attemptCount`

说明：

- 这是按主步骤组织的主视图
- `Node1`、`Node3`、`Node4` 都会依赖它

### 7.4 currentRound

用于描述当前轮任务。

字段建议：

- `roundIndex`
- `currentStepId`
- `roundTask`
- `suggestedTools[]`
- `plannerNotes`
- `expectedEvidence`

说明：

- 这是 `Node2` 的唯一合法任务入口
- `Node2` 应只围绕这里定义的任务执行

### 7.5 roundArchive

用于按轮次存档执行快照。

每轮建议保存：

- `node1PlanSnapshot`
- `node2ExecutionSnapshot`
- `node3VerificationSnapshot`

说明：

- 用于调试、回放和前端 trace 展示
- 这是按轮组织的审计视图

### 7.6 toolExecutionLog

用于记录真实的工具调用事实。

每条记录建议包含：

- `roundIndex`
- `stepId`
- `toolName`
- `requestPayload`
- `responsePayload`
- `normalizedOutcome`
- `success`
- `errorType`
- `errorMessage`
- `timestamp`

说明：

- 这是 `Node3` 判断工具是否真的成功的核心依据
- 不能用自然语言摘要代替这一层

### 7.7 acceptedResults

用于保存通过 `Node3` 验收的成果。

每条记录建议包含：

- `stepId`
- `resultType`
- `content`
- `evidenceRefs`
- `acceptedByRound`
- `acceptedReason`

说明：

- 只有 `Node3` 可以写入这里
- `Node4` 只能使用这里作为事实来源

### 7.8 overallStatus

用于保存总任务状态。

字段建议：

- `state`
- `completedSteps`
- `remainingSteps`
- `blockedReasons`
- `finalDecision`

说明：

- 决定是否继续下一轮，还是进入 `Node4`

## 八、双轨存档原则

本次设计采用双轨存档：

### 8.1 按轮存档

通过 `roundArchive` 保留每一轮的快照，解决“过程黑盒”的问题。

### 8.2 按任务项存档

通过 `taskBoard` 和 `acceptedResults` 保留每个主步骤的真实推进结果，解决“最终回答没有事实锚点”的问题。

这两种视图必须同时存在，不能只保留一种。

## 九、节点间输入输出契约

### 9.1 Node1 输入

- `sessionGoal`
- `masterPlan`
- `taskBoard`
- `roundArchive`
- `overallStatus`
- 上一轮 `Node3` 的验证结论与下一轮指令

### 9.2 Node1 输出

首轮输出：

- `masterPlan`
- 首轮 `currentRound`

后续每轮输出：

- 新的 `currentRound`
- 当前选择推进的 `stepId`
- 本轮期望证据与建议工具

### 9.3 Node2 输入

- `currentRound`
- `sessionGoal.rawUserInput`
- `sessionGoal.sanitizedGoal`
- advisor/RAG 自动注入的内容
- 运行时已装配的 MCP 工具

### 9.4 Node2 输出

- `node2ExecutionSnapshot`
- `toolExecutionLog` 增量
- 候选结果内容

### 9.5 Node3 输入

- `currentRound`
- `node2ExecutionSnapshot`
- `toolExecutionLog`
- `masterPlan` 中当前步骤的完成标准
- `taskBoard`
- `sessionGoal`

### 9.6 Node3 输出

- `node3VerificationSnapshot`
- `acceptedResults` 增量
- `taskBoard` 更新
- `overallStatus` 更新
- `nextRoundDirective`

### 9.7 Node4 输入

- `sessionGoal.rawUserInput`
- `sessionGoal.sanitizedGoal`
- `acceptedResults`
- `taskBoard`
- `overallStatus`

### 9.8 Node4 输出

- 最终用户回答

## 十、Node1 Prompt 设计

`Node1` 的 prompt 应按模式切换。

### 10.1 Bootstrap 模式

职责：

- 理解原始问题
- 判断任务复杂度
- 构建 `masterPlan`
- 生成第一轮 `currentRound`

输出应至少包括：

- `planDecision`
- `mainSteps`
- `currentStepId`
- `roundTask`
- `suggestedTools`
- `expectedEvidence`

### 10.2 Round Planner 模式

职责：

- 读取上轮验证结果
- 决定继续当前主步骤还是切换到下一主步骤
- 为当前轮生成新的 `roundTask`

输出建议包括：

- `planDecision`
- `currentStepId`
- `roundTask`
- `suggestedTools`
- `expectedEvidence`
- `replanReason`

说明：

- 这里的 `suggestedTools` 只是建议，不是强约束
- 不应把具体 MCP 参数写死在 `Node1`

## 十一、Node2 Prompt 设计

`Node2` 的 prompt 应被刻意收窄为“执行当前轮任务”。

要求：

- 只围绕 `currentRound.roundTask` 工作
- 可以参考原始用户问题和 advisor/RAG 注入内容
- 是否调用工具、调用哪个工具、参数如何构造，由 `Node2` 自主决定
- 应输出执行说明与候选成果
- 工具调用事实应通过真实执行记录沉淀，而不是只靠口头描述

建议输出结构：

- `executionNarrative`
- `candidateOutputs`
- `toolIntentSummary`

说明：

- 真正的事实依据仍以 `toolExecutionLog` 为准

## 十二、Node3 Prompt 设计

`Node3` 应从“质量点评节点”转为“验收节点”。

它必须回答三个问题：

- 当前轮任务是否完成
- 当前主步骤是否完成
- 总任务是否完成

建议输出结构：

- `roundDecision`
- `stepDecision`
- `overallDecision`
- `acceptedResultsDelta`
- `issues`
- `nextRoundDirective`

其中：

- `acceptedResultsDelta` 决定哪些内容可以进入已验收成果
- `nextRoundDirective` 决定 `Node1` 下一轮如何规划

## 十三、Node4 Prompt 设计

`Node4` 的输入必须严格受限。

允许输入：

- 用户原始问题
- 归一化后的目标
- 已验收成果
- 任务板状态
- 总体完成状态

禁止输入：

- 未经验证的 `Node2` 自述结论
- 原始执行阶段的自由文本总结作为事实来源

输出应覆盖三种情况：

- `success summary`
- `partial completion summary`
- `failure summary`

其中：

- 表达方式可以参考原始用户问题
- 事实内容只能来自已验收成果

## 十四、路由与状态推进设计

虽然物理拓扑固定，但语义状态应显式化。

### 14.1 物理拓扑

- `Root -> Node1`
- `Node1 -> Node2`
- `Node2 -> Node3`
- `Node3 -> Node1 或 Node4`
- `Node4 -> complete`

### 14.2 Node3 不可直接重试执行

由于系统拓扑限制，`Node3` 不能直接跳回 `Node2`。

因此，“重试”必须重新定义为：

- `Node3` 只给出下一轮指令
- `Node1` 在下一轮读取该指令
- 由 `Node1` 重新派发给 `Node2`

### 14.3 nextRoundDirective 设计

建议支持以下指令：

- `REPLAN_SAME_STEP`
- `ADVANCE_NEXT_STEP`
- `FINISH_SUCCESS`
- `FINISH_PARTIAL`
- `FINISH_FAILED`

语义如下：

- `REPLAN_SAME_STEP`：当前步骤未完成，下一轮继续由 `Node1` 规划同一步骤
- `ADVANCE_NEXT_STEP`：当前步骤已完成，下一轮切换到下一个主步骤
- `FINISH_SUCCESS`：总体任务完成，进入 `Node4`
- `FINISH_PARTIAL`：任务部分完成或达到最大轮次，进入 `Node4`
- `FINISH_FAILED`：无法继续推进，进入 `Node4`

## 十五、工具真实性模型

为避免“工具假成功”，工具相关数据建议拆为三层：

### 15.1 toolIntent

表示 `Node2` 计划调用什么工具、预期完成什么目标。

说明：

- 这是执行意图，不是真实事实

### 15.2 toolExecutionRecord

表示工具真实调用记录。

每条应包含：

- `toolName`
- `requestPayload`
- `responsePayload`
- `success`
- `errorType`
- `errorMessage`
- `timestamp`

说明：

- 这是工具是否真的成功执行的事实来源

### 15.3 acceptedResult

表示通过 `Node3` 验收后，被提升为事实成果的内容。

说明：

- 只有这一层才允许 `Node4` 采信

## 十六、通用错误分类

建议统一错误分类，便于 `Node3` 验证和 `Node1` 下一轮规划：

- `TOOL_NOT_CALLED`
- `TOOL_CALL_FAILED`
- `TOOL_OUTPUT_INVALID`
- `EVIDENCE_MISSING`
- `STEP_NOT_COMPLETE`
- `OVERALL_NOT_COMPLETE`

语义如下：

- `TOOL_NOT_CALLED`：本轮本应依赖工具，但没有真实工具调用记录
- `TOOL_CALL_FAILED`：工具调用报错或明确失败
- `TOOL_OUTPUT_INVALID`：工具执行成功，但输出不满足本轮目标
- `EVIDENCE_MISSING`：有结论，但缺少足够证据
- `STEP_NOT_COMPLETE`：本轮推进了一部分，但当前主步骤未完成
- `OVERALL_NOT_COMPLETE`：当前步骤可能完成，但总任务尚未完成

## 十七、Node3 验收规则

`Node3` 不应再做模糊的“像不像完成了”的判断，而应遵循如下顺序：

1. 当前轮任务是否要求工具支撑的证据
2. 如果要求工具证据，是否存在对应 `toolExecutionRecord`
3. 该工具记录是否成功
4. 工具结果是否满足 `expectedEvidence` 与 `completionCriteria`
5. 满足时才允许写入 `acceptedResults`
6. 否则只能输出继续规划或结束失败类指令

这一步是整个重构中最关键的稳定性锚点。

## 十八、Node4 回答原则

`Node4` 的回答必须同时满足两个要求：

### 18.1 形式对齐用户

通过读取：

- `sessionGoal.rawUserInput`

决定回答方式、语气和交付形式。

### 18.2 事实严格受限

通过读取：

- `acceptedResults`
- `taskBoard`
- `overallStatus`

决定真正能说什么。

因此：

- `rawUserInput` 决定“怎么说”
- `acceptedResults` 决定“能说什么”

## 十九、测试策略

为了避免这次重构后继续黑盒运行，测试建议分三层。

### 19.1 Node Contract Tests

验证各节点输入输出契约：

- `Node1` 是否能生成 `masterPlan/currentRound`
- `Node2` 是否只消费 `currentRound`
- `Node3` 是否只把已验证成果写入 `acceptedResults`
- `Node4` 是否只依赖已验收成果生成回答

### 19.2 State Transition Tests

验证状态推进：

- 是否正确经过 `Node1 -> Node2 -> Node3`
- 工具失败时是否回到 `Node1` 进行同一步骤重规划
- 当前步骤完成时是否正确推进下一步骤
- 总任务完成时是否正确进入 `Node4`

### 19.3 Tool Truth Tests

专门验证工具真实性：

- `Node2` 口头说成功，但没有工具记录时，`Node3` 必须判失败
- 工具有记录但 `success=false` 时，`Node3` 必须判失败
- 工具成功且证据满足条件时，才能写入 `acceptedResults`

## 二十、推荐落地顺序

推荐实现顺序如下：

1. 重构 `DynamicContext` 对象结构
2. 引入显式的轮次状态与总体状态对象
3. 先重写 `Node3`，让它成为唯一验收入口
4. 再重写 `Node1`，让它成为总规划者和每轮派工者
5. 再重写 `Node2`，让它只负责当前轮执行并沉淀真实工具记录
6. 最后重写 `Node4`，让它只基于已验收成果生成最终回答
7. 最后补齐前端 trace 映射与日志展示

这个顺序的原因是：

- 先把“真相来源”和“验收权”立住
- 再去改规划和执行
- 这样可以避免继续出现“流程看起来正确，但事实来源仍然不可信”的问题

## 二十一、最终结论

本次重构不应继续走“按功能打补丁”的路线，而应把现有四节点 harness 统一收敛为一套稳定的、明确分工的 Plan-and-Execute 变体：

- `Node1`：总规划 + 每轮派工
- `Node2`：当前轮执行
- `Node3`：验收与下一轮指令生成
- `Node4`：最终交付

同时通过以下原则保证稳定性：

- 运行时能力层与业务编排层分离
- `DynamicContext` 结构化
- 工具调用事实显式记录
- 已验收成果与候选成果分离
- `Node4` 只基于已验收成果回答
- `Node3` 不直接重试执行，而是通过指令驱动 `Node1` 重新派工

这套设计既保留了你当前项目的四节点外形，也把它实质上升级为一套更稳的 `Plan-and-Execute with Verification and Final Composition` 架构。
