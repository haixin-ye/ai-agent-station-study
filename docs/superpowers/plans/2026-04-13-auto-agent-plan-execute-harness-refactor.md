# Auto Agent Plan-and-Execute Harness Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有四节点 AutoAgent 重构为带验证层的 Plan-and-Execute 架构，消除 MCP 幻觉成功，并把 Node1-4 的 system prompt 全量外置到 MySQL。

**Architecture:** 保留 `Node1 -> Node2 -> Node3 -> Node1 ... -> Node4` 物理拓扑不变，引入结构化 `DynamicContext`、显式 `nextRoundDirective`、真实工具执行日志和 `acceptedResults` 验收面。运行时 MCP/advisor/RAG 继续由 Spring AI 装配，业务编排状态只在 harness 内流转。

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring AI, Maven, MySQL, SSE, FastJSON, JUnit/Spring Boot Test.

---

## File Structure

### Core domain files to modify
- `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/AutoAgentExecuteStrategy.java`
- `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/factory/DefaultAutoAgentExecuteStrategyFactory.java`
- `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/RootNode.java`
- `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/AbstractExecuteSupport.java`
- `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step1AnalyzerNode.java`
- `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java`
- `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step3QualitySupervisorNode.java`
- `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step4LogExecutionSummaryNode.java`
- `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/armory/AiClientNode.java`

### New domain files to create
- `.../model/entity/SessionGoalVO.java`
- `.../model/entity/MasterPlanVO.java`
- `.../model/entity/PlanStepVO.java`
- `.../model/entity/CurrentRoundTaskVO.java`
- `.../model/entity/RoundArchiveVO.java`
- `.../model/entity/TaskBoardItemVO.java`
- `.../model/entity/ToolExecutionRecordVO.java`
- `.../model/entity/AcceptedResultVO.java`
- `.../model/entity/OverallStatusVO.java`
- `.../model/entity/NextRoundDirectiveVO.java`
- `.../model/valobj/enums/NextRoundDirectiveTypeEnumVO.java`
- `.../model/valobj/enums/OverallStateEnumVO.java`
- `.../model/valobj/enums/StepStatusEnumVO.java`

### Prompt and SQL files to modify
- `Prompt.txt`
- `docs/dev-ops/mysql/sql/ai-agent-station-study.sql`
- `docs/dev-ops/sql-bak/3-12-ai-agent-station-study.sql`

### Tests to create or modify
- `ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/AutoAgentExecuteStrategyTest.java`
- `ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/AutoAgentPlanExecuteStateMachineTest.java`
- `ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/ToolTruthContractTest.java`
- `ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step1AnalyzerNodeTest.java`
- `ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNodeTest.java`
- `ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step3QualitySupervisorNodeTest.java`
- `ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step4LogExecutionSummaryNodeTest.java`

---

### Task 1: 建立新的编排状态对象与 DynamicContext 骨架

**Files:**
- Create: `SessionGoalVO.java`, `MasterPlanVO.java`, `PlanStepVO.java`, `CurrentRoundTaskVO.java`
- Create: `RoundArchiveVO.java`, `TaskBoardItemVO.java`, `ToolExecutionRecordVO.java`
- Create: `AcceptedResultVO.java`, `OverallStatusVO.java`, `NextRoundDirectiveVO.java`
- Create: `NextRoundDirectiveTypeEnumVO.java`, `OverallStateEnumVO.java`, `StepStatusEnumVO.java`
- Modify: `DefaultAutoAgentExecuteStrategyFactory.java`
- Test: `AutoAgentPlanExecuteStateMachineTest.java`

- [ ] **Step 1: 写 DynamicContext 结构测试**
```java
@Test
void should_initialize_structured_context_for_plan_execute() {
    DynamicContext context = new DynamicContext();
    context.initSession("请分析项目并输出风险", 5);
    assertNotNull(context.getSessionGoal());
    assertNotNull(context.getTaskBoard());
    assertNotNull(context.getRoundArchive());
    assertNotNull(context.getToolExecutionLog());
    assertNotNull(context.getAcceptedResults());
    assertNotNull(context.getOverallStatus());
}
```
- [ ] **Step 2: 运行测试确认失败**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=AutoAgentPlanExecuteStateMachineTest test`
Expected: FAIL，缺少 `initSession` 和结构化字段。
- [ ] **Step 3: 写 VO / 枚举最小实现**
```java
public enum NextRoundDirectiveTypeEnumVO {
    REPLAN_SAME_STEP, ADVANCE_NEXT_STEP, FINISH_SUCCESS, FINISH_PARTIAL, FINISH_FAILED
}
```
- [ ] **Step 4: 扩展 DynamicContext**
```java
public void initSession(String rawUserInput, int maxRounds) {
    this.maxStep = maxRounds;
    this.sessionGoal = SessionGoalVO.builder().rawUserInput(rawUserInput).sanitizedGoal(rawUserInput).maxRounds(maxRounds).build();
    this.overallStatus = OverallStatusVO.running();
}
```
- [ ] **Step 5: 重新运行测试确认通过**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=AutoAgentPlanExecuteStateMachineTest test`
Expected: PASS.
- [ ] **Step 6: Commit**
```bash
git add ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/model ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/factory/DefaultAutoAgentExecuteStrategyFactory.java ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/AutoAgentPlanExecuteStateMachineTest.java
git commit -m "domain: add structured plan execute context"
```

### Task 2: 重构执行入口与状态机路由

**Files:**
- Modify: `AutoAgentExecuteStrategy.java`, `RootNode.java`, `AbstractExecuteSupport.java`
- Test: `AutoAgentExecuteStrategyTest.java`, `AutoAgentPlanExecuteStateMachineTest.java`

- [ ] **Step 1: 写 Node3 回 Node1 的 directive 测试**
```java
@Test
void should_route_node3_back_to_node1_when_directive_is_replan_same_step() {
    DynamicContext context = new DynamicContext();
    context.initSession("写桌面文件", 3);
    context.setNextRoundDirective(NextRoundDirectiveVO.replanSameStep("step-1", "缺少文件写入证据"));
    StrategyHandler<ExecuteCommandEntity, DynamicContext, String> next = node3.get(command, context);
    assertSame(step1AnalyzerNode, next);
}
```
- [ ] **Step 2: 运行测试确认失败**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=AutoAgentExecuteStrategyTest,AutoAgentPlanExecuteStateMachineTest test`
Expected: FAIL，旧逻辑只会 PASS/REPLAN。
- [ ] **Step 3: 改造入口初始化**
```java
dynamicContext.initSession(executeCommandEntity.getMessage(), executeCommandEntity.getMaxStep() != null ? executeCommandEntity.getMaxStep() : 3);
dynamicContext.setExecutionHistory(new StringBuilder());
```
- [ ] **Step 4: 在 AbstractExecuteSupport 中补充助手**
```java
protected void appendRoundArchive(DynamicContext context, int round, Consumer<RoundArchiveVO> updater) {
    RoundArchiveVO archive = context.getRoundArchive().computeIfAbsent(round, key -> new RoundArchiveVO());
    updater.accept(archive);
}
```
- [ ] **Step 5: 重新运行测试确认通过**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=AutoAgentExecuteStrategyTest,AutoAgentPlanExecuteStateMachineTest test`
Expected: PASS.
- [ ] **Step 6: Commit**
```bash
git add ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto
git commit -m "domain: add explicit round directive state machine"
```

### Task 3: 先重写 Node3，让它成为唯一验收入口

**Files:**
- Modify: `Step3QualitySupervisorNode.java`
- Test: `Step3QualitySupervisorNodeTest.java`, `ToolTruthContractTest.java`

- [ ] **Step 1: 写“无工具证据不得放行”测试**
```java
@Test
void should_replan_same_step_when_tool_required_but_no_tool_record_exists() throws Exception {
    DynamicContext context = fixtureContextForToolRequiredStep();
    context.setValue("executionResult", "已成功写入文件 C:/fake/output.txt");
    context.setCurrentRound(CurrentRoundTaskVO.builder().currentStepId("step-file").roundTask("写入桌面 txt 文件").expectedEvidence("filesystem write success").toolRequired(true).build());
    node3.doApply(command, context);
    assertEquals(NextRoundDirectiveTypeEnumVO.REPLAN_SAME_STEP, context.getNextRoundDirective().getDirectiveType());
    assertTrue(context.getAcceptedResults().isEmpty());
}
```
- [ ] **Step 2: 写“有成功工具证据才可验收”测试**
```java
@Test
void should_accept_result_when_tool_record_satisfies_expected_evidence() throws Exception {
    DynamicContext context = fixtureContextForToolRequiredStep();
    context.getToolExecutionLog().add(ToolExecutionRecordVO.builder().roundIndex(1).stepId("step-file").toolName("filesystem").success(true).normalizedOutcome("写入成功: C:/Users/Administrator/Desktop/report.txt").build());
    node3.doApply(command, context);
    assertFalse(context.getAcceptedResults().isEmpty());
    assertEquals(NextRoundDirectiveTypeEnumVO.ADVANCE_NEXT_STEP, context.getNextRoundDirective().getDirectiveType());
}
```
- [ ] **Step 3: 运行测试确认失败**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=Step3QualitySupervisorNodeTest,ToolTruthContractTest test`
Expected: FAIL，当前 Node3 仍按字符串 PASS 放行。
- [ ] **Step 4: 改写 Node3 为结构化验收器**
```java
if (Boolean.TRUE.equals(roundTask.getToolRequired()) && roundRecords.isEmpty()) {
    return VerificationDecision.replanSameStep("TOOL_NOT_CALLED", "缺少真实工具执行记录");
}
if (Boolean.TRUE.equals(roundTask.getToolRequired()) && roundRecords.stream().noneMatch(ToolExecutionRecordVO::getSuccess)) {
    return VerificationDecision.replanSameStep("TOOL_CALL_FAILED", "工具调用未成功");
}
```
- [ ] **Step 5: 同步写入 `acceptedResults/taskBoard/overallStatus/nextRoundDirective`**
```java
context.getAcceptedResults().add(accepted);
context.setNextRoundDirective(NextRoundDirectiveVO.advanceNextStep(stepId));
context.setOverallStatus(recomputeOverallStatus(context));
```
- [ ] **Step 6: 重新运行测试确认通过**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=Step3QualitySupervisorNodeTest,ToolTruthContractTest test`
Expected: PASS.
- [ ] **Step 7: Commit**
```bash
git add ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step3QualitySupervisorNode.java ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step3QualitySupervisorNodeTest.java ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/ToolTruthContractTest.java
git commit -m "domain: make node3 the sole acceptance gate"
```

### Task 4: 重写 Node1 为双模式 planner，并去掉代码里的大段硬编码 prompt

**Files:**
- Modify: `Step1AnalyzerNode.java`, `AiClientNode.java`
- Test: `Step1AnalyzerNodeTest.java`

- [ ] **Step 1: 写 Node1 bootstrap/round-planner 测试**
```java
@Test
void should_build_master_plan_on_first_round() throws Exception {
    DynamicContext context = new DynamicContext();
    context.initSession("搜索北京天气并写到桌面文件", 4);
    node1.doApply(command, context);
    assertNotNull(context.getMasterPlan());
    assertNotNull(context.getCurrentRound());
}
```
- [ ] **Step 2: 写同一步骤重规划测试**
```java
@Test
void should_replan_same_step_from_node3_directive() throws Exception {
    DynamicContext context = fixtureWithMasterPlan();
    context.setNextRoundDirective(NextRoundDirectiveVO.replanSameStep("step-search-and-write", "缺少写文件证据"));
    node1.doApply(command, context);
    assertEquals("step-search-and-write", context.getCurrentRound().getCurrentStepId());
}
```
- [ ] **Step 3: 运行测试确认失败**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=Step1AnalyzerNodeTest test`
Expected: FAIL，当前 Node1 仍返回 `StepExecutionPlanVO`。
- [ ] **Step 4: 将 Node1 输入输出切到 masterPlan/currentRound**
```java
if (context.getMasterPlan() == null || context.getMasterPlan().getMainSteps().isEmpty()) {
    context.setMasterPlan(parseBootstrapPlan(callPlanner(...)));
    context.setCurrentRound(buildFirstRoundTask(context.getMasterPlan(), context));
} else {
    context.setCurrentRound(buildNextRoundTask(context.getMasterPlan(), context.getTaskBoard(), context.getNextRoundDirective(), context));
}
```
- [ ] **Step 5: 删除代码里大段业务 prompt，改为只拼接上下文**
```java
String plannerInput = JSON.toJSONString(Map.of("sessionGoal", context.getSessionGoal(), "masterPlan", context.getMasterPlan(), "taskBoard", context.getTaskBoard(), "roundArchive", context.getRoundArchive(), "nextRoundDirective", context.getNextRoundDirective(), "availableTools", loadAllowedToolNames(flowConfig.getClientId())));
String planningResult = chatClient.prompt(plannerInput).call().content();
```
- [ ] **Step 6: 重新运行测试确认通过**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=Step1AnalyzerNodeTest test`
Expected: PASS.
- [ ] **Step 7: Commit**
```bash
git add ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step1AnalyzerNode.java ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/armory/AiClientNode.java ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step1AnalyzerNodeTest.java
git commit -m "domain: refactor node1 into bootstrap and round planner"
```

### Task 5: 重写 Node2 为当前轮执行器，并记录真实工具执行日志

**Files:**
- Modify: `Step2PrecisionExecutorNode.java`, `AbstractExecuteSupport.java`
- Test: `Step2PrecisionExecutorNodeTest.java`, `ToolTruthContractTest.java`

- [ ] **Step 1: 写 Node2 仅消费 currentRound 的测试**
```java
@Test
void should_execute_only_current_round_task() throws Exception {
    DynamicContext context = fixtureWithCurrentRound("step-2", "将查询结果保存为桌面 txt 文件");
    node2.doApply(command, context);
    assertEquals("step-2", context.getCurrentRound().getCurrentStepId());
    assertNotNull(context.getValue("node2ExecutionSnapshot"));
}
```
- [ ] **Step 2: 写工具日志沉淀测试**
```java
@Test
void should_append_tool_execution_record_when_tool_is_called() throws Exception {
    DynamicContext context = fixtureWithCurrentRound("step-file", "写入桌面文件");
    context.getCurrentRound().setToolRequired(true);
    context.getCurrentRound().setSuggestedTools(List.of("filesystem"));
    node2.doApply(command, context);
    assertFalse(context.getToolExecutionLog().isEmpty());
}
```
- [ ] **Step 3: 运行测试确认失败**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=Step2PrecisionExecutorNodeTest,ToolTruthContractTest test`
Expected: FAIL，当前 Node2 依赖 `currentStepPlan`。
- [ ] **Step 4: 改写 Node2 输入输出结构**
```java
CurrentRoundTaskVO roundTask = context.getCurrentRound();
String executorInput = JSON.toJSONString(Map.of("sessionGoal", context.getSessionGoal(), "currentRound", roundTask, "acceptedResults", context.getAcceptedResults(), "taskBoard", context.getTaskBoard()));
String executionResult = chatClient.prompt(executorInput).call().content();
context.setValue("node2ExecutionSnapshot", executionResult);
```
- [ ] **Step 5: 在工具调用后沉淀真实 `ToolExecutionRecordVO`**
```java
appendToolExecutionRecord(context, ToolExecutionRecordVO.builder().roundIndex(context.getStep()).stepId(roundTask.getCurrentStepId()).toolName(actualToolName).requestPayload(actualRequestJson).responsePayload(actualResponseJson).normalizedOutcome(normalizedOutcome).success(callSucceeded).errorType(callSucceeded ? "" : "TOOL_CALL_FAILED").errorMessage(callSucceeded ? "" : errorMessage).timestamp(LocalDateTime.now()).build());
```
- [ ] **Step 6: 重新运行测试确认通过**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=Step2PrecisionExecutorNodeTest,ToolTruthContractTest test`
Expected: PASS.
- [ ] **Step 7: Commit**
```bash
git add ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/AbstractExecuteSupport.java ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNodeTest.java ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/ToolTruthContractTest.java
git commit -m "domain: make node2 record real tool execution facts"
```

### Task 6: 重写 Node4 为只读已验收成果的最终回答节点

**Files:**
- Modify: `Step4LogExecutionSummaryNode.java`
- Test: `Step4LogExecutionSummaryNodeTest.java`

- [ ] **Step 1: 写 Node4 只读 acceptedResults 的测试**
```java
@Test
void should_generate_final_answer_from_accepted_results_only() throws Exception {
    DynamicContext context = fixtureCompletedContext();
    context.getAcceptedResults().add(AcceptedResultVO.builder().stepId("step-file").resultType("FILE_WRITE").content("已将报告写入 C:/Users/Administrator/Desktop/report.txt").build());
    context.setValue("node2ExecutionSnapshot", "模型口头声称还写了 fake 路径");
    node4.doApply(command, context);
    String finalSummary = context.getValue("finalSummary");
    assertTrue(finalSummary.contains("report.txt"));
    assertFalse(finalSummary.contains("fake"));
}
```
- [ ] **Step 2: 运行测试确认失败**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=Step4LogExecutionSummaryNodeTest test`
Expected: FAIL，当前 Node4 仍读 `executionHistory`。
- [ ] **Step 3: 改写 Node4 输入拼装**
```java
String summaryInput = JSON.toJSONString(Map.of("rawUserInput", context.getSessionGoal().getRawUserInput(), "sanitizedGoal", context.getSessionGoal().getSanitizedGoal(), "acceptedResults", context.getAcceptedResults(), "taskBoard", context.getTaskBoard(), "overallStatus", context.getOverallStatus()));
String summaryResult = chatClient.prompt(summaryInput).call().content();
```
- [ ] **Step 4: 重新运行测试确认通过**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=Step4LogExecutionSummaryNodeTest test`
Expected: PASS.
- [ ] **Step 5: Commit**
```bash
git add ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step4LogExecutionSummaryNode.java ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/step/Step4LogExecutionSummaryNodeTest.java
git commit -m "domain: make node4 summarize only accepted facts"
```

### Task 7: 全量外置 Node1-4 system prompt 到 MySQL，并更新 Prompt.txt 为 SQL 脚本

**Files:**
- Modify: `Prompt.txt`, `docs/dev-ops/mysql/sql/ai-agent-station-study.sql`, `docs/dev-ops/sql-bak/3-12-ai-agent-station-study.sql`
- Test: `AutoAgentExecuteStrategyTest.java`

- [ ] **Step 1: 写 prompt 装配回归测试**
```java
@Test
void should_load_node_system_prompts_from_mysql_only() {
    Map<String, AiClientSystemPromptVO> promptMap = repository.queryAiClientSystemPromptMapByClientIds(List.of("3101", "3102", "3103", "3104"));
    assertTrue(promptMap.containsKey("6101"));
    assertTrue(promptMap.containsKey("6102"));
    assertTrue(promptMap.containsKey("6103"));
    assertTrue(promptMap.containsKey("6104"));
}
```
- [ ] **Step 2: 运行测试确认当前基线失败或内容不匹配**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=AutoAgentExecuteStrategyTest test`
Expected: FAIL 或 prompt 内容断言不匹配。
- [ ] **Step 3: 编写四个中文 system prompt，遵循新架构职责**
```sql
UPDATE ai_client_system_prompt SET prompt_content = '你是 Node1（总规划与每轮派工节点）...' WHERE prompt_id = '6101';
UPDATE ai_client_system_prompt SET prompt_content = '你是 Node2（当前轮执行节点）...' WHERE prompt_id = '6102';
UPDATE ai_client_system_prompt SET prompt_content = '你是 Node3（唯一验收节点）...' WHERE prompt_id = '6103';
UPDATE ai_client_system_prompt SET prompt_content = '你是 Node4（最终汇总节点）...' WHERE prompt_id = '6104';
```
- [ ] **Step 4: 从 Java 代码中删掉旧的大段 prompt 业务说明，只保留上下文拼接**
```java
String input = JSON.toJSONString(runtimePayload);
String result = chatClient.prompt(input).call().content();
```
- [ ] **Step 5: 把 `Prompt.txt` 改成 UTF-8 中文 SQL 脚本**
```sql
START TRANSACTION;
SELECT prompt_id, prompt_name, prompt_content FROM ai_client_system_prompt WHERE prompt_id IN ('6101','6102','6103','6104');
UPDATE ai_client_system_prompt ...;
COMMIT;
```
- [ ] **Step 6: 重新运行 prompt 装配测试**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=AutoAgentExecuteStrategyTest test`
Expected: PASS.
- [ ] **Step 7: Commit**
```bash
git add Prompt.txt docs/dev-ops/mysql/sql/ai-agent-station-study.sql docs/dev-ops/sql-bak/3-12-ai-agent-station-study.sql ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/AutoAgentExecuteStrategyTest.java ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/execute/auto/step
git commit -m "docs: externalize node prompts to mysql"
```

### Task 8: 端到端回归与 trace 兼容性验证

**Files:**
- Modify: `docs/dev-ops/nginx/html/index_cool.html`
- Test: `AutoAgentPlanExecuteStateMachineTest.java`, `ToolTruthContractTest.java`

- [ ] **Step 1: 写多轮任务状态推进测试**
```java
@Test
void should_finish_only_after_search_step_and_write_step_both_complete() throws Exception {
    DynamicContext context = fixtureForTwoStepTask();
    simulateRound1SearchAccepted(context);
    assertEquals(NextRoundDirectiveTypeEnumVO.ADVANCE_NEXT_STEP, context.getNextRoundDirective().getDirectiveType());
    simulateRound2WriteAccepted(context);
    assertEquals(OverallStateEnumVO.COMPLETED, context.getOverallStatus().getState());
}
```
- [ ] **Step 2: 运行测试确认失败**
Run: `mvn -q -pl ai-agent-station-study-app -am -Dtest=AutoAgentPlanExecuteStateMachineTest,ToolTruthContractTest test`
Expected: FAIL，若还有“单轮 PASS 即整体完成”残留逻辑。
- [ ] **Step 3: 适配前端 trace 字段兼容映射**
```javascript
const roundTask = event.currentRound?.roundTask || event.content;
const acceptedResults = event.acceptedResults || [];
```
- [ ] **Step 4: 运行聚合测试集**
Run: `mvn -q -pl ai-agent-station-study-app -am "-Dtest=Step1AnalyzerNodeTest,Step2PrecisionExecutorNodeTest,Step3QualitySupervisorNodeTest,Step4LogExecutionSummaryNodeTest,AutoAgentExecuteStrategyTest,AutoAgentPlanExecuteStateMachineTest,ToolTruthContractTest" test`
Expected: PASS.
- [ ] **Step 5: Commit**
```bash
git add docs/dev-ops/nginx/html/index_cool.html ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto ai-agent-station-study-app/src/test/java/cn/bugstack/ai/domain/agent/service/execute/auto/step
git commit -m "app: verify plan execute harness end to end"
```

## Self-Review

### Spec coverage
- `Node1` 双身份：Task 4 覆盖。
- `Node2` 唯一执行 + 工具自主调用：Task 5 覆盖。
- `Node3` 唯一验收 + 不直跳 Node2：Task 3 覆盖。
- `Node4` 只基于已验收成果回答：Task 6 覆盖。
- `DynamicContext` 双轨存档：Task 1 覆盖。
- `nextRoundDirective` 显式状态推进：Task 2 覆盖。
- MySQL prompt 外置与 `Prompt.txt` SQL 化：Task 7 覆盖。
- 前端 trace 兼容：Task 8 覆盖。

### Placeholder scan
- 未使用 `TODO/TBD/implement later`。
- 每个任务都给了具体文件、测试、命令和代码骨架。

### Type consistency
- 统一使用：`SessionGoalVO`、`MasterPlanVO`、`CurrentRoundTaskVO`、`ToolExecutionRecordVO`、`AcceptedResultVO`、`OverallStatusVO`、`NextRoundDirectiveVO`。
- 指令枚举统一为：`NextRoundDirectiveTypeEnumVO`。
- 总体状态统一为：`OverallStateEnumVO`。

## Risk Notes
- `Prompt.txt` 当前存在编码异常，实施时必须统一改为 UTF-8。
- 旧的 `StepExecutionPlanVO` 不能一次性粗暴删除，需在 Task 4/5 完成前保持最小兼容，避免编译中断。
- 如果前端 trace 过早切新字段，容易与后端 SSE 数据不对齐，因此放到 Task 8 统一收口。

## Suggested Execution Order
1. Task 1
2. Task 2
3. Task 3
4. Task 4
5. Task 5
6. Task 6
7. Task 7
8. Task 8
