# AutoAgent Subagent Harness Phase 4.5 Implementation Plan

> **For agentic workers:** Implement this phase with TDD. Write dispatcher/runtime tests first, verify they fail, then add the minimal production code needed to pass.

**Goal:** Refactor `GenericSubAgentRuntime` from hard-coded action branching into a dispatcher/handler based child action routing foundation.

**Architecture:** This phase aligns the generic subagent runtime with the long-lived subagent harness spec's `AgentActionRouter` idea. The runtime remains deterministic and still does not execute MCP/RAG/ASK_USER actions. It delegates every `SubAgentActionVO` to `SubAgentActionDispatcher`; handlers return normalized runtime results, and the runtime decides whether to continue the loop or stop terminally.

This is a foundation slice before real child MCP/RAG/ASK_USER routing.

---

## Scope

In scope:

- `SubAgentActionHandler` interface;
- `SubAgentActionDispatcher`;
- `SubAgentActionHandlerResultVO`;
- handlers for `CONTINUE`, `COMMIT`, `FAIL`;
- explicit blocked handlers for `CALL_TOOL`, `RETRIEVE_RAG`, `ASK_USER`;
- `GenericSubAgentRuntime` uses dispatcher instead of action-specific hard-coded branches;
- full-context entries keep recording node action plus handler/runtime result.

Out of scope:

- real MCP tool execution from subagent;
- real RAG retrieval from subagent;
- child `ASK_USER` pending-input/resume;
- async parent wakeup orchestration;
- CodeAgent bridge implementation.

## Required Behavior

### CONTINUE

- Handler returns non-terminal `CONTINUE`;
- runtime appends a deterministic `HANDLER_RESULT` full-context entry;
- runtime invokes child node again.

### COMMIT

- Handler validates commit payload exists;
- handler marks registry relation `COMMITTED`;
- runtime appends `HANDLER_RESULT` and terminal `COMMIT`;
- runtime returns `GenericSubAgentRunResultVO` with status `COMMITTED`.

### FAIL

- Handler extracts message/reason from action input;
- handler marks registry relation `FAILED`;
- runtime appends `HANDLER_RESULT` and terminal `FAIL`;
- parent is not failed directly.

### CALL_TOOL / RETRIEVE_RAG / ASK_USER

- Dispatch succeeds through a registered handler;
- handler returns terminal child failure with message:
  - `Generic subagent action routing is not implemented for CALL_TOOL.`
  - `Generic subagent action routing is not implemented for RETRIEVE_RAG.`
  - `Generic subagent action routing is not implemented for ASK_USER.`
- This preserves an honest boundary until these actions are wired into real runtime modules.

## Verification

Focused tests:

```bash
mvn -q -pl ai-agent-station-study-app -am "-Dtest=SubAgentActionDispatcherTest,GenericSubAgentRuntimeTest,SubAgentFullContextRecorderTest,AgentDispatchRuntimeTest,ChildAgentResultProjectionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.
