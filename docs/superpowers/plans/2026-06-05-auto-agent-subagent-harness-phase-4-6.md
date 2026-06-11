# AutoAgent Subagent Harness Phase 4.6 Implementation Plan

> **For agentic workers:** Implement this phase with TDD. Write orchestration tests first, verify they fail, then add the minimal production code needed to pass.

**Goal:** Add a single domain orchestration entry point that runs a generic subagent and projects the terminal child result back into the parent working state.

**Architecture:** Earlier phases produced separate pieces:

- `GenericSubAgentRuntime` runs a child loop and marks `ParentChildRunRegistry`;
- `ParentChildRunRegistry` stores child terminal state;
- `ChildAgentResultProjector` projects terminal child facts into parent worklog/evidence/state view.

This phase wires those pieces into `GenericSubAgentOrchestrator`. It does not introduce real async execution or parent wakeup scheduling. It gives future dispatch/wakeup code one clear method to call when a child is ready to run synchronously in tests or when an async child worker finishes.

---

## Scope

In scope:

- `GenericSubAgentOrchestrationResultVO`;
- `GenericSubAgentOrchestrator`;
- run child via `GenericSubAgentRuntime`;
- fetch/update terminal relation from `ParentChildRunRegistry`;
- project child terminal result into parent `RuntimeExecutionContext`;
- expose whether the parent wait set is satisfied.

Out of scope:

- starting Java async threads;
- real queue/background execution;
- parent runtime resume scheduling;
- child MCP/RAG/ASK_USER execution;
- CodeAgent bridge.

## Required Behavior

### Successful Child Commit

When a generic subagent commits:

- child runtime returns status `COMMITTED`;
- registry relation is terminal committed;
- projector adds child evidence/worklog to parent working state;
- orchestration result reports `parentReady` from registry wait satisfaction;
- parent notebook is not semantically modified.

### Child Failure

When a generic subagent fails:

- child runtime returns status `FAILED`;
- registry relation is terminal failed;
- projector adds child failure evidence/worklog to parent working state;
- parent run is not failed directly;
- orchestration result reports `parentReady` from registry wait satisfaction.

## Verification

Focused tests:

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=GenericSubAgentOrchestratorTest,GenericSubAgentRuntimeTest,SubAgentActionDispatcherTest,ChildAgentResultProjectionTest,AgentDispatchRuntimeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.
