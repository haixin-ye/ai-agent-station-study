# Client Token统计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为每次 client 调用输出 token 明细并在执行结束输出总计，通过 SSE 实时展示到左侧思考区。

**Architecture:** 使用统一 `TokenUsageAdvisor` 拦截每次调用后的 usage，发送 `token/client_usage` 事件并累加到会话聚合器；流程结束时输出 `token/total_usage`。保留现有 `AutoAgentExecuteResultEntity` 协议并扩展 token 工厂方法。

**Tech Stack:** Java 17, Spring AI ChatClient Advisor, Spring Boot SSE, 前端原生 JS。

---

### Task 1: 扩展结果协议与聚合器

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/model/entity/TokenUsageAccumulator.java`
- Modify: `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/model/entity/AutoAgentExecuteResultEntity.java`
- Test: `ai-agent-station-study-app/src/test/java/cn/bugstack/ai/test/domain/TokenUsageAccumulatorTest.java`

- [ ] Step 1: 写失败测试（聚合统计）
- [ ] Step 2: 运行测试确认失败
- [ ] Step 3: 实现 TokenUsageAccumulator 与 token 事件工厂方法
- [ ] Step 4: 运行测试确认通过

### Task 2: 实现 TokenUsageAdvisor 并接入执行上下文

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/armory/factory/element/TokenUsageAdvisor.java`
- Modify: `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/service/execute/auto/step/AbstractExecuteSupport.java`
- Modify: `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/armory/AiClientNode.java`
- Modify: `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step1AnalyzerNode.java`
- Modify: `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java`
- Modify: `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step3QualitySupervisorNode.java`
- Modify: `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step4LogExecutionSummaryNode.java`
- Modify: `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/AutoAgentExecuteStrategy.java`

- [ ] Step 1: 在步骤节点注入 token 统计上下文参数
- [ ] Step 2: 实现 advisor 从响应提取 usage 并发送 client_usage
- [ ] Step 3: 在 execute 完成阶段发送 total_usage
- [ ] Step 4: 运行相关测试与编译检查

### Task 3: 前端 token 事件渲染

**Files:**
- Modify: `docs/dev-ops/nginx/html/index_cool.html`

- [ ] Step 1: 扩展 stage/subType 映射
- [ ] Step 2: 增加 token 小方块渲染
- [ ] Step 3: 手工检查 token 明细与总计显示

### Task 4: 验证与提交

**Files:**
- Modify: `docs/superpowers/specs/2026-04-02-client-token-stats-design.md`（若设计同步更新）

- [ ] Step 1: 运行测试与必要命令
- [ ] Step 2: 记录验证结果
- [ ] Step 3: 提交中文 commit（标题简短 + 分点说明）
