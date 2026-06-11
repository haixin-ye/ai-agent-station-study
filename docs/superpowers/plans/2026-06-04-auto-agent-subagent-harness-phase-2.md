# AutoAgent Subagent Harness Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the generic subagent action contract, `COMMIT` payload model, and full-context run memory recorder without enabling MainAgent delegation yet.

**Architecture:** This phase keeps MainAgent untouched. Generic subagents get their own action enum, action VO, contract validation method, output mapper path, and full-context recorder. Runtime dispatch and parent-child persistence remain Phase 3 work.

**Tech Stack:** Java 17, Maven, JUnit 4, FastJSON, Lombok.

---

## File Structure

- Modify `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/contract/AgentComponentCodeEnumVO.java`
  - Add `GENERIC_SUB_AGENT`.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/agent/SubAgentActionTypeEnumVO.java`
  - Define `CALL_TOOL`, `RETRIEVE_RAG`, `ASK_USER`, `CONTINUE`, `COMMIT`, `FAIL`.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/agent/SubAgentCommitStatusEnumVO.java`
  - Define `SUCCESS`, `PARTIAL`, `BLOCKED`, `FAILED`.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/agent/SubAgentCommitVO.java`
  - Structured child-to-parent commit payload.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/agent/SubAgentActionVO.java`
  - Generic subagent structured action output.
- Modify `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/contract/ContractRegistry.java`
  - Register `GENERIC_SUB_AGENT` with `SubAgentActionContract`, `generic-sub-agent-action-v1`.
- Modify `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/contract/ContractValidator.java`
  - Add `validateSubAgentAction(String rawOutput)`.
  - Reject `FINAL`.
  - Require `commit` fields for `COMMIT`.
- Modify `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/invocation/NodeOutputMapper.java`
  - Map `GENERIC_SUB_AGENT` to `SubAgentActionVO`.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/agent/SubAgentFullContextEntryVO.java`
  - One durable ordered entry in a generic subagent full-context run.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/agent/SubAgentFullContextVO.java`
  - Holds child run id, parent run id, task id, and ordered entries.
- Create `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/agent/SubAgentFullContextRecorder.java`
  - Deterministic append-only recorder for full-context entries.
- Create test `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/harness/SubAgentActionContractTest.java`
  - Contract validation tests.
- Create test `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/harness/SubAgentFullContextRecorderTest.java`
  - Full-context append ordering tests.

## Task 1: SubAgent Contract

- [ ] **Step 1: Write failing contract tests**

Create `SubAgentActionContractTest` with tests:

- registry contains `GENERIC_SUB_AGENT`;
- validator accepts valid `COMMIT`;
- validator rejects `FINAL`;
- validator rejects `COMMIT` without `taskId`;
- validator rejects `COMMIT` without `status`;
- validator rejects `COMMIT` without result text.

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=SubAgentActionContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compile failure because subagent contract classes do not exist.

- [ ] **Step 3: Implement enum, VO, registry, validator, and mapper**

Keep this implementation independent from MainAgent's contract path. Do not add `COMMIT` to `MainAgentActionTypeEnumVO`.

- [ ] **Step 4: Run tests and verify pass**

Run the same Maven command. Expected: PASS.

## Task 2: Full-Context Recorder

- [ ] **Step 1: Write failing recorder tests**

Create `SubAgentFullContextRecorderTest` with tests:

- initial context includes parent task as first entry;
- appended tool/result/ask/commit entries preserve sequence;
- returned entry list is immutable from caller mutation.

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=SubAgentFullContextRecorderTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compile failure because full-context classes do not exist.

- [ ] **Step 3: Implement full-context VO and recorder**

Recorder is in-memory and deterministic for now. Persistence adapter is later work.

- [ ] **Step 4: Run tests and verify pass**

Run the same Maven command. Expected: PASS.

## Task 3: Focused Verification And Commit

- [ ] **Step 1: Run focused Phase 2 tests**

Run:

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=SubAgentActionContractTest,SubAgentFullContextRecorderTest,AgentProfileRegistryTest,AgentCapabilityResolverTest,MainAgentActionContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 2: Commit**

Stage only Phase 2 files:

```bash
git add docs/superpowers/plans/2026-06-04-auto-agent-subagent-harness-phase-2.md \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/contract/AgentComponentCodeEnumVO.java \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/agent \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/agent \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/agent \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/contract/ContractRegistry.java \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/contract/ContractValidator.java \
  ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/invocation/NodeOutputMapper.java \
  ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/harness
git commit -m "agent: add generic subagent action contract"
```

Do not stage `.idea/vcs.xml` or `rooftop_basil_plan.txt`.

