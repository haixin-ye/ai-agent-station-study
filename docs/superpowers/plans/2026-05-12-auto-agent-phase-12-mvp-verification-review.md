# AutoAgent Phase 12 MVP Verification Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove that the rebuilt AutoAgent MVP behaves as the canonical spec describes before it is treated as usable.

**Architecture:** Phase 12 does not add new product behavior. It verifies the finished implementation through contract tests, scenario tests, safety-property tests, API/SSE checks, debug-boundary checks, and a written review report. The phase must fail loudly when the normal route leaks old Node1-4 behavior, raw model output, debug trace, unguarded final content, unauthorized tool execution, or unbounded context.

**Tech Stack:** Java 17, Spring Boot Test, JUnit, Maven, MyBatis, Spring AI MCP, Server-Sent Events, deterministic fake node clients, deterministic fake MCP clients.

---

## 0. Source Of Truth

Read these documents before starting:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`
- `docs/superpowers/plans/2026-05-12-auto-agent-main-loop-harness-master-plan.md`
- `docs/superpowers/plans/2026-05-12-auto-agent-phase-0-1-contract-skeleton.md`
- `docs/superpowers/plans/2026-05-12-auto-agent-phase-2-persistence-repository.md`
- `docs/superpowers/plans/2026-05-12-auto-agent-phase-3-prompt-node-invocation.md`
- `docs/superpowers/plans/2026-05-12-auto-agent-phase-4-context-artifact-memory.md`
- `docs/superpowers/plans/2026-05-12-auto-agent-phase-5-runtime-pending-input.md`
- `docs/superpowers/plans/2026-05-12-auto-agent-phase-6-main-action-handlers.md`
- `docs/superpowers/plans/2026-05-12-auto-agent-phase-7-rag-runtime-verification.md`
- `docs/superpowers/plans/2026-05-12-auto-agent-phase-8-tool-mcp-permission-approval.md`
- `docs/superpowers/plans/2026-05-12-auto-agent-phase-9-final-response-guard-repair.md`
- `docs/superpowers/plans/2026-05-12-auto-agent-phase-10-api-sse-debug-mock.md`
- `docs/superpowers/plans/2026-05-12-auto-agent-phase-11-old-harness-isolation-cleanup.md`

Phase 12 must not reinterpret architecture. If a test expectation conflicts with the canonical spec, update the test expectation or stop and ask for a spec correction.

## 1. Verification Scope

Phase 12 must verify these MVP boundaries:

- Main loop route is the normal route.
- Old Node1-4 harness is isolated from normal chat execution.
- `MainAgentNode` emits actions only and never calls MCP tools directly.
- Runtime owns run lifecycle, loop control, persistence, action dispatch, pending input, verifier routing, and final delivery.
- RAG verification is triggered only by factual `ragWasUsed=true`.
- Tool verification proves real invocation and basic receipt status, not full business correctness.
- High-risk tool approval uses `SINGLE_CHOICE` with `allowFreeText=false`.
- User-facing output only comes from `FinalDeliveryService`.
- Normal frontend does not receive debug trace, raw model output, raw verifier payloads, or internal JSON.
- Debug data is persisted and available only through debug APIs or debug SSE.
- Context planning prevents prompt budget overflow and preserves artifact references.

## 2. Verification File Map

Create these review artifacts:

- Create: `docs/superpowers/reviews/2026-05-12-auto-agent-mvp-verification-report.md`
- Create: `docs/superpowers/reviews/2026-05-12-auto-agent-known-gaps-backlog.md`

Create or complete these test resources:

- Create: `ai-agent-station-study-app/src/test/resources/auto-agent/mvp-scenarios/direct-answer.json`
- Create: `ai-agent-station-study-app/src/test/resources/auto-agent/mvp-scenarios/artifact-create-update.json`
- Create: `ai-agent-station-study-app/src/test/resources/auto-agent/mvp-scenarios/rag-answer-verified.json`
- Create: `ai-agent-station-study-app/src/test/resources/auto-agent/mvp-scenarios/tool-approval-execute.json`
- Create: `ai-agent-station-study-app/src/test/resources/auto-agent/mvp-scenarios/tool-approval-reject.json`
- Create: `ai-agent-station-study-app/src/test/resources/auto-agent/mvp-scenarios/clarify-artifact-reference.json`
- Create: `ai-agent-station-study-app/src/test/resources/auto-agent/mvp-scenarios/final-guard-repair.json`
- Create: `ai-agent-station-study-app/src/test/resources/auto-agent/mvp-scenarios/context-budget-compaction.json`

Create or complete these tests:

- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/mvp/AutoAgentMvpScenarioTest.java`
- Test helper: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/mvp/MvpScenarioHarness.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/mvp/AutoAgentSafetyPropertyTest.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/trigger/agent/AutoAgentSseContractTest.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/trigger/agent/AutoAgentDebugApiBoundaryTest.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/runtime/AutoAgentOldHarnessIsolationTest.java`

If earlier phases already created any of these files, update the existing file instead of creating a duplicate.

## 3. Scenario Fixture Contract

Every file under `mvp-scenarios` must follow this shape:

```json
{
  "scenarioId": "direct-answer",
  "description": "Simple user request that needs no RAG, no MCP tool, and no pending input.",
  "userMessage": "用两句话解释 RAG 是什么",
  "given": {
    "historyMessages": [],
    "artifacts": [],
    "memories": [],
    "ragHits": [],
    "toolReceipts": []
  },
  "fakeNodeResponses": [
    {
      "componentCode": "CONTEXT_PLANNER",
      "response": {
        "status": "READY",
        "selectedContextRefs": [],
        "reason": "No external context required."
      }
    },
    {
      "componentCode": "MAIN_AGENT",
      "response": {
        "action": "FINAL",
        "content": {
          "text": "RAG 是检索增强生成：先从知识库检索相关资料，再让大模型基于资料回答问题。它能降低幻觉并提高回答的可追溯性。"
        },
        "stateDelta": {
          "finalCandidate": {
            "text": "RAG 是检索增强生成：先从知识库检索相关资料，再让大模型基于资料回答问题。它能降低幻觉并提高回答的可追溯性。"
          }
        }
      }
    }
  ],
  "expected": {
    "finalStatus": "COMPLETED",
    "requiredEvents": ["RUN_STARTED", "NODE_STARTED", "FINAL_DELIVERED"],
    "forbiddenNormalPayloadFragments": ["rawResult", "trace", "node1", "node2", "node3", "node4"],
    "requiredDebugTraceTypes": ["NODE_INPUT", "NODE_OUTPUT", "FINAL_GUARD_RESULT"],
    "ragWasUsed": false,
    "toolWasInvoked": false,
    "pendingInputCreated": false
  }
}
```

Fields:

- `scenarioId`: stable kebab-case id.
- `description`: one sentence.
- `userMessage`: original user message.
- `given.historyMessages`: same-session prior messages needed for memory tests.
- `given.artifacts`: prior artifact summaries, references, and optional content.
- `given.memories`: recalled memory summaries.
- `given.ragHits`: fake RAG runtime result set.
- `given.toolReceipts`: fake MCP runtime receipt set.
- `fakeNodeResponses`: deterministic fake outputs returned by `NodeClientPort`.
- `expected`: assertions for final status, event stream, debug boundary, RAG flags, tool flags, and pending-input behavior.

## 4. Required MVP Scenarios

### 4.1 Direct Answer

Fixture: `direct-answer.json`

Required flow:

1. User asks a simple non-tool, non-RAG question.
2. ContextPlanner returns `READY`.
3. MainAgent returns `FINAL`.
4. FinalResponseGuard passes.
5. Runtime persists final response and emits `FINAL_DELIVERED`.

Assertions:

- `ragWasUsed=false`
- no tool call row exists
- no pending input row exists
- normal SSE contains no raw action JSON
- debug trace contains bounded node input and output records

### 4.2 Artifact Create Then Update

Fixture: `artifact-create-update.json`

Required flow:

1. User asks for a long text artifact.
2. MainAgent emits `CREATE_ARTIFACT`.
3. Runtime stores artifact content and returns artifact summary to MainAgent in the next loop.
4. User later asks to revise the article.
5. ContextPlanner selects the artifact reference.
6. ArtifactContextPolicy loads full content because the request requires editing.
7. MainAgent emits `UPDATE_ARTIFACT`.
8. Runtime stores a new artifact version.
9. FinalResponseGuard delivers a concise final message.

Assertions:

- artifact id is internal and stable
- artifact version increments
- `StateView` contains selected full artifact content only when policy allows it
- normal SSE exposes artifact summary and artifact id, not internal payload store details

### 4.3 RAG Answer Verified

Fixture: `rag-answer-verified.json`

Required flow:

1. MainAgent emits `RETRIEVE_RAG`.
2. Runtime accepts the action and immediately sets `RagState.ragWasUsed=true`.
3. Runtime executes `RagRuntime`.
4. Runtime stores RAG query, hits, payload refs, and evidence summaries.
5. MainAgent receives bounded RAG evidence in the next loop.
6. MainAgent emits `FINAL`.
7. Runtime invokes `RagVerifier` because `ragWasUsed=true`.
8. RagVerifier returns `PASSED`.
9. FinalResponseGuard delivers the answer.

Assertions:

- no keyword scan is used to decide verifier invocation
- `RagVerifierInput` contains user request, final answer candidate, RAG evidence summaries, and payload refs
- final answer cannot include unsupported claims marked by fixture
- debug trace has RAG runtime and verifier records

### 4.4 Tool Approval And Execute

Fixture: `tool-approval-execute.json`

Required flow:

1. MainAgent emits `CALL_TOOL`.
2. Runtime resolves capability and materializes arguments.
3. PermissionPolicy returns `NEEDS_APPROVAL`.
4. UserInteractionManager creates `TOOL_APPROVAL` pending input.
5. Frontend receives `SINGLE_CHOICE` with approve/reject options and `allowFreeText=false`.
6. User clicks approve.
7. Runtime resumes the same run.
8. ToolRuntime re-checks permission and invokes MCP through configured Spring AI MCP client.
9. Runtime captures typed receipt.
10. ToolVerifier validates real invocation proof and basic receipt status.
11. Tool evidence is added to the next MainAgent loop.
12. FinalResponseGuard delivers final answer.

Assertions:

- approval key is idempotent
- free text cannot approve execution
- tool execution never occurs before approval
- `tool_invocation_id` exists and is linked to receipt
- ToolVerifier does not assert business-specific success beyond receipt proof

### 4.5 Tool Approval Reject

Fixture: `tool-approval-reject.json`

Required flow:

1. MainAgent emits `CALL_TOOL`.
2. Runtime creates tool approval pending input.
3. User clicks reject.
4. Runtime records rejection.
5. ToolRuntime is not invoked.
6. Runtime resumes MainAgent with rejection evidence.
7. MainAgent emits a user-facing final explanation.
8. FinalResponseGuard delivers the explanation.

Assertions:

- no MCP call is made
- final answer does not claim the tool action was completed
- debug trace records approval rejection
- normal SSE shows clean visible status only

### 4.6 Clarify Artifact Reference

Fixture: `clarify-artifact-reference.json`

Required flow:

1. User says "把那个文章发到 CSDN".
2. Runtime preselects candidate artifacts.
3. ContextPlanner cannot uniquely resolve "那个文章".
4. ContextPlanner returns clarification request.
5. UserInteractionManager creates `CONTEXT_CLARIFICATION` pending input.
6. Frontend renders options such as latest artifact, named artifact, and free text if enabled.
7. User selects an option.
8. Runtime resumes ContextPlanner continuation.
9. ContextPlanner returns `READY` with selected artifact reference.
10. ArtifactResolver materializes content according to ToolArgumentMaterializer policy.
11. MainAgent emits `CALL_TOOL`.

Assertions:

- user answer is Java-normalized as `OPTION_CLICK`
- no LLM-based user input resolver is called
- pending input remains attached to the same run
- if the user cancels, run ends as cancelled instead of silently restarting

### 4.7 Final Guard Repair

Fixture: `final-guard-repair.json`

Required flow:

1. MainAgent emits `FINAL` containing internal terms such as trace/node/raw JSON.
2. FinalResponseGuard rejects with `FINAL_INTERNAL_TRACE_LEAK` or `FINAL_RAW_JSON_LEAK`.
3. Runtime invokes `REPAIR_FINAL`.
4. Repair node receives only the failure reason, user request, and final candidate context allowed by spec.
5. Repair node returns cleaned final candidate.
6. FinalResponseGuard passes.
7. FinalDeliveryService persists and emits final response.

Assertions:

- repair budget is consumed once per repair attempt
- raw failed answer is stored only in debug payload storage
- normal frontend never sees the failed answer
- if repair budget is exhausted, fixed safe fallback is delivered through FinalDeliveryService

### 4.8 Context Budget Compaction

Fixture: `context-budget-compaction.json`

Required flow:

1. User request requires several prior messages, an artifact, and RAG evidence.
2. Runtime builds compact candidates first.
3. ContextPlanner selects candidate refs.
4. ContextBudgetPolicy decides which refs can be loaded as full content, snippets, or summaries.
5. MainAgentStateView is built within budget.
6. MainAgent receives stable refs for omitted full content.

Assertions:

- prompt budget limit is never exceeded by known token estimator
- large artifact content is loaded only when required
- selected omitted content remains reachable through refs
- debug trace records budget decisions

## 5. Safety Property Tests

Create `AutoAgentSafetyPropertyTest.java` with tests for these properties:

```java
package yhx.com.test.domain.agent.mvp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutoAgentSafetyPropertyTest {

    @Test
    void normalFinalResponseMustNotContainInternalHarnessTerms() {
        String normalPayload = "{\"type\":\"final\",\"content\":\"RAG 是检索增强生成。\"}";

        assertThat(normalPayload)
                .doesNotContain("rawResult")
                .doesNotContain("node1")
                .doesNotContain("node2")
                .doesNotContain("node3")
                .doesNotContain("node4")
                .doesNotContain("trace")
                .doesNotContain("ContractRegistry")
                .doesNotContain("DynamicContext");
    }

    @Test
    void toolApprovalPayloadMustNotAllowFreeText() {
        String approvalPayload = "{\"inputMode\":\"SINGLE_CHOICE\",\"allowFreeText\":false}";

        assertThat(approvalPayload).contains("\"inputMode\":\"SINGLE_CHOICE\"");
        assertThat(approvalPayload).contains("\"allowFreeText\":false");
    }

    @Test
    void ragVerifierMustBeFactTriggeredByRagWasUsed() {
        boolean ragWasUsed = true;
        boolean finalTextMentionsKnowledgeBase = false;

        boolean shouldVerify = ragWasUsed;

        assertThat(shouldVerify).isTrue();
        assertThat(finalTextMentionsKnowledgeBase).isFalse();
    }
}
```

When real domain classes exist, replace string assertions with DTO and service assertions while preserving these exact safety meanings.

## 6. Test Command Matrix

Run targeted tests by concern. Do not start with full project tests unless targeted tests are already passing.

### 6.1 Contract And Parser

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=MainAgentActionContractTest,ContextPlannerContractTest,RawOutputParserTest,ContractRegistryTest" test
```

Expected:

- all contract examples parse
- invalid action JSON fails with deterministic contract error
- repairable raw output is normalized by the parser
- component-code contract lookup succeeds for all MVP components

### 6.2 Persistence And Repository

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentRunRepositoryTest,AgentArtifactRepositoryTest,AgentPendingInputRepositoryTest,AgentTraceRepositoryTest" test
```

Expected:

- run, message, trace, artifact, evidence, RAG, tool, approval, and final response records persist
- large payloads use payload refs where required
- normal payload records and debug payload records remain separable

### 6.3 Prompt And Node Invocation

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=PromptAssemblyServiceTest,NodeInvocationPipelineTest,NodeContractRepairTest" test
```

Expected:

- layered prompts include Java-owned stable rules and DB-owned editable role content
- output contracts are appended from ContractRegistry
- NodeInvocationPipeline stores input/output trace safely
- bounded repair triggers only within configured repair budget

### 6.4 Context, Artifact, And Memory

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=ContextPlannerRuntimeTest,ArtifactResolverTest,MemoryRetrieverTest,ContextBudgetPolicyTest" test
```

Expected:

- ContextPlanner receives compact candidates, not raw full trace
- artifact full content is loaded only when policy requires it
- memory candidates are recalled and summarized within budget
- oversized context becomes clarification or compaction, not overflow

### 6.5 Runtime And Pending Input

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=AutoAgentRuntimeStateMachineTest,UserInteractionManagerTest,PendingInputResumeTest,LoopPolicyTest" test
```

Expected:

- run phases transition deterministically
- pending input pauses the same run
- option click and free text are Java-normalized into `UserAnswer`
- cancel terminates the run cleanly
- loop limit and recovery limit prevent infinite execution

### 6.6 Main Action Handlers

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=MainAgentActionDispatcherTest,CreateArtifactActionHandlerTest,RetrieveRagActionHandlerTest,CallToolActionHandlerTest,FinalActionHandlerTest" test
```

Expected:

- each MainAgent action routes to exactly one handler
- `FINAL` always goes to FinalDeliveryService
- `RETRIEVE_RAG` sets `ragWasUsed=true`
- `CALL_TOOL` creates tool execution intent and never mounts MCP on MainAgentNode

### 6.7 RAG Runtime And Verifier

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RagRuntimeTest,RagExecutionAndVerificationTest,RagVerifierInputBuilderTest" test
```

Expected:

- RAG query and hits persist
- `RagVerifier` runs only when `ragWasUsed=true`
- keyword/citation wording never triggers verifier by itself
- failed grounding routes to deterministic recovery

### 6.8 Tool, MCP, Permission, And Approval

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=CapabilityRegistryTest,ToolPermissionPolicyTest,ToolApprovalPendingInputTest,ToolRuntimeAndVerificationTest,ToolArgumentMaterializerTest" test
```

Expected:

- capability yml loads and validates
- unknown capability fails closed
- high-risk tools require approval
- free text cannot approve tool execution
- MCP adapter returns typed receipt
- ToolVerifier proves invocation and basic receipt status only

### 6.9 Final Guard And Repair

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=FinalResponseGuardTest,FinalDeliveryServiceTest,FinalRepairFlowTest" test
```

Expected:

- raw JSON, internal trace, empty final, and unsupported tool/RAG claims are rejected
- repair receives bounded failure context
- final response is persisted only after guard success or safe fallback
- normal SSE receives only guarded final response

### 6.10 API, SSE, Debug, Mock, And Legacy Isolation

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=AutoAgentApiContractTest,AutoAgentSseContractTest,AutoAgentDebugApiBoundaryTest,AutoAgentMockScenarioTest,AutoAgentOldHarnessIsolationTest" test
```

Expected:

- normal API and SSE expose clean user-visible payloads
- debug API and debug SSE require debug mode and expose bounded debug records
- mock scenarios can drive frontend pending input, approval, final, artifact, and error events
- old Node1-4 code is never reached by the normal route

### 6.11 Compile Gate

Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am -DskipTests compile
```

Expected:

- compilation succeeds for all modules required by the app module

## 7. MVP Scenario Test Skeleton

Create `AutoAgentMvpScenarioTest.java` with this structure:

```java
package yhx.com.test.domain.agent.mvp;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AutoAgentMvpScenarioTest {

    private final MvpScenarioHarness harness = new MvpScenarioHarness();

    @ParameterizedTest
    @ValueSource(strings = {
            "direct-answer",
            "artifact-create-update",
            "rag-answer-verified",
            "tool-approval-execute",
            "tool-approval-reject",
            "clarify-artifact-reference",
            "final-guard-repair",
            "context-budget-compaction"
    })
    void mvpScenarioShouldSatisfyExpectedFlow(String scenarioId) {
        MvpScenarioResult result = harness.run(scenarioId);

        assertThat(result.finalStatus()).isEqualTo(result.expectedFinalStatus());
        assertThat(result.normalPayload()).doesNotContain(result.forbiddenNormalFragments());
        assertThat(result.missingRequiredEvents()).isEmpty();
        assertThat(result.safetyViolations()).isEmpty();
    }
}
```

Create `MvpScenarioHarness.java` with this structure:

```java
package yhx.com.test.domain.agent.mvp;

import java.util.List;

final class MvpScenarioHarness {

    MvpScenarioResult run(String scenarioId) {
        MvpScenarioFixture fixture = MvpScenarioFixtureLoader.load(scenarioId);
        MvpRuntimeResult runtimeResult = executeRuntimeWithFakePorts(fixture);

        return new MvpScenarioResult(
                runtimeResult.finalStatus(),
                fixture.expectedFinalStatus(),
                runtimeResult.normalPayload(),
                fixture.forbiddenNormalFragments(),
                runtimeResult.missingRequiredEvents(fixture.requiredEvents()),
                runtimeResult.safetyViolations(fixture)
        );
    }

    private MvpRuntimeResult executeRuntimeWithFakePorts(MvpScenarioFixture fixture) {
        FakeNodeClientPort nodeClientPort = FakeNodeClientPort.fromFixture(fixture);
        FakeRagRuntimePort ragRuntimePort = FakeRagRuntimePort.fromFixture(fixture);
        FakeMcpClientRegistry mcpClientRegistry = FakeMcpClientRegistry.fromFixture(fixture);

        return MvpRuntimeFixtureRunner.builder()
                .nodeClientPort(nodeClientPort)
                .ragRuntimePort(ragRuntimePort)
                .mcpClientRegistry(mcpClientRegistry)
                .fixture(fixture)
                .build()
                .run();
    }
}

record MvpScenarioResult(
            String finalStatus,
            String expectedFinalStatus,
            String normalPayload,
            String[] forbiddenNormalFragments,
            List<String> missingRequiredEvents,
            List<String> safetyViolations
    ) {
}
```

During implementation, create the named fake ports and fixture runner in the same test package. The harness must wire:

- fake `NodeClientPort`
- fake `RagRuntimePort`
- fake `McpClientRegistry`
- in-memory or test database repositories
- real Runtime action dispatcher
- real UserInteractionManager
- real FinalResponseGuard
- real SSE event mapper

## 8. Verification Report Template

Create `docs/superpowers/reviews/2026-05-12-auto-agent-mvp-verification-report.md` with this shape:

```markdown
# AutoAgent MVP Verification Report

## Summary

- Date:
- Branch:
- Commit:
- Canonical spec:
- Result: PASS / FAIL

## Commands Run

| Command | Result | Notes |
| --- | --- | --- |
| `mvn -q -pl ai-agent-station-study-app -am "-Dtest=..." test` | PASS / FAIL | |

## Scenario Results

| Scenario | Result | Evidence |
| --- | --- | --- |
| direct-answer | PASS / FAIL | |
| artifact-create-update | PASS / FAIL | |
| rag-answer-verified | PASS / FAIL | |
| tool-approval-execute | PASS / FAIL | |
| tool-approval-reject | PASS / FAIL | |
| clarify-artifact-reference | PASS / FAIL | |
| final-guard-repair | PASS / FAIL | |
| context-budget-compaction | PASS / FAIL | |

## Safety Properties

| Property | Result | Evidence |
| --- | --- | --- |
| Old harness isolated from normal route | PASS / FAIL | |
| No raw model output in normal frontend | PASS / FAIL | |
| Final response always guarded | PASS / FAIL | |
| RAG verifier fact-triggered by ragWasUsed | PASS / FAIL | |
| Tool approval rejects free text | PASS / FAIL | |
| Tool verifier checks invocation proof only | PASS / FAIL | |
| Pending input resumes same run | PASS / FAIL | |
| Debug data separated from normal API/SSE | PASS / FAIL | |
| Context budget enforced | PASS / FAIL | |

## Defects Found

| Id | Severity | Area | Description | Required Fix |
| --- | --- | --- | --- | --- |

## MVP Decision

State whether the implementation is acceptable as MVP, blocked by defects, or acceptable with documented gaps.
```

## 9. Known Gaps Backlog Template

Create `docs/superpowers/reviews/2026-05-12-auto-agent-known-gaps-backlog.md` with this shape:

```markdown
# AutoAgent Known Gaps Backlog

## Accepted MVP Gaps

| Id | Gap | Why Accepted | Future Phase |
| --- | --- | --- | --- |

## Blockers

| Id | Blocker | Impact | Required Fix |
| --- | --- | --- | --- |

## Deferred Features

| Id | Feature | Reason Deferred |
| --- | --- | --- |
| BACKLOG-001 | LLM safety guard beyond Java FinalResponseGuard | MVP uses Java rule-based guard only. |
| BACKLOG-002 | Business-specific tool result verification | MVP ToolVerifier validates real invocation and basic receipt status only. |
| BACKLOG-003 | Sub-agent delegation | Design reserved but not in MVP. |
| BACKLOG-004 | Coding agent specialization | Future capability family; not hard-coded into MVP Runtime. |
```

## 10. Review Checklist

Before declaring Phase 12 complete:

- [ ] The English canonical spec was used as the only architecture source.
- [ ] All Phase 0-11 targeted tests were run or explicitly marked blocked with reason.
- [ ] Every required MVP scenario fixture exists.
- [ ] Every required MVP scenario passed through Runtime, not mocked around Runtime.
- [ ] Normal API and normal SSE were inspected for internal leakage.
- [ ] Debug API and debug SSE were inspected for bounded trace access.
- [ ] Old Node1-4 normal-route isolation was proven by test.
- [ ] Tool approval was tested for approve, reject, cancel, and free-text rejection.
- [ ] RAG verifier invocation was tested with `ragWasUsed=true` and `ragWasUsed=false`.
- [ ] FinalResponseGuard was tested for pass, repair, and fallback.
- [ ] Context budget compaction was tested with a large artifact or large memory candidate.
- [ ] Verification report was written.
- [ ] Known gaps backlog was written.

## 11. Defect Severity Rules

Use these severity levels:

- `BLOCKER`: MVP cannot be accepted. Examples: old Node1-4 normal route still active, unguarded final response returned, high-risk tool executes without approval, raw model output leaks to normal frontend.
- `HIGH`: MVP can run but violates important architecture. Examples: debug endpoint lacks debug-mode gate, RAG verifier uses keyword trigger, pending input creates a new run instead of resuming same run.
- `MEDIUM`: behavior is usable but incomplete. Examples: a mock scenario lacks one expected event, debug trace misses a non-critical field, known gap not documented.
- `LOW`: cleanup issue. Examples: naming mismatch in a non-public test helper, duplicate test fixture text, review report wording issue.

Rules:

- Any `BLOCKER` fails Phase 12.
- Any unresolved `HIGH` requires explicit user acceptance before Phase 12 can pass.
- `MEDIUM` and `LOW` items may be recorded in known gaps if they do not weaken MVP boundaries.

## 12. Worker Split Guidance

If using subagents, split by independent verification areas:

- Worker A owns contract/parser/prompt/node-invocation tests.
- Worker B owns persistence/repository/debug-data boundary tests.
- Worker C owns runtime/action/pending-input tests.
- Worker D owns RAG/tool/final-guard tests.
- Worker E owns API/SSE/mock/frontend contract tests and old-harness isolation.

Workers are not alone in the codebase. They must not rewrite shared Runtime contracts without reading the canonical spec and the already completed phase plans. They must list every file they changed in their final answer.

## 13. Phase 12 Acceptance

Phase 12 is complete when:

- Targeted verification commands have been run.
- Scenario tests cover all required MVP paths.
- The verification report exists and states PASS or lists exact blockers.
- The known gaps backlog exists and separates accepted gaps from blockers.
- The normal user route is proven clean, guarded, and isolated from the old harness.
- Debug observability remains available without polluting normal frontend behavior.

Do not mark the full AutoAgent redesign complete until Phase 12 passes or the user explicitly accepts the documented blockers.
