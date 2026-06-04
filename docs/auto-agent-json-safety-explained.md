# Auto-Agent 项目中如何保证模型输出 JSON 合法、可用，以及异常时不把 Agent 直接拖死

## 1. 问题本质：为什么多节点 Agent 很容易被“坏 JSON”拖死

先把问题说透。

一个多节点 Agent，通常会有这样的执行链路：

1. 第一个节点负责规划
2. 第二个节点负责执行
3. 第三个节点负责验证
4. 第四个节点负责整理最终结果

如果这些节点之间交换信息时，完全依赖大模型直接输出的 JSON 文本，那么系统会面临几个非常典型的风险：

- 模型可能不按要求输出 JSON，而是输出自然语言
- 模型可能输出半截 JSON，前面对、后面断掉
- 模型可能输出字段名不对，或者字段缺失
- 模型可能语法上是合法 JSON，但语义上是错的
- 模型可能“口头说”自己调用了工具，但实际上根本没调
- 模型可能把上一轮内容编造出来，导致后面节点基于假信息继续执行

最糟糕的情况是：后面的节点把这些输出当成“可信输入”继续处理。这样一来，就会出现两类严重问题：

- 解析异常，流程直接中断
- 逻辑异常，流程不报错但沿着错误状态继续跑，最后得到错误结果

所以，这类系统真正要解决的，不只是“让模型输出 JSON”，而是下面这句话：

**不能把模型原始输出文本直接当成系统状态。**

这也是这个项目设计保障机制的核心出发点。

---

## 2. 项目真实执行链路：Root / Node1 / Node2 / Node3 / Node4

这个项目的 Auto-Agent 执行链，核心是一个四节点闭环：

1. `RootNode`
2. `Step1AnalyzerNode`，也就是 Node1，负责规划
3. `Step2PrecisionExecutorNode`，也就是 Node2，负责执行
4. `Step3QualitySupervisorNode`，也就是 Node3，负责验收和决策
5. `Step4LogExecutionSummaryNode`，也就是 Node4，负责总结输出

执行入口在：

- [AutoAgentExecuteStrategy.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/AutoAgentExecuteStrategy.java:37)

初始化执行链和上下文的地方在：

- [DefaultAutoAgentExecuteStrategyFactory.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/factory/DefaultAutoAgentExecuteStrategyFactory.java:52)

整个流程可以先理解成下面这样：

1. 用户发起请求
2. Root 初始化本次执行上下文
3. Node1 生成当前轮计划
4. Node2 根据当前轮计划真实执行
5. Node3 判断这轮是否真的完成
6. 如果没完成，就回到 Node1 重规划
7. 如果完成，进入 Node4 汇总输出

这里最关键的一点是：

**节点之间的“主信息通道”不是模型输出的原始 JSON 字符串，而是 Java 里的结构化上下文对象。**

这点非常重要，因为它直接决定了系统的稳定性上限。

---

## 3. 这套系统真正依赖什么交换信息：不是裸 JSON，而是 `DynamicContext + VO`

很多人一听“多节点交换信息”，会默认以为：

- Node1 输出一段 JSON
- Node2 把这段 JSON 整段读进去
- Node3 再继续读下一段 JSON

但这个项目不是这么做的。

它的核心载体是一个结构化上下文对象：

- [DefaultAutoAgentExecuteStrategyFactory.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/factory/DefaultAutoAgentExecuteStrategyFactory.java:52)

里面放了很多强类型字段，比如：

- `currentRound`
- `currentStepPlan`
- `taskBoard`
- `roundArchive`
- `toolExecutionLog`
- `acceptedResults`
- `roundExecutionSummary`
- `overallStatus`
- `nextRoundDirective`

这些字段对应的不是一坨字符串，而是 Java VO，比如：

- [StepExecutionPlanVO.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/model/entity/StepExecutionPlanVO.java:1)
- [RoundExecutionSummaryVO.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/model/entity/RoundExecutionSummaryVO.java:1)

所以，正确理解这个项目的方式是：

**模型输出 JSON，只发生在“模型和系统的边界”；系统内部节点流转，依赖的是结构化状态，而不是继续传递裸文本。**

这就把问题拆成了两层：

1. 如何把模型输出安全地转成结构化状态
2. 如何保证后续节点只消费“经过整理和校验后的状态”

后面的保障措施，基本都是围绕这两层展开的。

---

## 4. 六层保障机制

下面按从前到后的顺序，完整讲这套系统是怎么做保障的。

### 4.1 第一层：先用 Prompt 对输出格式做硬约束

第一层保障，是告诉模型：你必须输出什么格式。

例如 Node1 的规划 prompt，会明确写出：

- 返回 exactly one JSON object
- 必须有哪些字段
- 字段之间有哪些约束关系

对应代码在：

- [Step1AnalyzerNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step1AnalyzerNode.java:222)

Node3 的验证 prompt 也一样，会要求只返回一个 JSON 对象，并明确输出 schema：

- [Step3QualitySupervisorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step3QualitySupervisorNode.java:188)

这一层的作用是：

- 提高模型按结构输出的概率
- 降低自由发挥空间
- 提前把字段约束讲清楚

但是要注意：

**这一层只能算“前置约束”，不能算真正的可靠性保障。**

因为模型仍然可能不听话。

所以系统后面一定要有更硬的兜底。

---

### 4.2 第二层：JSON 解析失败时，不直接崩，而是降级处理

Node1 是第一个明显依赖 JSON 输出的节点。

它在拿到模型返回后，不会直接信任，而是做下面几步：

1. 先清洗模型输出
2. 从文本里提取 JSON 片段
3. 用 `JSON.parseObject` 解析成 `StepExecutionPlanVO`
4. 如果解析失败，走降级逻辑

对应代码在：

- [Step1AnalyzerNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step1AnalyzerNode.java:409)

这里有两个非常关键的降级动作。

第一个是 `parseLegacyTextPlan`。

意思是：如果模型没有给出标准 JSON，但是输出里还有一些能读懂的自然语言内容，比如“需要工具”“下一步做什么”，系统就从文本里尽量抽出一个可用计划，而不是直接失败。

对应位置：

- [Step1AnalyzerNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step1AnalyzerNode.java:435)

第二个是 `buildFallbackPlan`。

意思是：如果连自然语言都提不出一个靠谱计划，那就生成一个最保守的兜底计划，例如“直接回答，不使用工具”。

对应位置：

- [Step1AnalyzerNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step1AnalyzerNode.java:474)

这一层的核心思想可以总结成一句话：

**Node1 不把“JSON 解析失败”直接等同于“流程失败”，而是优先尝试把坏输出收敛成一个保守可执行计划。**

这就是为什么一个坏 JSON 不会立刻把整条链拖死。

---

### 4.3 第三层：一旦解析成功，马上落到强类型上下文里，不再传裸文本

Node1 解析完计划后，不会把原始 JSON 字符串继续往后传。

它会把结果同步进结构化状态：

- `currentRound`
- `taskBoard`
- `roundArchive`
- `masterPlan`

对应代码在：

- [Step1AnalyzerNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step1AnalyzerNode.java:615)

这里的意义非常大。

因为这一步意味着：

- Node2 读的是 `currentRound` 这种结构化对象
- Node3 读的是 `roundExecutionSummary`、`taskBoard`、`acceptedResults`
- 不需要反复从上一节点的自由文本里重新猜字段

换句话说，系统只在入口处承担一次“不确定性”，之后尽量转为“确定性结构”。

这在多节点 Agent 设计里，是非常重要的稳定性原则。

如果没有这层，系统就会变成：

- 每个节点都重新解析上一节点文本
- 每一轮都重复面对 JSON 漂移
- 错误会逐轮累积放大

而这个项目通过 `DynamicContext + VO`，避免了这种问题。

---

### 4.4 第四层：Node2 不相信模型自述，必须捕获“真实工具调用证据”

这是整套设计里最关键的一层。

因为真正危险的，不是“JSON 语法错了”，而是下面这种情况：

- 模型说“我已经调用了工具”
- 实际上它根本没调
- 系统如果信了，就会把假结果传给 Node3

这个项目专门防的就是这件事。

Node2 的 prompt 会明确要求：

- 如果需要工具，必须真实调用 Spring AI 工具
- 不能只是输出一个描述 tool call 的 JSON
- 不能编造 ToolReceipt

对应代码在：

- [Step2PrecisionExecutorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java:83)

但更重要的是，它不是只靠 prompt。

系统在执行工具调用时，使用了 `ToolCallCaptureHolder` 去捕获真实回调记录：

- [ToolCallCaptureHolder.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/armory/support/ToolCallCaptureHolder.java:12)

每次真实工具执行，都会记录：

- 工具名
- 请求参数
- 响应内容
- 是否成功
- 错误类型
- 错误信息

这些都会落成 `ToolExecutionRecordVO`。

Node2 在执行完成后，会把这些真实记录塞回 `dynamicContext`，并同步进 `toolExecutionLog`：

- [Step2PrecisionExecutorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java:141)
- [Step2PrecisionExecutorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java:598)

这一步的本质是：

**系统把“模型说自己调用过工具”替换成“系统自己掌握工具调用事实”。**

这是从“语言可信”切换到“证据可信”。

面试里如果要说亮点，这一层一定要讲出来。

---

### 4.5 第五层：Node2 不只看是否调了工具，还会做结果证据和后置条件校验

仅仅“调用过工具”还不够。

因为还可能出现：

- 工具调了，但失败了
- 工具调了，但结果没保留下来
- 工具调了，但副作用实际上没发生

所以 Node2 会再进一步，把这轮执行压缩成一个结构化事实摘要：

- `toolRequired`
- `toolInvoked`
- `toolSuccess`
- `evidenceAvailable`
- `evidenceSummary`
- `blockingReason`

对应代码：

- [Step2PrecisionExecutorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java:564)

这相当于给 Node3 准备了一份“本轮真实执行报告”。

除此之外，这个项目还对副作用任务做了后置条件校验。

典型例子有两个：

1. 文件系统类任务
2. CSDN 发布类任务

对于文件任务，它会去检查文件路径是不是真的存在，内容是不是真的写进去了：

- [Step2PrecisionExecutorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java:950)

对于 CSDN 发布，它会从真实工具响应里找链接，并确认不是错误信息：

- [Step2PrecisionExecutorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java:756)

所以，这里的判断逻辑不是：

- 模型说成功了，所以成功

而是：

- 工具真实调用了
- 调用结果成功了
- 关键证据留下来了
- 必要时副作用被验证了

只有这样，系统才会认为这轮执行是可信的。

---

### 4.6 第六层：Node3 是最终验收门，不满足证据就强制重规划

Node3 的职责不是“润色上轮结果”，而是做真正的验收。

它的关键设计思想是：

**即使 Node2 的自然语言看起来很像成功，只要证据不够，也不让它通过。**

Node3 的 prompt 会明确告诉模型：

- 工具任务必须对照 `roundExecutionSummary` 和真实工具记录
- 自然语言叙述本身不能作为充分证据
- 证据不足时，必须返回重试而不是伪装成功

对应代码：

- [Step3QualitySupervisorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step3QualitySupervisorNode.java:188)

但同样，最重要的不是 prompt，而是它在代码里先做了一层硬判定：

- 如果本轮要求工具
- 但 `roundExecutionSummary` 显示没有真实调用、没有成功、没有证据
- 那么直接输出 `ROUND_RETRY + OVERALL_CONTINUE`

对应位置：

- [Step3QualitySupervisorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step3QualitySupervisorNode.java:264)

随后，Node3 会把决策应用回上下文：

- 成功就推进到下一步或结束
- 失败就写入 `REPLAN_SAME_STEP`
- 然后回到 Node1 重新规划

对应代码：

- [Step3QualitySupervisorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step3QualitySupervisorNode.java:541)

于是，这个项目形成了一个很关键的闭环：

1. Node1 规划
2. Node2 执行
3. Node3 验收
4. 不通过就回 Node1

这意味着系统不是“线性撞墙”，而是“失败后可回退、可重试、可重规划”。

这也是为什么它通常不会因为某一次输出不规范就整个挂死。

---

## 5. 异常场景逐个讲透

下面按面试官最可能追问的方式，把异常场景一条条说明白。

### 5.1 场景一：Node1 没输出 JSON，只输出一段自然语言

处理方式：

1. 先尝试从文本中提取 JSON
2. 提取不到或解析失败，就走 `parseLegacyTextPlan`
3. 从自然语言里识别“需不需要工具、任务是什么”
4. 组装成一个兜底计划

如果连这个都做不到，再走 `buildFallbackPlan`，用保守策略继续。

结论：

**不会因为 Node1 没给标准 JSON 就直接死。**

---

### 5.2 场景二：Node1 输出的是半截 JSON，或者字段名不对

处理方式基本一样：

1. 先 `extractJson`
2. 再 `JSON.parseObject`
3. 异常后自动降级到 legacy parser

这说明系统把“JSON 失败”当作一种可恢复异常，而不是不可恢复异常。

---

### 5.3 场景三：Node2 没有真的调工具，只输出一个“准备调工具的 JSON”

这是多节点 Agent 里特别常见的假动作问题。

项目里专门有识别逻辑 `looksLikeToolIntentOnly`，会检查这种情况：

- 输出是一个 JSON
- 里面有 `tool` 和 `arguments`
- 但没有真实 `ToolReceipt`
- 也没有真实执行证据

对应代码：

- [Step2PrecisionExecutorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java:909)

发现后系统会：

1. 先重试一次
2. 如果仍然是假动作，就把结果明确标记为失败

对应位置：

- [Step2PrecisionExecutorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java:103)

结论：

**它不会把“工具调用意图”误当成“工具调用事实”。**

---

### 5.4 场景四：工具参数不合法，工具执行报错

项目里有两层处理。

第一层是策略校验。

Node2 会先检查：

- 这个工具是否在允许列表里
- 是否满足工具策略

对应代码：

- [Step2PrecisionExecutorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java:64)

第二层是执行失败后的重试。

如果返回的是参数类错误，比如：

- Invalid arguments
- missing required
- expected xxx but got yyy

系统会再发一个 retry prompt，让模型只修复本次工具调用细节，而不是整个任务乱改。

对应代码：

- [Step2PrecisionExecutorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java:118)

如果重试后还是失败，Node3 会根据 `executionOutcome` 把它打回重规划。

---

### 5.5 场景五：任务依赖上轮生成内容，但 Node2 拿不到那段内容

这是一个非常容易让 Agent“看起来会做，实际做不了”的场景。

比如：

- “把刚刚那篇文章发到 CSDN”
- “把上一轮写的内容翻译一下”

如果系统不显式携带上轮内容，Node2 很容易：

- 猜内容
- 编内容
- 假装自己知道那篇文章是什么

这个项目专门加了 `sourceContent` 机制。

Node1 如果识别到任务依赖前序内容，就会把那段可复用内容显式塞进计划：

- [Step1AnalyzerNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step1AnalyzerNode.java:551)

Node2 在执行前会检查：如果这是依赖前文内容的任务，但 `sourceContent` 为空，就直接报：

- `MISSING_REQUIRED_SOURCE_CONTENT`

对应代码：

- [Step2PrecisionExecutorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java:831)

结论：

**系统宁可明确失败，也不允许模型自己脑补缺失上下文。**

---

### 5.6 场景六：Node3 自己的 JSON 输出又歪了怎么办

Node3 的输出也可能不稳定，这个项目同样做了兼容。

它先尝试把 Node3 输出按 JSON 解析。

如果能解析，就按结构化决策处理。

如果 JSON 解析失败，就退回老式文本格式解析，也就是从：

- `Assessment:`
- `Issues:`
- `Suggestions:`
- `Decision:`

这些字段里继续提取。

对应代码：

- [Step3QualitySupervisorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step3QualitySupervisorNode.java:306)

所以 Node3 这里也是一样的思路：

**尽量把坏输出修复为可消费状态，而不是立刻中断。**

---

## 6. 为什么通常不会“死机”，以及仍然会挂的点

这里要把话讲准确。

### 6.1 为什么通常不会死机

因为这套系统不是单点依赖模型输出，而是做了完整的“约束 + 降级 + 证据 + 回退”闭环。

总结一下，就是四个关键动作：

1. 模型输出被要求结构化
2. 结构化失败时尝试降级解析
3. 执行结果不看模型口头汇报，只看真实证据
4. 验收失败时不是结束，而是回到 Node1 重新规划

所以，大多数“不正常输出”最终会被转成下面几种可控状态：

- 保守计划
- 执行失败
- 证据不足
- 缺少上下文
- 同一步重规划

这些都属于“业务上的失败”，不是“系统直接挂掉”。

这就是面试里可以说的一个重点：

**它把模型不稳定，尽量收敛成状态不通过，而不是程序崩溃。**

### 6.2 仍然会挂的点

不过也要实话实说，这个项目不是完全不会中断。

现在仍有一些硬异常场景会直接抛错，例如：

- Node2 缺少关键上下文字段
- 工具策略校验直接抛 `IllegalStateException`
- Node3 缺少 `executionResult` 或 `currentStepPlan`

对应位置：

- [Step2PrecisionExecutorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java:53)
- [Step2PrecisionExecutorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java:66)
- [Step3QualitySupervisorNode.java](E:/javaProject/ai-agent-station-study/ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step3QualitySupervisorNode.java:52)

而且外层 `executeHandler.apply(...)` 没有把整条链包成“统一可恢复异常”，所以这些硬错误在当前版本里，理论上仍可能中断本次请求。

因此，最准确的评价应该是：

**这个项目已经很好地解决了“模型输出不规范导致业务链条失真”的问题，但还没有彻底消灭“底层硬异常导致请求中断”的问题。**

这个说法会显得你理解得很真实，不会像背稿子。

---

## 7. 面试时怎么复述

下面给你三种讲法。

### 7.1 一分钟短版

你可以这样说：

> 这个项目不是靠相信大模型一定输出合法 JSON 来保证稳定性，它的做法是把模型输出只当作边界输入，然后尽快转成系统内部的结构化上下文。Node1 如果 JSON 解析失败，会降级到文本解析或者保守计划，不会直接崩。Node2 对工具执行不相信模型自述，而是抓真实工具回调记录，形成执行证据和后置条件校验。Node3 再根据这些结构化证据做验收，如果证据不足，就强制回到 Node1 重规划，而不是把错误结果继续往下传。所以它的核心思想是，把模型不稳定尽量收敛成“这轮不通过，需要重试”，而不是“程序直接挂掉”。

### 7.2 三分钟标准版

你可以这样展开：

> 这个项目里，多节点之间表面上看像是在交换 JSON，但实际上真正的主交换载体是 `DynamicContext` 和一系列强类型 VO。模型输出 JSON 主要发生在 Node1 规划和 Node3 验收这两个边界节点。
>
> 第一层，prompt 会强制约束输出格式，比如只允许返回一个 JSON 对象，并给出固定 schema。
>
> 第二层，Node1 收到输出后不会直接信任，而是先提取 JSON，再反序列化成 `StepExecutionPlanVO`。如果失败，就降级到 legacy 文本解析；如果还不行，就生成一个保守 fallback plan。
>
> 第三层，一旦得到计划，就马上同步成结构化上下文，比如 `currentRound`、`taskBoard`、`roundArchive`，后续节点消费的是结构化状态，不再继续传裸 JSON。
>
> 第四层，Node2 对工具执行采用证据机制。模型说自己调了工具不算，系统要通过 `ToolCallCaptureHolder` 抓到真实的工具回调记录，形成 `ToolExecutionRecordVO`。
>
> 第五层，Node2 还会把本轮执行总结成 `RoundExecutionSummaryVO`，并对文件写入、CSDN 发布这种副作用任务做后置条件校验，确保不是口头成功。
>
> 第六层，Node3 是最终验收门。如果当前轮要求工具，但没有真实调用、没有成功、没有证据，那就直接判定 `ROUND_RETRY`，回到 Node1 重规划。
>
> 所以它不是保证模型永远输出正确，而是保证即使模型偶尔输出错，系统也能把问题收敛到可控失败和重试闭环里。

### 7.3 深挖版

如果面试官继续追问“那是不是完全不会挂”，你可以这样答：

> 也不能说完全不会挂。它已经很好解决了模型输出不规范、工具假调用、证据缺失这些常见问题，但如果底层上下文字段缺失，或者策略校验直接抛 `IllegalStateException`，当前版本仍可能中断请求。所以更准确地说，它解决的是“LLM 不稳定导致业务链失真”的问题，还没有完全做到“所有异常都转成可恢复状态机事件”。如果继续增强，我会考虑在每个节点外面包统一异常层，把所有节点异常收敛成 `FAILED_RETRYABLE` 或 `FAILED_FATAL` 两类状态，再交给状态机处理。

---

## 8. 最后用一句话总结

如果面试官只要一句最核心的话，你就说：

**这个项目的关键不是让模型永远输出完美 JSON，而是把模型输出当成不可信边界输入，通过降级解析、结构化上下文、真实工具证据、后置条件校验和 Node3 重规划闭环，把“不正常输出”尽量收敛成“本轮失败可重试”，而不是“系统直接挂掉”。**
