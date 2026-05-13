# AutoAgent Phase 11 Old Harness Isolation Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Isolate and clean up the old fixed Node1-4 AutoAgent harness so normal execution can only use the new main-loop Runtime.

**Architecture:** Cleanup happens by routing and boundary control first, deletion second. The new Runtime becomes the only normal AutoAgent execution path. Old Node1-4 classes, old prompt/parser contracts, and old node-trace outputs are either put behind an explicit legacy comparison switch or removed after compile-safe migration. Old trace output must never feed the normal final answer path.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Maven multi-module project, DDD package layout under `yhx.com`, existing trigger/domain/infrastructure/app modules.

---

## 0. Execution Rules

- Start this phase only after Phase 10 API/SSE compiles against the new Runtime.
- Do not delete old code before proving no normal API path calls it.
- Do not route old Node1-4 output into `FinalDeliveryService`.
- Do not expose old `node-trace` logs through normal frontend APIs.
- Do not keep two active AutoAgent implementations behind the same route.
- Do not commit unless the user explicitly asks.

## 1. Source Of Truth

Use the English canonical spec only:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`

Primary spec sections:

- Section 0.5: non-negotiable rules
- Section 1.1: current harness problems
- Section 2.7: final answer ownership
- Section 2.8: debug data boundary
- Section 8: DDD package layout
- Section 10: frontend API and SSE
- Section 13.14: old harness isolation and cleanup

## 2. Phase Boundary

### In Scope

- Identify old Node1-4 execution classes and callers.
- Identify old trace/log output paths.
- Identify old prompt/parser contracts that conflict with the new contract system.
- Replace normal AutoAgent execution route with new `AutoAgentRuntimeService`.
- Add optional legacy comparison switch only when needed.
- Remove or quarantine old parser/prompt code that can conflict with new contracts.
- Block old trace output from final answer and normal frontend APIs.
- Update docs and tests.

### Out Of Scope

- Rewriting new Runtime behavior.
- Adding new features to old Node1-4 harness.
- Maintaining old frontend trace UI as a normal production view.
- Data migration of historical node-trace log files.

## 3. Old Harness Inventory

Known old harness files:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/AutoAgentExecuteStrategy.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/contract/AutoAgentNodeContract.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/contract/AutoAgentNodeContracts.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/contract/AutoAgentPromptContractSupport.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/step/AbstractExecuteSupport.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/step/Step1AnalyzerNode.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/step/Step2PrecisionExecutorNode.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/step/Step3QualitySupervisorNode.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/step/Step4LogExecutionSummaryNode.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/step/factory/DefaultAutoAgentExecuteStrategyFactory.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/support/AutoAgentTraceLogSupport.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/support/SessionMemoryPromptSupport.java`

Potential old output/log paths:

- `data/log/node-trace/`
- old frontend panels that render raw node trace JSON
- old API response fields that contain node trace or node result internals

## 4. Routing Design

### 4.1 Normal Route

Normal AutoAgent route must call:

```text
AgentChatController
  -> AgentRuntimeFacade
  -> AutoAgentRuntimeService
  -> new main-loop Runtime
```

Normal AutoAgent route must not call:

```text
AutoAgentExecuteStrategy
Step1AnalyzerNode
Step2PrecisionExecutorNode
Step3QualitySupervisorNode
Step4LogExecutionSummaryNode
DefaultAutoAgentExecuteStrategyFactory
```

### 4.2 Legacy Comparison Route

If old harness is temporarily needed for comparison, expose it only through explicit dev-only path:

```text
POST /agent/legacy/compare
```

Rules:

- disabled by default
- local/dev profile only
- never used by normal chat UI
- response clearly marked as debug/legacy
- old output cannot become normal assistant message

If comparison route is not needed, do not create it.

## 5. Configuration Design

Create or update config:

```yaml
auto-agent:
  runtime:
    enabled: true
  legacy:
    enabled: false
    compare-api-enabled: false
```

Rules:

- `auto-agent.runtime.enabled=true` is required for normal AutoAgent API.
- `auto-agent.legacy.enabled=false` by default.
- production must not enable old harness.

## 6. Cleanup Strategy

### 6.1 Step 1: Caller Audit

Search all modules for references:

```text
AutoAgentExecuteStrategy
DefaultAutoAgentExecuteStrategyFactory
Step1AnalyzerNode
Step2PrecisionExecutorNode
Step3QualitySupervisorNode
Step4LogExecutionSummaryNode
AutoAgentTraceLogSupport
AutoAgentNodeContracts
```

Classify each reference:

- normal execution path
- test-only
- debug-only
- unused

### 6.2 Step 2: Route Replacement

Replace normal execution callers with:

- `AgentRuntimeFacade`
- `AutoAgentRuntimeService`

Rules:

- preserve external API response compatibility where needed.
- do not return old node trace in normal response.
- use normal SSE for progress events.

### 6.3 Step 3: Output Boundary Removal

Remove or disable normal frontend fields that expose:

- `stepPlan`
- `todoList` from old nodes
- raw node JSON
- old `understanding`
- old `result.rawResult`
- old verifier/supervisor summaries
- old `node4` execution summary as final answer

Replacement:

- normal UI reads `agent_message`, `FinalResponse`, `agent_run_event`, pending input, and artifacts.
- debug UI reads `agent_run_trace` only through debug API.

### 6.4 Step 4: Prompt/Parser Conflict Cleanup

Old prompt/parser code conflicts if it defines:

- node-specific JSON contracts outside `ContractRegistry`
- old fallback parser rules
- old node-to-node summary prompts
- old final-answer construction from node trace

Either delete it or keep only under legacy package guarded by legacy switch.

### 6.5 Step 5: Trace Log Isolation

Old `data/log/node-trace` output must:

- not be written by new Runtime
- not be read by normal APIs
- not be used as final answer fallback
- be accessible only manually or through future migration/debug tooling

## 7. Files To Modify

Likely files to inspect and modify:

- `ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/*`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/IExecuteStrategy.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/AutoAgentExecuteStrategy.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/flow/FlowExecuteStrategy.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/**`
- `ai-agent-station-study-app/src/main/java/yhx/com/config/**`
- `docs/dev-ops/nginx/html/index_cool.html`
- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md` only if documentation references require clarification

Final write scope depends on caller audit. Do not pre-delete `execute/auto/**` until compile references are known.

## 8. Required Tests

Create under:

- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/migration/`

Required files:

- `OldHarnessRoutingIsolationTest.java`
- `OldTraceOutputIsolationTest.java`
- `LegacySwitchTest.java`
- `NormalApiNoOldTraceTest.java`

### 8.1 `OldHarnessRoutingIsolationTest`

Required cases:

1. `normal_chat_route_uses_new_runtime_facade`
2. `normal_chat_route_does_not_call_auto_agent_execute_strategy`
3. `old_step_nodes_are_not_registered_as_normal_runtime_nodes`

### 8.2 `OldTraceOutputIsolationTest`

Required cases:

1. `old_node_trace_cannot_be_final_response_source`
2. `old_raw_result_is_not_mapped_to_assistant_message`
3. `old_node_json_is_not_emitted_as_user_visible_event`

### 8.3 `LegacySwitchTest`

Required cases:

1. `legacy_harness_disabled_by_default`
2. `legacy_compare_api_requires_dev_switch`
3. `legacy_compare_response_is_marked_debug_only`

### 8.4 `NormalApiNoOldTraceTest`

Required cases:

1. `normal_message_api_has_no_step_plan_or_understanding_fields`
2. `normal_sse_has_no_old_node_trace_payload`
3. `debug_api_is_the_only_path_for_internal_trace`

## 9. Execution Tasks

### Task 1: Run Caller Audit

- [ ] Run:

```powershell
rg -n "AutoAgentExecuteStrategy|DefaultAutoAgentExecuteStrategyFactory|Step1AnalyzerNode|Step2PrecisionExecutorNode|Step3QualitySupervisorNode|Step4LogExecutionSummaryNode|AutoAgentTraceLogSupport|AutoAgentNodeContracts" ai-agent-station-study-domain ai-agent-station-study-trigger ai-agent-station-study-app ai-agent-station-study-api docs
```

Expected:

```text
All old harness references are classified as normal path, debug path, test path, or unused.
```

### Task 2: Add Legacy Configuration

**Files:**

- Create or modify app configuration classes for `auto-agent.legacy`.
- Modify yml only if project config requires defaults.

- [ ] Add `legacy.enabled=false`.
- [ ] Add `legacy.compare-api-enabled=false`.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 3: Replace Normal Execution Route

**Files:**

- Trigger/controller/facade files identified by Task 1.

- [ ] Route normal chat execution to `AgentRuntimeFacade`.
- [ ] Ensure old `AutoAgentExecuteStrategy` is not called.
- [ ] Preserve public response DTO shape where required.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-trigger -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 4: Remove Old Output From Normal API/SSE

**Files:**

- DTO/controller/frontend files identified by Task 1.

- [ ] Remove old `stepPlan`, `todoList`, `understanding`, raw node result, and old result summary from normal DTOs.
- [ ] Ensure normal SSE uses `UserVisibleEvent`.
- [ ] Ensure debug data uses debug API only.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-trigger -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 5: Quarantine Or Delete Old Contract/Parser Code

**Files:**

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/auto/contract/**`
- old prompt/parser callers identified by Task 1

- [ ] If no references remain, delete old contract/parser classes.
- [ ] If legacy compare is kept, move usage behind `auto-agent.legacy.enabled`.
- [ ] Ensure new `ContractRegistry` remains the only normal contract source.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 6: Add Isolation Tests

**Files:**

- Create tests listed in Section 8.

- [ ] Implement all Section 8 cases.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=OldHarnessRoutingIsolationTest,OldTraceOutputIsolationTest,LegacySwitchTest,NormalApiNoOldTraceTest" test
```

Expected result:

```text
BUILD SUCCESS
```

### Task 7: Cross-Spec Consistency Scan

- [ ] Run:

```powershell
rg -n "Step1AnalyzerNode|Step2PrecisionExecutorNode|Step3QualitySupervisorNode|Step4LogExecutionSummaryNode|node-trace|AutoAgentNodeContracts" ai-agent-station-study-trigger ai-agent-station-study-api ai-agent-station-study-app docs\architecture\auto-agent-main-loop-harness-redesign-spec.md
```

Expected:

```text
No old harness references in normal API/app path or canonical spec, except historical problem descriptions if explicitly marked historical.
```

- [ ] Run:

```powershell
rg -n "stepPlan|todoList|understanding|rawResult" ai-agent-station-study-api ai-agent-station-study-trigger docs\dev-ops\nginx\html\index_cool.html
```

Expected:

```text
No normal frontend/API dependency on old node trace display fields.
```

## 10. Acceptance Checklist

- [ ] Normal chat route calls new Runtime.
- [ ] Normal chat route cannot call old Node1-4 flow.
- [ ] Old trace output cannot become final answer.
- [ ] Normal API/SSE contains no old step/node trace fields.
- [ ] Debug API remains the only internal trace path.
- [ ] Legacy harness is disabled by default.
- [ ] Legacy comparison route is absent or dev-only.
- [ ] Old prompt/parser contracts do not conflict with new `ContractRegistry`.
- [ ] Compile passes.
- [ ] Isolation tests pass.

## 11. Worker Split Guidance

If using subagents, split work by non-overlapping file ownership:

- Worker A: caller audit and legacy config.
- Worker B: trigger/facade normal route replacement.
- Worker C: API/SSE/frontend old output removal.
- Worker D: old contract/parser quarantine or deletion.
- Worker E: isolation tests.

The integrator must verify routing and normal output boundaries before deleting old code.

