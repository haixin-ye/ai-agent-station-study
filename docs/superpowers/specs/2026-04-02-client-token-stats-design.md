# Client 维度 Token 统计与 SSE 展示设计

日期：2026-04-02  
分支：feature/client-token-stats-emitter  
范围：AutoAgent 执行链路（不落库，仅会话内实时展示）

## 1. 目标与范围

### 1.1 目标
1. 对每次 client 调用统计 token：input/output/total。
2. 在左侧思考日志中显示每次调用 token 明细（小方块/标签样式）。
3. 在整个 agent 执行完成后，输出总 token 消耗（input/output/total）。

### 1.2 明确不做
1. 本期不做 MySQL/Redis 持久化。
2. 本期不做计费规则换算。
3. 本期不做多会话跨天聚合。

## 2. 现状与约束

1. 当前 Step1/Step2/Step3/Step4 均通过 `ChatClient.prompt(...).call().content()` 调用模型。
2. 左侧日志通过 SSE 数据结构 `AutoAgentExecuteResultEntity` 的 `type/subType/step/content` 渲染。
3. `AiClientNode` 在装配期构建 `ChatClient`，可统一挂载 `defaultAdvisors`。
4. 现有架构是数据库驱动 + 预装配 Bean，需采用低侵入改造。

## 3. 总体方案（已选）

采用 `TokenUsageAdvisor + SSE 事件`：

1. 在 `AiClientNode` 为每个 ChatClient 追加一个默认 advisor：`TokenUsageAdvisor`。
2. 每次模型调用结束后，advisor 统一读取 usage，发送一条 token 明细 SSE。
3. 同时将 usage 累加到会话级聚合器。
4. AutoAgent 流程结束前发送一条 token 总计 SSE。

选择原因：
1. 统一拦截，避免 Step 节点重复代码。
2. 新增/替换 client 时自动覆盖统计。
3. 对现有 DDD 分层和预装配机制改动最小。

## 4. 组件设计

### 4.1 TokenUsageAdvisor（新增）

建议路径：
- `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/armory/factory/element/TokenUsageAdvisor.java`

职责：
1. 在 `after(...)` 阶段读取 `ChatClientResponse.chatResponse()` usage。
2. 从 `context` 获取 client 与会话元信息。
3. 构建并发送 token 明细 SSE 事件。
4. 将本次 usage 累加到会话聚合器。
5. 统计失败不抛出异常（仅日志警告）。

上下文字段（约定 key）：
1. `token_stat_emitter`
2. `token_stat_session_id`
3. `token_stat_client_id`
4. `token_stat_client_type`
5. `token_stat_step`
6. `token_stat_accumulator`

### 4.2 Token 聚合器（新增）

建议结构：
- 轻量 Java Bean（会话内对象），放入 `DynamicContext.dataObjects`
- 字段：
  - `inputTokens`
  - `outputTokens`
  - `totalTokens`

职责：
1. 提供线程安全 `add(input, output, total)`。
2. 提供 `snapshot()` 用于最终 SSE 输出。

说明：
- 运行线程由 AutoAgent 的流程控制，按当前结构不会出现同一会话并发写多个 step，但仍建议使用原子类型或同步保护，避免后续并发扩展风险。

### 4.3 AutoAgentExecuteResultEntity 扩展

保持原结构，新增工厂方法：
1. `createTokenClientUsageResult(...)`
2. `createTokenTotalUsageResult(...)`

事件约定：
1. `type = "token"`
2. `subType = "client_usage" | "total_usage"`
3. `step = 当前步骤（明细）| null（总计）`
4. `content = JSON 字符串（含 input/output/total + client 信息）`

### 4.4 AiClientNode 装配扩展

在现有 `defaultAdvisors` 的基础上追加 `TokenUsageAdvisor`，确保每个 client 都可统计。

### 4.5 Step 节点参数补充

在 Step1/2/3/4 的 `.advisors(a -> a.param(...))` 补充 token 上下文参数。

## 5. 数据流

### 5.1 单次调用
1. StepX 构建 prompt 并注入 token 统计上下文。
2. ChatClient 执行调用。
3. TokenUsageAdvisor.after 读取 usage。
4. 发送 `type=token, subType=client_usage` 到 SSE。
5. 聚合器累加。

### 5.2 流程结束
1. `AutoAgentExecuteStrategy.execute(...)` 在完成阶段读取聚合器。
2. 发送 `type=token, subType=total_usage` 到 SSE。
3. 再发送现有 `complete` 事件，保持兼容。

## 6. 前端展示设计

目标文件：
- `docs/dev-ops/nginx/html/index_cool.html`

改造点：
1. `stageTypeMap` 增加 `token` 类型。
2. `subTypeMap` 增加 `client_usage`、`total_usage`。
3. `handleSSEMessage` 保持左侧渲染逻辑，token 事件统一走 `addLeftStageMessage`。
4. 在 `addLeftStageMessage` 中对 `type===token` 增加 token 小方块渲染：
   - 输入：`IN xxx`
   - 输出：`OUT xxx`
   - 总计：`TOTAL xxx`
5. `client_usage` 显示 clientId/clientType + Step。
6. `total_usage` 显示整轮总和，视觉上用更醒目标记。

## 7. Token 读取策略

主路径：
1. 从 `ChatClientResponse.chatResponse()` 读取 usage（Spring AI 返回的 usage 元数据）。

降级路径：
1. usage 为空或字段缺失时，按 0 处理。
2. 仍发送事件（可附带 `usageMissing=true`），避免前端断层。

兼容原则：
1. 无论上游模型是否返回 usage，业务主流程不受影响。

## 8. 错误处理与稳定性

1. Token 统计逻辑异常只记录日志，不中断执行链路。
2. SSE 发送异常沿用现有 `sendSseResult` 容错策略。
3. 不修改现有 `analysis/execution/supervision/summary` 语义，新增 `token` 类型向后兼容。

## 9. 测试策略

### 9.1 后端
1. `TokenUsageAdvisor` 单测：
   - 正常 usage -> 明细事件 + 聚合累加。
   - usage 缺失 -> 0 值事件。
   - context 缺 key -> 不抛异常。
2. AutoAgent 执行链路测试：
   - 结束时产生 `total_usage` 事件。

### 9.2 前端
1. token 明细事件正确渲染小方块。
2. token 总计事件显示在左侧并格式正确。
3. 不影响既有阶段日志显示。

## 10. 实施边界与里程碑

里程碑 1：后端可发送 token 明细与总计 SSE。  
里程碑 2：前端可视化 token 明细与总计。  
里程碑 3：联调验证 3 个步骤 + 总计展示一致。

---

## Spec 自检结论

1. 占位符检查：无 TBD/TODO。
2. 一致性检查：后端事件结构与前端渲染映射一致。
3. 范围检查：聚焦于会话内实时统计与展示，未扩展到持久化。
4. 歧义检查：统计口径固定为每次 client 调用明细 + 全流程总计。
