# AutoAgent Subagent Harness Phase 4 Implementation Plan

> **For agentic workers:** Implement this phase with TDD. Write runtime loop tests first, verify they fail for missing classes or behavior, then implement the smallest domain slice that passes.

**Goal:** Add a minimal generic subagent runtime loop that can run a delegated child task with full-context memory and finish with `COMMIT` or `FAIL`.

**Architecture:** This phase creates the generic child runtime entry point, not the full asynchronous scheduler. The runtime uses a deterministic Java loop and a replaceable node port. Each child turn receives its full context, returns one `SubAgentActionVO`, and the runtime appends the observed action to the child full-context store. Terminal child results are recorded in `ParentChildRunRegistry`; parent projection/wakeup remains handled by the Phase 3/3.5 services.

---

## Scope

In scope:

- generic subagent run command/result value objects;
- generic subagent node port interface;
- deterministic loop with max loop enforcement from `AgentProfile`;
- full-context append for parent task, node actions, continuation facts, commit, and failure;
- registry update for child `COMMIT` and `FAIL`.

Out of scope:

- real LLM node service implementation;
- MCP/RAG/ASK_USER routing from child actions;
- asynchronous thread execution;
- parent wakeup orchestration;
- code agent bridge/internal code agent harness.

## File Structure

- Create `GenericSubAgentRunCommandVO.java`
  - Carries relation, delegated task, effective capabilities, profile, and optional initial context.
- Create `GenericSubAgentRunResultVO.java`
  - Carries child run id, status, commit/failure message, loop count, and full context.
- Create `GenericSubAgentNodePort.java`
  - Domain service port for invoking the generic subagent node.
- Create `GenericSubAgentRuntime.java`
  - Runs the deterministic child loop.
- Add test `GenericSubAgentRuntimeTest.java`.

## Behavior

### COMMIT

When the node returns `COMMIT`:

- append a `COMMIT` full-context entry;
- mark child relation `COMMITTED` in `ParentChildRunRegistry`;
- return `GenericSubAgentRunResultVO` with status `COMMITTED`;
- do not answer the user.

### FAIL

When the node returns `FAIL`:

- append a `FAIL` full-context entry;
- mark child relation `FAILED` in `ParentChildRunRegistry`;
- return `GenericSubAgentRunResultVO` with status `FAILED`;
- parent does not fail directly.

### CONTINUE

When the node returns `CONTINUE`:

- append a `CONTINUE` full-context entry;
- append a deterministic runtime note that no external action was executed in this phase;
- invoke the node again until terminal result or max loop.

### Other Non-Terminal Actions

`CALL_TOOL`, `RETRIEVE_RAG`, and `ASK_USER` are valid subagent actions but their runtime routing is not implemented in this phase. The first loop implementation should treat them as blocked failures with explicit failure messages so the parent can replan later. Real routing belongs in the next child-runtime phase.

### Max Loop

If max loop is exceeded:

- mark child relation `FAILED`;
- append `FAIL`;
- return failure message `Generic subagent exceeded max loop count.`

## Verification

Focused tests:

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=GenericSubAgentRuntimeTest,SubAgentFullContextRecorderTest,AgentDispatchRuntimeTest,ChildAgentResultProjectionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.
