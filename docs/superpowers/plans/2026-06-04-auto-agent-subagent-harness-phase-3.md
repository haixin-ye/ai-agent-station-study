# AutoAgent Subagent Harness Phase 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add MainAgent `DELEGATE_AGENTS` dispatch support with deterministic parent-child wait-all coordination.

**Architecture:** This phase enables MainAgent to emit a validated `DELEGATE_AGENTS` action and routes it to `AgentDispatchRuntime`. The dispatch runtime creates in-memory child run relations and pauses the parent in `WAITING_CHILDREN`; real child node execution, persistence adapters, and child ASK_USER resume remain later phases.

**Tech Stack:** Java 17, Maven, JUnit 4, FastJSON, Lombok.

---

## File Structure

- Modify `MainAgentActionTypeEnumVO.java`
  - Add `DELEGATE_AGENTS`; do not add `DELEGATE_CODE_AGENT`.
- Modify `StateDeltaFieldEnumVO.java`
  - Add `delegateAgentsRequest`.
- Modify `StateDeltaScopeRules.java`
  - Allow only `delegateAgentsRequest` for `DELEGATE_AGENTS`.
- Modify `RuntimeStateMachine.java`
  - Route `DELEGATE_AGENTS` to `WAITING_CHILDREN`.
- Modify `MainActionHandlerStatusEnumVO.java`
  - Add `WAITING_CHILDREN`.
- Create `AgentDelegationWaitModeEnumVO.java`
  - First version only accepts `WAIT_ALL`.
- Create `ChildAgentRunStatusEnumVO.java`
  - Define `PENDING`, `RUNNING`, `COMMITTED`, `FAILED`, `BLOCKED`.
- Create `DelegateAgentTaskVO.java`
  - Child task id/name/objective/boundary/output/capability request.
- Create `DelegateAgentsRequestVO.java`
  - Wait mode and child tasks.
- Create `ParentChildRunRelationVO.java`
  - Parent-child relation and terminal status.
- Create `AgentDispatchResultVO.java`
  - Child ids, wait mode, and parent ready flag.
- Create `ParentChildRunRegistry.java`
  - In-memory wait set model for tests and first domain slice.
- Create `AgentDispatchRuntime.java`
  - Dispatch children and record commits/failures.
- Create `DelegateAgentsActionHandler.java`
  - Main action handler for `DELEGATE_AGENTS`.
- Modify `ContractValidator.java`
  - Validate `DELEGATE_AGENTS` request: `WAIT_ALL`, non-empty tasks, each task has `taskId`, `name`, `objective`.
- Modify `ActionHandlerTestSupport.java`
  - Register `DelegateAgentsActionHandler`.
- Tests:
  - `MainAgentDelegateAgentsContractTest.java`
  - `AgentDispatchRuntimeTest.java`
  - `DelegateAgentsActionHandlerTest.java`

## Task 1: MainAgent Delegate Contract

- [ ] **Step 1: Write failing contract tests**

Add tests proving valid `DELEGATE_AGENTS` passes and invalid wait/task fields fail.

- [ ] **Step 2: Run tests to verify failure**

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=MainAgentDelegateAgentsContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compile or validation failure because `DELEGATE_AGENTS` is not implemented.

- [ ] **Step 3: Implement enum/scope/validator changes**

Do not expose `DELEGATE_CODE_AGENT`.

- [ ] **Step 4: Run tests to verify pass**

Run the same Maven command. Expected: PASS.

## Task 2: Dispatch Runtime Wait-All Model

- [ ] **Step 1: Write failing dispatch runtime tests**

Tests cover:

- dispatch creates child relations and parent is not ready immediately;
- one child commit does not satisfy wait-all;
- all children terminal satisfies wait-all;
- child failure is terminal but does not fail parent directly.

- [ ] **Step 2: Run tests to verify failure**

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentDispatchRuntimeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compile failure because dispatch classes do not exist.

- [ ] **Step 3: Implement registry/runtime skeleton**

Keep it deterministic and in-memory. Persistence is later work.

- [ ] **Step 4: Run tests to verify pass**

Run the same Maven command. Expected: PASS.

## Task 3: MainActionHandler Integration

- [ ] **Step 1: Write failing handler tests**

Tests cover:

- dispatcher registry has a handler for every action after adding `DELEGATE_AGENTS`;
- `DELEGATE_AGENTS` dispatch returns `WAITING_CHILDREN` and next phase `WAITING_CHILDREN`;
- action effect records child ids.

- [ ] **Step 2: Run tests to verify failure**

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=DelegateAgentsActionHandlerTest,MainActionDispatcherTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: failure because handler does not exist.

- [ ] **Step 3: Implement handler and test support registration**

Handler parses `delegateAgentsRequest` from `stateDelta`; contract validation remains in `ContractValidator`.

- [ ] **Step 4: Run tests to verify pass**

Run the same Maven command. Expected: PASS.

## Task 4: Focused Verification And Commit

- [ ] **Step 1: Run focused tests**

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=MainAgentDelegateAgentsContractTest,AgentDispatchRuntimeTest,DelegateAgentsActionHandlerTest,MainActionDispatcherTest,RuntimeStateMachineTest,MainAgentActionContractTest,SubAgentActionContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/2026-06-04-auto-agent-subagent-harness-phase-3.md \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/runtime \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/agent \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/agent \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/runtime \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/agent \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/contract \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/handler \
  ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/harness \
  ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime \
  ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/handler
git commit -m "agent: add generic subagent dispatch runtime"
```

Do not stage `.idea/vcs.xml` or `rooftop_basil_plan.txt`.

