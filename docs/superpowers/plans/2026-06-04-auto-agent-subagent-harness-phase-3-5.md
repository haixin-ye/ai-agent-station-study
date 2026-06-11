# AutoAgent Subagent Harness Phase 3.5 Implementation Plan

> **For agentic workers:** Implement this phase with TDD. Write the projection tests first, verify they fail for the expected missing behavior, then add the smallest production code needed to pass.

**Goal:** Project generic child agent `COMMIT` and terminal failure results back into the parent run as deterministic Runtime facts.

**Architecture:** A child agent does not update the parent notebook directly and does not answer the user. When it finishes, Runtime converts the child terminal result into a parent-readable action effect: one ordered worklog item plus one materialized evidence entry. The existing `RunWorkingStateManager.apply/project` pipeline then makes those facts visible in the next `MainAgentStateView`. MainAgent reads notebook + worklog + evidencePack and decides the next PER step.

---

## Scope

In scope:

- successful generic child `COMMIT` projection;
- terminal generic child `FAIL` projection;
- deterministic evidence content from child payload or failure message;
- parent worklog metadata linking parent run, child run, task id, child name, and child status;
- no semantic notebook mutation by Runtime.

Out of scope:

- real asynchronous child execution;
- persistence adapters for parent-child relations;
- child `ASK_USER` resume wiring;
- code agent internal harness;
- `DELEGATE_CODE_AGENT`.

## File Structure

- Create `ChildAgentResultProjector.java`
  - Domain service under `domain/agent/service/agent`.
  - Converts `ParentChildRunRelationVO` terminal state into a `MainActionHandlerResult`.
  - Reuses `DELEGATE_AGENTS` as the parent action category for projected child results.
- Add tests:
  - `ChildAgentResultProjectionTest.java`
  - Verify successful commit projection.
  - Verify failed child projection.

## Projection Contract

For a child commit:

- worklog action type: `DELEGATE_AGENTS`;
- source component: remains Runtime-managed through `RunWorkingStateManager`, with child metadata carried in `request.raw` / `result.raw` / evidence metadata;
- status: child commit status when present, otherwise `COMMITTED`;
- evidence type: `SUB_AGENT`;
- evidence content: child `result` and `detail` preserved without LLM-generated summaries;
- evidence metadata: parent run id, child run id, task id, child name, child status, evidence refs, inspected resources, assumptions, blockers, suggested parent next step.

For a child failure:

- worklog action type: `DELEGATE_AGENTS`;
- status: `FAILED`;
- evidence type: `SUB_AGENT_FAILURE`;
- evidence content: failure message preserved;
- failure does not directly fail the parent run. Parent gets facts and replans.

## TDD Tasks

- [ ] Write failing `ChildAgentResultProjectionTest` for successful commit projection.
- [ ] Run only the new test and verify failure because projector does not exist.
- [ ] Implement `ChildAgentResultProjector`.
- [ ] Run the new test and verify success.
- [ ] Add failing test for child failure projection.
- [ ] Implement failure projection if needed.
- [ ] Run focused regression tests:

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=ChildAgentResultProjectionTest,RunWorkingStateWorklogProjectionTest,RuntimeWorkingStateProjectionTest,AgentDispatchRuntimeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.
