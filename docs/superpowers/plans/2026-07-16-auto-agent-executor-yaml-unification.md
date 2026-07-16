# AutoAgent Executor YAML Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将项目自行创建的线程池统一为可命名、可隔离、可关闭、可观测的 Spring Executor，并把全部线程池参数集中到 `application-*.yml`，消除生产代码对 `ForkJoinPool.commonPool()` 和 Java 硬编码线程数的依赖。

**Architecture:** 保留五个有明确职责的执行池：`autoAgentExecutionExecutor` 负责父 Run、子 Agent 和父 Run 恢复；`autoAgentSseExecutor` 负责普通、调试和 Mock SSE；`autoAgentContextRecallExecutor` 负责上下文三路召回；`autoAgentMemoryTaskExecutor` 负责 Turn Summary 与 Memory GC；`autoAgentMcpExecutor` 负责带超时的阻塞 MCP 调用。所有池由一个类型安全的 `AutoAgentExecutorProperties` 从 YAML 绑定，并由 Spring 负责初始化和优雅关闭。

**Tech Stack:** Java 17、Spring Boot 3.4.x、Spring `ThreadPoolTaskExecutor`、`@ConfigurationProperties`、JUnit 4、Mockito、Maven。

---

## Scope And Non-Goals

本计划只调整执行资源和装配关系，不改变 MainAgent、子 Agent、Runtime 状态机、LLM Contract、Tool/RAG 行为和对外 API。

开始实现前先运行 `git status --short`，并在 `feature/auto-agent-executor-yaml-unification` 分支或独立 worktree 中工作。当前工作区可能已经存在对 `AutoAgentRuntimeConfig.java`、`application-dev.yml` 和前端文件的用户修改；必须逐段合并，不得覆盖或回退这些修改。

纳入本次改造的生产代码执行池：

- Agent 执行池：父 Run 启动、通用子 Agent、父 Run 恢复。
- SSE 池：普通 SSE、Debug SSE、Mock SSE。
- Context Recall 池：MySQL、Vector、RAG 候选并行召回。
- Memory 池：Turn Summary、Memory GC 及其后续任务。
- MCP 池：`McpSyncClient.callTool` 的异步超时包装。
- Tomcat 工作线程：只做 YAML 显式配置，不在 Java 中自行创建。

明确不纳入：Hikari、Redis/Redisson 连接池，Spring AI、MCP SDK 或 HTTP 客户端内部线程。这些不是本项目直接创建的业务 Executor，应在各自稳定性专题中治理。

当前没有任何 `@Async` 方法，因此删除未使用的 `AsyncConfiguration`，不要为了“配置完整”保留一个没有调用者的线程池。

## Target YAML Contract

统一使用以下属性结构。Java 中不得再出现业务线程数、队列容量、线程名前缀或拒绝策略的硬编码值。

```yaml
auto-agent:
  executors:
    agent-execution:
      core-pool-size: 4
      max-pool-size: 8
      queue-capacity: 32
      keep-alive: 60s
      thread-name-prefix: auto-agent-exec-
      rejection-policy: CALLER_RUNS
      allow-core-thread-timeout: false
      wait-for-tasks-to-complete-on-shutdown: true
      await-termination: 30s
    sse:
      core-pool-size: 4
      max-pool-size: 8
      queue-capacity: 16
      keep-alive: 60s
      thread-name-prefix: auto-agent-sse-
      rejection-policy: ABORT
      allow-core-thread-timeout: false
      wait-for-tasks-to-complete-on-shutdown: false
      await-termination: 5s
    context-recall:
      core-pool-size: 4
      max-pool-size: 4
      queue-capacity: 32
      keep-alive: 60s
      thread-name-prefix: auto-agent-context-
      rejection-policy: CALLER_RUNS
      allow-core-thread-timeout: false
      wait-for-tasks-to-complete-on-shutdown: true
      await-termination: 15s
    memory:
      core-pool-size: 2
      max-pool-size: 2
      queue-capacity: 128
      keep-alive: 60s
      thread-name-prefix: auto-agent-memory-
      rejection-policy: ABORT
      allow-core-thread-timeout: false
      wait-for-tasks-to-complete-on-shutdown: true
      await-termination: 30s
    mcp:
      core-pool-size: 2
      max-pool-size: 4
      queue-capacity: 32
      keep-alive: 60s
      thread-name-prefix: auto-agent-mcp-
      rejection-policy: ABORT
      allow-core-thread-timeout: false
      wait-for-tasks-to-complete-on-shutdown: true
      await-termination: 15s
```

开发环境使用上述 4 核 8 线程机器的默认值。`application-prod.yml` 使用环境变量覆盖，但必须给出相同的保守默认值；不要在不知道部署 CPU、模型限流和 SSE 连接数时擅自提高生产默认并发。

测试环境使用更小的值：Agent `2/4/16`、SSE `1/2/8`、Context `2/2/16`、Memory `1/1/16`、MCP `1/2/8`。

---

### Task 1: Add Typed Executor Properties And YAML Contract

**Files:**
- Create: `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentExecutorProperties.java`
- Delete: `ai-agent-station-study-app/src/main/java/yhx/com/config/ThreadPoolConfigProperties.java`
- Modify: `ai-agent-station-study-app/src/main/resources/application-dev.yml`
- Modify: `ai-agent-station-study-app/src/main/resources/application-prod.yml`
- Modify: `ai-agent-station-study-app/src/main/resources/application-test.yml`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/config/AutoAgentExecutorPropertiesTest.java`

- [ ] **Step 1: Write a failing YAML binding test**

使用 `ApplicationContextRunner` 加载 `AutoAgentExecutorProperties`，至少断言以下值能绑定：

```java
Assert.assertEquals(4, properties.getAgentExecution().getCorePoolSize());
Assert.assertEquals(8, properties.getAgentExecution().getMaxPoolSize());
Assert.assertEquals(Duration.ofSeconds(60), properties.getAgentExecution().getKeepAlive());
Assert.assertEquals("auto-agent-exec-", properties.getAgentExecution().getThreadNamePrefix());
Assert.assertEquals(RejectionPolicy.CALLER_RUNS, properties.getAgentExecution().getRejectionPolicy());
```

- [ ] **Step 2: Run the test and verify it fails because the new properties class does not exist**

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=AutoAgentExecutorPropertiesTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation failure for missing `AutoAgentExecutorProperties`.

- [ ] **Step 3: Implement the type-safe properties model**

Use this public shape so later configuration and tests share exact names:

```java
@Data
@ConfigurationProperties(prefix = "auto-agent.executors")
public class AutoAgentExecutorProperties {

    private PoolProperties agentExecution;
    private PoolProperties sse;
    private PoolProperties contextRecall;
    private PoolProperties memory;
    private PoolProperties mcp;

    public void validate() {
        validatePool("agent-execution", agentExecution);
        validatePool("sse", sse);
        validatePool("context-recall", contextRecall);
        validatePool("memory", memory);
        validatePool("mcp", mcp);
    }

    @Data
    public static class PoolProperties {
        private int corePoolSize;
        private int maxPoolSize;
        private int queueCapacity;
        private Duration keepAlive;
        private String threadNamePrefix;
        private RejectionPolicy rejectionPolicy;
        private boolean allowCoreThreadTimeout;
        private boolean waitForTasksToCompleteOnShutdown;
        private Duration awaitTermination;
    }

    public enum RejectionPolicy {
        ABORT,
        CALLER_RUNS,
        DISCARD,
        DISCARD_OLDEST
    }
}
```

Implement `validatePool` to require a non-null spec, `corePoolSize >= 1`, `maxPoolSize >= corePoolSize`, `queueCapacity >= 0`, non-negative durations, a non-blank prefix, and a non-null rejection policy. `ThreadPoolConfig` must call `properties.validate()` before creating any executor. Fail application startup with an actionable message such as `auto-agent.executors.mcp.max-pool-size must be >= core-pool-size`. Do not add a validation dependency only for these five property groups.

- [ ] **Step 4: Move all pool values into profile YAML**

Remove the legacy `thread.pool.executor.config` block from dev/prod/test. Add the complete `auto-agent.executors` block to every profile. Use Spring duration syntax such as `60s`; do not preserve the current ambiguous `keep-alive-time: 10000`, which Java interprets as seconds.

For production, use environment-overridable values such as:

```yaml
core-pool-size: ${AUTO_AGENT_EXECUTION_CORE_POOL_SIZE:4}
max-pool-size: ${AUTO_AGENT_EXECUTION_MAX_POOL_SIZE:8}
```

Add explicit Tomcat settings under each profile:

```yaml
server:
  tomcat:
    threads:
      min-spare: 4
      max: 50
    accept-count: 50
```

Keep test values smaller. Do not modify Hikari or Redis connection pool settings in this task.

- [ ] **Step 5: Run the binding test**

Expected: PASS and all five pool specs are non-null.

- [ ] **Step 6: Commit**

```bash
git add ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentExecutorProperties.java ai-agent-station-study-app/src/main/java/yhx/com/config/ThreadPoolConfigProperties.java ai-agent-station-study-app/src/main/resources/application-dev.yml ai-agent-station-study-app/src/main/resources/application-prod.yml ai-agent-station-study-app/src/main/resources/application-test.yml ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/config/AutoAgentExecutorPropertiesTest.java
git commit -m "app: centralize executor properties in yaml"
```

### Task 2: Build Five Named, Lifecycle-Managed Executors

**Files:**
- Modify: `ai-agent-station-study-app/src/main/java/yhx/com/config/ThreadPoolConfig.java`
- Delete: `ai-agent-station-study-app/src/main/java/yhx/com/config/AsyncConfiguration.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/config/AutoAgentExecutorConfigTest.java`

- [ ] **Step 1: Write failing bean and thread-name tests**

The test must assert that these exact bean names exist:

```text
autoAgentExecutionExecutor
autoAgentSseExecutor
autoAgentContextRecallExecutor
autoAgentMemoryTaskExecutor
autoAgentMcpExecutor
```

Submit one latch-backed task to each bean and assert the thread name starts with the configured prefix. Also assert core/max/queue values through `ThreadPoolTaskExecutor#getThreadPoolExecutor()`.

- [ ] **Step 2: Replace the generic raw ThreadPoolExecutor factory**

`ThreadPoolConfig` should enable `AutoAgentExecutorProperties` and expose five `ThreadPoolTaskExecutor` beans. Do not retain `@ConditionalOnMissingBean(ThreadPoolExecutor.class)`.

The shared builder must apply every YAML field:

```java
private ThreadPoolTaskExecutor executor(PoolProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.getCorePoolSize());
    executor.setMaxPoolSize(properties.getMaxPoolSize());
    executor.setQueueCapacity(properties.getQueueCapacity());
    executor.setKeepAliveSeconds(Math.toIntExact(properties.getKeepAlive().toSeconds()));
    executor.setThreadNamePrefix(properties.getThreadNamePrefix());
    executor.setAllowCoreThreadTimeOut(properties.isAllowCoreThreadTimeout());
    executor.setWaitForTasksToCompleteOnShutdown(properties.isWaitForTasksToCompleteOnShutdown());
    executor.setAwaitTerminationSeconds(Math.toIntExact(properties.getAwaitTermination().toSeconds()));
    executor.setRejectedExecutionHandler(rejectionHandler(properties.getRejectionPolicy()));
    executor.initialize();
    return executor;
}
```

Map all four rejection enums to the matching `ThreadPoolExecutor` handler. Unknown/null policies must fail startup; do not silently fall back.

- [ ] **Step 3: Remove unused Spring async configuration**

Delete `AsyncConfiguration.java` and remove `@EnableAsync` from `ThreadPoolConfig`. `rg -n "@Async" . -g '*.java'` currently finds no methods, so no async behavior is being removed.

- [ ] **Step 4: Run config tests**

Expected: five beans initialize, use their configured names and expose expected bounds.

- [ ] **Step 5: Commit**

```bash
git add ai-agent-station-study-app/src/main/java/yhx/com/config/ThreadPoolConfig.java ai-agent-station-study-app/src/main/java/yhx/com/config/AsyncConfiguration.java ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/config/AutoAgentExecutorConfigTest.java
git commit -m "app: add named lifecycle managed executors"
```

### Task 3: Route Parent Runs, Child Agents, And Resume Through Agent Executor

**Files:**
- Modify: `ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/AgentChatController.java`
- Modify: `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentRuntimeConfig.java`
- Modify: `ai-agent-station-study-app/src/main/java/yhx/com/config/RuntimeParentRunResumePort.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/agent/GenericSubAgentDispatchOrchestrator.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/RuntimeDeferredSubAgentStartTest.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/RuntimeParentRunResumePortTest.java`

- [ ] **Step 1: Add a test proving the child executor is explicit**

Construct `GenericSubAgentDispatchOrchestrator` with a recording Executor. Dispatch one prepared child and assert the submitted Runnable is received by that Executor. Preserve the existing assertion that the parent Run is durably `WAITING_CHILDREN` before the child starts.

- [ ] **Step 2: Inject the agent executor into the chat controller**

Replace raw `ThreadPoolExecutor` injection with:

```java
@Resource(name = "autoAgentExecutionExecutor")
private Executor agentExecutionExecutor;
```

Continue using `execute`. Keep the existing exception reporting path. A rejected chat submission must return a failed API response instead of claiming `RUNNING`.

- [ ] **Step 3: Wire the same executor into subagent dispatch**

Add `@Qualifier("autoAgentExecutionExecutor") Executor agentExecutionExecutor` to the `genericSubAgentDispatchOrchestrator` bean method and pass it as the last constructor argument, replacing `null`.

Remove fallback-to-common-pool behavior from the production constructor path. Prefer requiring a non-null Executor in the full constructor. Lightweight unit-test constructors may retain direct executors only when explicitly supplied.

- [ ] **Step 4: Make rejected child submission terminal and observable**

Wrap `CompletableFuture.runAsync(..., childExecutor)` submission. On `RejectedExecutionException`:

```java
registry.markFailed(childRunId, "Generic subagent execution was rejected by the configured executor.");
lifecycleEventPublisher.terminal(parentRunId, latestRelation);
resumeParentIfSatisfied(parentRunId);
```

Do not leave a child in `RUNNING`; otherwise the parent remains in `WAITING_CHILDREN` forever.

- [ ] **Step 5: Inject the agent executor into parent resume**

Change `RuntimeParentRunResumePort` to require an Executor:

```java
public RuntimeParentRunResumePort(ObjectProvider<AutoAgentRuntimeService> provider,
                                  IRunRepository runRepository,
                                  Executor executor)
```

Replace unqualified `CompletableFuture.runAsync` with `CompletableFuture.runAsync(task, executor)` or `executor.execute(task)`. Update the Spring bean method with `@Qualifier("autoAgentExecutionExecutor")`.

On rejection, log `parentRunId` and preserve enough information for operational retry. Do not mark the parent completed or failed merely because resume scheduling was rejected.

- [ ] **Step 6: Run focused runtime tests**

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RuntimeDeferredSubAgentStartTest,RuntimeParentRunResumePortTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS; no test depends on `ForkJoinPool.commonPool()`.

- [ ] **Step 7: Commit**

```bash
git add ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/AgentChatController.java ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentRuntimeConfig.java ai-agent-station-study-app/src/main/java/yhx/com/config/RuntimeParentRunResumePort.java ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/agent/GenericSubAgentDispatchOrchestrator.java ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime
git commit -m "agent: isolate runtime and subagent execution"
```

### Task 4: Route All SSE Work Through The SSE Executor

**Files:**
- Modify: `ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/AgentEventController.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/AgentDebugController.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/AgentMockController.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/trigger/agent/AgentSseEventApiTest.java`

- [ ] **Step 1: Change all three controllers to the named SSE Executor**

Use the exact injection in every controller:

```java
@Resource(name = "autoAgentSseExecutor")
private Executor sseExecutor;
```

Replace the unqualified `CompletableFuture.runAsync` in `AgentMockController` with `sseExecutor.execute`.

- [ ] **Step 2: Handle saturation without leaking SseEmitter**

Wrap submission in `try/catch (RejectedExecutionException)`. On rejection, immediately call `sseEmitterRegistry.completeWithError(streamKey, error)` and return the emitter. Do not allow a registered emitter to stay open for five minutes without an executing poller.

- [ ] **Step 3: Extend SSE tests**

Inject a rejecting Executor and assert the emitter is completed with an error. Keep existing event ordering, heartbeat and terminal-event behavior unchanged.

- [ ] **Step 4: Run SSE tests**

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentSseEventApiTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/AgentEventController.java ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/AgentDebugController.java ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/AgentMockController.java ai-agent-station-study-app/src/test/java/yhx/com/test/trigger/agent/AgentSseEventApiTest.java
git commit -m "trigger: isolate sse streaming executor"
```

### Task 5: Move Context And Memory Pools Out Of Runtime Assembly

**Files:**
- Modify: `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentRuntimeConfig.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/memory/AsyncTurnSummaryProcessor.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/memory/gc/MemoryGcTaskDispatcher.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/config/AutoAgentExecutorConfigTest.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/memory/MemoryExecutorRejectionTest.java`

- [ ] **Step 1: Remove Java-created fixed pools**

Delete these bean methods from `AutoAgentRuntimeConfig`:

```java
autoAgentContextRecallExecutor() -> Executors.newFixedThreadPool(4)
autoAgentMemoryTaskExecutor() -> Executors.newFixedThreadPool(2)
```

Remove the now-unused `Executors` import.

- [ ] **Step 2: Preserve existing qualified injections**

`ContextPreparationService`, `AsyncTurnSummaryProcessor`, `MemoryGcFollowupScheduler` and `MemoryGcTaskDispatcher` must continue receiving the exact bean names `autoAgentContextRecallExecutor` and `autoAgentMemoryTaskExecutor`. No domain constructor should change.

- [ ] **Step 3: Assert no pool size remains hardcoded in runtime config**

Add a static assertion in the config test or a review command:

```bash
rg -n "Executors\.new|new ThreadPoolExecutor" ai-agent-station-study-app/src/main/java ai-agent-station-study-domain/src/main/java ai-agent-station-study-trigger/src/main/java ai-agent-station-study-infrastructure/src/main/java
```

Expected: no project-created production executor outside `ThreadPoolConfig`.

- [ ] **Step 4: Preserve persisted Memory tasks when the executor is saturated**

Both Memory entry points persist a `PENDING` task before submitting work. Catch `RejectedExecutionException` around `executor.execute`, log `taskType/taskId/turnId`, and leave the persisted task in `PENDING` so `MemoryGcRetryService` can retry it. Do not mark the memory task successful or failed solely because local scheduling was rejected.

The rejection test must use an Executor that always throws and assert that the exception does not escape the turn-completion path and that the task remains retrievable as `PENDING`.

- [ ] **Step 5: Commit**

```bash
git add ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentRuntimeConfig.java ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/memory/AsyncTurnSummaryProcessor.java ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/memory/gc/MemoryGcTaskDispatcher.java ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/config/AutoAgentExecutorConfigTest.java ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/memory/MemoryExecutorRejectionTest.java
git commit -m "app: yaml configure context and memory executors"
```

### Task 6: Isolate Timed MCP Calls From The Common Pool

**Files:**
- Modify: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/port/SpringAiMcpToolInvokerAdapter.java`
- Modify: `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentToolConfig.java`
- Modify: `ai-agent-station-study-app/src/test/java/yhx/com/infrastructure/adapter/port/SpringAiMcpToolInvokerAdapterTest.java`

- [ ] **Step 1: Write a failing executor-usage test**

Pass a recording Executor to the adapter, invoke a command with `timeoutMs > 0`, and assert `client.callTool` runs on a thread whose name starts with `auto-agent-mcp-` or on the explicitly supplied test executor.

- [ ] **Step 2: Require an Executor in the adapter**

Use this constructor:

```java
public SpringAiMcpToolInvokerAdapter(McpClientRegistry registry, Executor mcpExecutor) {
    this.mcpClientRegistry = Objects.requireNonNull(registry, "McpClientRegistry is required.");
    this.mcpExecutor = Objects.requireNonNull(mcpExecutor, "MCP executor is required.");
}
```

Change the timeout branch to:

```java
CompletableFuture.supplyAsync(() -> callTool(...), mcpExecutor)
```

The no-timeout branch may continue calling the sync client directly because it does not create an additional thread.

- [ ] **Step 3: Map executor rejection to a deterministic tool failure**

Catch `RejectedExecutionException` around future creation and return a failed `McpToolInvokeResultVO` with:

```text
errorCode = MCP_EXECUTOR_SATURATED
called = false
```

Do not throw an unclassified RuntimeException into Tool Runtime.

- [ ] **Step 4: Wire the named MCP Executor**

Change the bean method in `AutoAgentToolConfig`:

```java
public McpToolInvokerPort mcpToolInvokerPort(
        McpClientRegistry registry,
        @Qualifier("autoAgentMcpExecutor") Executor mcpExecutor) {
    return new SpringAiMcpToolInvokerAdapter(registry, mcpExecutor);
}
```

Update all unit-test constructors.

- [ ] **Step 5: Run MCP tests**

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=SpringAiMcpToolInvokerAdapterTest,AutoAgentToolConfigTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS; timeout and initialization-failure behavior remains unchanged.

- [ ] **Step 6: Commit**

```bash
git add ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/port/SpringAiMcpToolInvokerAdapter.java ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentToolConfig.java ai-agent-station-study-app/src/test/java/yhx/com/infrastructure/adapter/port/SpringAiMcpToolInvokerAdapterTest.java
git commit -m "tool: isolate timed mcp execution"
```

### Task 7: Integration Verification And Operational Acceptance

**Files:**
- Modify: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/config/AutoAgentExecutorConfigTest.java`
- Modify: `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`

- [ ] **Step 1: Run the focused suite**

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=AutoAgentExecutorPropertiesTest,AutoAgentExecutorConfigTest,RuntimeDeferredSubAgentStartTest,RuntimeParentRunResumePortTest,MemoryExecutorRejectionTest,SpringAiMcpToolInvokerAdapterTest,AutoAgentToolConfigTest,AgentSseEventApiTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 2: Run full compilation**

```bash
mvn -q -DskipTests compile
```

Expected: exit code 0.

- [ ] **Step 3: Check for accidental common-pool and hardcoded-pool use**

```bash
rg -n "ForkJoinPool\.commonPool|CompletableFuture\.(runAsync|supplyAsync)\([^,]+\)|Executors\.new|new ThreadPoolExecutor" ai-agent-station-study-app/src/main/java ai-agent-station-study-domain/src/main/java ai-agent-station-study-trigger/src/main/java ai-agent-station-study-infrastructure/src/main/java
```

Expected: no project-owned production async path silently uses the common pool. Review any match manually because multiline calls may defeat a simple regex.

- [ ] **Step 4: Verify runtime behavior manually**

Run one normal chat and one `DELEGATE_AGENTS` request with two child tasks. Inspect logs or a thread dump and confirm these prefixes appear:

```text
auto-agent-exec-
auto-agent-sse-
auto-agent-context-
auto-agent-memory-
auto-agent-mcp-
```

Confirm the parent Run enters `WAITING_CHILDREN` before child execution, both child tasks can overlap, and the parent resumes once all children are terminal.

- [ ] **Step 5: Verify saturation semantics**

Using small test-only queue sizes, confirm:

- Rejected child tasks become terminal `FAILED` and cannot strand the parent.
- Rejected SSE tasks close the emitter immediately.
- Rejected MCP tasks return `MCP_EXECUTOR_SATURATED`.
- Agent `CALLER_RUNS` provides backpressure instead of silently dropping a Run.
- Memory rejection leaves its persisted task eligible for retry and is logged.

- [ ] **Step 6: Document only durable architecture facts**

If the architecture spec discusses async execution, update it to state that thread pools are named bulkheads configured under `auto-agent.executors`. Do not copy concrete dev thread counts into the long-lived architecture document; those belong in YAML.

- [ ] **Step 7: Final commit**

```bash
git add docs/architecture/auto-agent-main-loop-harness-redesign-spec.md ai-agent-station-study-app/src/test/java
git commit -m "test: verify auto agent executor isolation"
```

---

## Acceptance Criteria

- All project-created production Executors are defined in one Spring configuration class.
- All core/max/queue/keep-alive/prefix/rejection/shutdown values come from profile YAML.
- No active `@Async` configuration exists without an `@Async` caller.
- Parent Run start, generic subagents and parent resume use `autoAgentExecutionExecutor`.
- Normal, Debug and Mock SSE use `autoAgentSseExecutor`.
- Context and Memory no longer call `Executors.newFixedThreadPool` in Java config.
- Timed MCP calls do not use `ForkJoinPool.commonPool()`.
- Rejection cannot strand a parent Run or leak an SSE emitter.
- Existing deferred-child-start ordering remains covered by tests.
- Targeted tests and full compilation pass.

## Known Follow-Up, Explicitly Out Of Scope

LLM concurrency still needs a separate stability design covering global/per-model semaphores, RPM/TPM budgeting, 429 `Retry-After`, exponential backoff, circuit breaking and metrics. Do not mix that behavior into this executor-only change.
