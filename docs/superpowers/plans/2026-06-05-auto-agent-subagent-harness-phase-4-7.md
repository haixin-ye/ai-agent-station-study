# AutoAgent Subagent Harness Phase 4.7 Implementation Plan

> **For agentic workers:** Implement this phase with TDD. Write the dispatch-to-orchestration test first, verify it fails, then add minimal production code.

**Goal:** Add a synchronous generic subagent dispatch bridge that can dispatch multiple child tasks, run each child through `GenericSubAgentOrchestrator`, project every child result into the parent working state, and report WAIT_ALL readiness.

**Architecture:** This phase connects existing pieces for deterministic tests and future orchestration wiring:

- `AgentDispatchRuntime` creates parent-child relations;
- `ParentChildRunRegistry` stores the wait set;
- `GenericSubAgentOrchestrator` runs one child and projects its terminal result;
- a new bridge runs all dispatched generic children synchronously for now.

This is not the final async scheduler. It is a synchronous bridge so the harness has a coherent end-to-end domain flow before async wakeup/resume is added.

---

## Scope

In scope:

- synchronous generic multi-child orchestration service;
- dispatch request to child relation lookup;
- run each child through `GenericSubAgentOrchestrator`;
- return child orchestration results and final parentReady;
- test parent state view contains multiple child worklog/evidence items.

Out of scope:

- background threads or queues;
- SSE events;
- persistent parent-child repository adapters;
- parent runtime resume scheduling;
- MCP/RAG/ASK_USER execution inside children.

## Required Behavior

Given a parent `RuntimeExecutionContext` and `DelegateAgentsRequestVO` with two tasks:

1. dispatch creates two child relations;
2. bridge runs both children synchronously through node ports;
3. each child commits/fails terminally;
4. parent working state receives one worklog/evidence item per child;
5. `parentReady` is true only after all child relations are terminal.

## Verification

Focused tests:

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=GenericSubAgentDispatchOrchestratorTest,GenericSubAgentOrchestratorTest,AgentDispatchRuntimeTest,ChildAgentResultProjectionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.
