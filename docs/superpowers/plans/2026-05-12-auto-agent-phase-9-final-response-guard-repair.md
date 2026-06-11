# AutoAgent Phase 9 Final Response Guard Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the final delivery path, Java MVP `FinalResponseGuard`, bounded final repair, and guarded assistant message creation.

**Architecture:** No handler, verifier, trace, raw model output, tool receipt, or runtime summary may create a normal assistant message directly. All user-visible final text must pass through `FinalDeliveryService`, optional RAG verification when `ragWasUsed=true`, Java guard checks, bounded `REPAIR_FINAL`, `FinalResponse` persistence, and then assistant message creation.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Maven multi-module project, Lombok, Fastjson2, JUnit4, DDD package layout under `yhx.com`.

---

## 0. Execution Rules

- Start this phase only after Phase 6 action handlers compile.
- If Phase 7 RAG verifier exists, integrate it before final guard.
- Do not expose final guard, verifier, repair, prompt, trace, contract, or raw JSON internals in normal final text.
- Do not append assistant messages outside `FinalDeliveryService`.
- Do not implement LLM safety/policy/quality guards in MVP.
- Do not call tools or RAG from repair.
- Do not commit unless the user explicitly asks.

## 1. Source Of Truth

Use the English canonical spec only:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`

Primary spec sections:

- Section 2.7: final answer ownership
- Section 3.5: action routing for final-capable actions
- Section 5.19: `REPAIR_FINAL`
- Section 5.20: `FAIL`
- Section 6.4: FinalResponseGuard MVP pipeline
- Section 6.7: recovery policy
- Section 11: logging, trace, audit, observability
- Section 13.12: Phase 9 implementation tasks

## 2. Phase Boundary

### In Scope

- `FinalDeliveryService`
- `FinalResponseGuard`
- MVP Java guard classes
- `FinalResponseGuardInput` builder
- `FinalResponse` builder/persistence
- final repair invocation using `REPAIR_FINAL`
- repair budget enforcement
- fixed user-safe fallback response
- final assistant message creation after guard pass only
- developer trace and payload refs for guard details
- tests with fake node invocation and fake repositories

### Out Of Scope

- LLM policy moderation
- advanced safety guardrails
- style/quality scoring
- frontend API implementation
- old harness cleanup

## 3. File Map

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/finalresponse/`

Required files:

- `FinalDeliveryService.java`
- `FinalDeliveryCommand.java`
- `FinalDeliveryResult.java`
- `FinalResponseGuard.java`
- `FinalResponseGuardInputBuilder.java`
- `FinalResponseBuilder.java`
- `FinalRepairService.java`
- `FinalRepairPromptContext.java`
- `FixedSafeFallbackFactory.java`
- `FinalResponsePersistenceService.java`

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/finalresponse/guard/`

Required files:

- `FinalGuard.java`
- `EmptyAnswerGuard.java`
- `InternalLeakGuard.java`
- `FormatGuard.java`
- `EvidenceReferenceGuard.java`
- `ToolClaimGuard.java`
- `LengthGuard.java`
- `FinalGuardChain.java`

Create tests under:

- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/finalresponse/`

Required test files:

- `FinalResponseGuardTest.java`
- `FinalDeliveryServiceTest.java`
- `FinalRepairServiceTest.java`
- `FinalResponsePersistenceBoundaryTest.java`

## 4. Final Delivery Contract

### 4.1 `FinalDeliveryCommand`

Fields:

```java
private String runId;
private String sessionId;
private String userId;
private Integer loopIndex;
private MainAgentActionTypeEnumVO sourceAction;
private FinalAnswerCandidateVO finalAnswerCandidate;
private FailureVO failure;
private List<String> evidenceRefs;
private List<String> verifiedToolCallRefs;
private String userFormatRequirement;
private Integer maxOutputChars;
private Boolean ragWasUsed;
```

Rules:

- `finalAnswerCandidate.content` is required unless source is `FAIL`.
- `FAIL` must be converted to a user-safe final candidate before guard.
- `sourceAction` must be one of `FINAL`, `REPAIR_FINAL`, `CREATE_ARTIFACT`, `UPDATE_ARTIFACT`, or `FAIL`.

### 4.2 `FinalDeliveryResult`

Fields:

```java
private boolean completed;
private boolean repairRequested;
private boolean failed;
private FinalResponseVO finalResponse;
private FinalResponseGuardResultVO guardResult;
private FailureCodeEnumVO failureCode;
private String finalMessageId;
private String finalAnswerRef;
private String message;
```

## 5. FinalDeliveryService Procedure

`FinalDeliveryService.deliver(command)` must:

1. Normalize failure text when `sourceAction=FAIL`.
2. Persist candidate final answer as debug payload.
3. If `ragWasUsed=true`, call `RagVerificationRouter`.
4. If RAG verification fails, route to final repair or safe failure.
5. Build `FinalResponseGuardInputVO`.
6. Run `FinalResponseGuard`.
7. If guard passes, build `FinalResponseVO`.
8. Persist final response payload.
9. Append assistant message through `IConversationRepository.saveMessage`.
10. Update run completion/failure through `IRunRepository`.
11. Emit user-visible completed/failed event.
12. Write developer trace and audit summaries.
13. Return `FinalDeliveryResult`.

Rules:

- This service is the only component allowed to append normal assistant final messages.
- It must not append trace, verifier result, raw output, or receipt text as assistant content.
- RAG verifier details are developer trace/debug payload only.

## 6. Guard Chain

### 6.1 `FinalGuard`

Interface:

```java
FinalResponseGuardResultVO check(FinalResponseGuardInputVO input);
```

Rules:

- Return passed result when guard has no issue.
- Return failed result with one `FINAL_*` failure code when blocked.
- Do not modify content.

### 6.2 Guard Order

`FinalGuardChain` must run in this exact order:

1. `EmptyAnswerGuard`
2. `InternalLeakGuard`
3. `FormatGuard`
4. `EvidenceReferenceGuard`
5. `ToolClaimGuard`
6. `LengthGuard`

Stop at first blocking failure.

## 7. Guard Rules

### 7.1 `EmptyAnswerGuard`

Blocks:

- null content
- empty string
- whitespace-only content

Failure:

- `FINAL_EMPTY`

### 7.2 `InternalLeakGuard`

Blocks user-facing content that mentions internal process terms when user did not ask for internals.

Blocked terms include:

```text
Runtime, node, verifier, trace, contract, prompt, StateView, StateDelta,
tool receipt, raw output, repair process, validation result, developer trace
```

Failure:

- `FINAL_INTERNAL_LEAK`

### 7.3 `FormatGuard`

Blocks:

- markdown headings when `PLAIN_TEXT` was requested
- JSON object text when plain answer was requested
- missing expected artifact/file summary format when explicitly required

Failure:

- `FINAL_FORMAT_VIOLATION`

### 7.4 `EvidenceReferenceGuard`

Blocks:

- citation evidence id missing from allowed `evidenceRefs`
- malformed citation target
- citation to evidence not marked usable

Failure:

- `FINAL_INVALID_CITATION`

### 7.5 `ToolClaimGuard`

Blocks:

- claims like "published", "uploaded", "deleted", "sent", "file changed", "tool succeeded" without verified tool call refs
- claims of URL/id returned by tool when no tool evidence supports it

Failure:

- `FINAL_FALSE_TOOL_CLAIM`

### 7.6 `LengthGuard`

Blocks:

- content length greater than configured `maxOutputChars`

Failure:

- `FINAL_TOO_LONG`

## 8. Repair Flow

### 8.1 `FinalRepairService`

Required method:

```java
FinalAnswerCandidateVO repair(FinalRepairPromptContext context);
```

Dependencies:

- `NodeInvocationPipeline`

Component code:

- `FINAL_REPAIR`

Rules:

- Repair output must be `MainAgentActionVO` with action `REPAIR_FINAL`.
- Repair input includes failed candidate, failure code, guard summary, and repair instruction.
- Repair input excludes raw prompts, raw model output, raw tool receipts, and full developer trace.
- Repair must not re-plan.
- Repair must not call tools or RAG.
- Repair must not explain the repair process.

### 8.2 Repair Budget

- Runtime max final repair attempts defaults to `2`.
- Each guard or RAG verification repair attempt increments final repair count.
- When exhausted, use `FixedSafeFallbackFactory`.

### 8.3 Fixed Fallback

`FixedSafeFallbackFactory` returns user-safe text:

```text
I could not produce a safe final response for this request. Please retry with a narrower request or provide more details.
```

This fallback must also pass through `FinalResponseGuard`. If it somehow fails, Runtime stores failure and returns the minimal fixed message from application error handling, not from LLM output.

## 9. Persistence Boundary

`FinalResponsePersistenceService` must:

- store final answer body as payload when large
- save normal assistant message only after guard pass
- update run final message id and final answer ref
- store guard detail as developer trace payload
- store audit summary

It must not:

- save raw model output as final message
- save verification summary as final message
- save tool receipt as final message
- save runtime status text as final message

## 10. Required Tests

### 10.1 `FinalResponseGuardTest`

Required cases:

1. `empty_answer_is_blocked`
2. `internal_runtime_wording_is_blocked`
3. `raw_json_answer_is_blocked`
4. `missing_citation_is_blocked`
5. `false_tool_success_claim_is_blocked`
6. `too_long_answer_is_blocked`
7. `clean_answer_passes`

### 10.2 `FinalDeliveryServiceTest`

Required cases:

1. `final_delivery_appends_assistant_message_only_after_guard_pass`
2. `rag_verification_runs_before_guard_when_rag_was_used`
3. `rag_verification_skips_when_rag_was_not_used`
4. `guard_failure_requests_repair_when_budget_remains`
5. `guard_failure_uses_fixed_fallback_when_repair_exhausted`
6. `fail_action_user_message_goes_through_guard`

### 10.3 `FinalRepairServiceTest`

Required cases:

1. `repair_invokes_final_repair_component`
2. `repair_requires_repair_final_action`
3. `repair_prompt_excludes_raw_receipt_and_trace`
4. `repair_output_does_not_explain_repair_process`

### 10.4 `FinalResponsePersistenceBoundaryTest`

Required cases:

1. `raw_model_output_is_not_saved_as_assistant_message`
2. `verifier_result_is_not_saved_as_assistant_message`
3. `tool_receipt_is_not_saved_as_assistant_message`
4. `guard_detail_is_saved_as_developer_trace`

## 11. Execution Tasks

### Task 1: Add Final Response Types And Service Skeleton

**Files:**

- Create files listed in Section 3.

- [x] Implement command/result/context classes.
- [x] Implement `FinalDeliveryService` skeleton.
- [x] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 2: Implement Guard Chain

**Files:**

- Guard files listed in Section 3.

- [x] Implement guard interface.
- [x] Implement all six guards.
- [x] Implement first-blocking-failure chain.
- [x] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 3: Implement Final Delivery

**Files:**

- `FinalDeliveryService.java`
- `FinalResponseGuardInputBuilder.java`
- `FinalResponseBuilder.java`
- `FinalResponsePersistenceService.java`

- [x] Integrate optional RAG verification.
- [x] Run guard.
- [x] Persist final response only after guard pass.
- [x] Append assistant message only through final delivery.
- [x] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 4: Implement Repair And Fallback

**Files:**

- `FinalRepairService.java`
- `FinalRepairPromptContext.java`
- `FixedSafeFallbackFactory.java`

- [x] Invoke `FINAL_REPAIR` through `NodeInvocationPipeline`.
- [x] Enforce repair budget.
- [x] Use fixed fallback when exhausted.
- [x] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 5: Update Phase 6 Handler Wiring

**Files:**

- `FinalActionHandler.java`
- `CreateArtifactActionHandler.java`
- `UpdateArtifactActionHandler.java`
- `RepairFinalActionHandler.java`
- `FailActionHandler.java`

- [x] Replace fake `FinalDeliveryPort` with `FinalDeliveryService` adapter.
- [x] Ensure all user-visible text routes through final delivery.
- [x] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 6: Add Final Response Tests

**Files:**

- Create tests listed in Section 10.

- [x] Use fake repositories.
- [x] Use fake `RagVerificationRouter`.
- [x] Use fake `NodeInvocationPipeline` for repair.
- [x] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=FinalResponseGuardTest,FinalDeliveryServiceTest,FinalRepairServiceTest,FinalResponsePersistenceBoundaryTest" test
```

Expected result:

```text
BUILD SUCCESS
```

### Task 7: Cross-Spec Consistency Scan

- [x] Run:

```powershell
rg -n "saveMessage|ASSISTANT|append assistant|FinalDeliveryService" ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent\service
```

Expected:

```text
Normal assistant final message creation appears only in FinalDeliveryService or its persistence helper.
```

- [x] Run:

```powershell
rg -n "Runtime|node|verifier|trace|contract|prompt|StateView|StateDelta|tool receipt|raw output" ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent\service\finalresponse
```

Expected:

```text
Matches are allowed in InternalLeakGuard blocked terms, repair instructions, and tests.
```

## 12. Acceptance Checklist

- [x] Final delivery is the only normal assistant message creation path.
- [x] RAG verification runs before guard only when `ragWasUsed=true`.
- [x] Empty answer is blocked.
- [x] Internal process leakage is blocked.
- [x] Raw JSON leakage is blocked.
- [x] Invalid citation is blocked.
- [x] False tool success claim is blocked.
- [x] Overlong answer is blocked.
- [x] Repair uses `REPAIR_FINAL` only.
- [x] Repair budget is bounded.
- [x] Fixed fallback exists.
- [x] Guard details are stored as debug trace, not normal output.
- [x] Tests pass.

## 13. Worker Split Guidance

If using subagents, split work by non-overlapping file ownership:

- Worker A: final response command/result types and persistence helper.
- Worker B: guard chain and six Java guards.
- Worker C: final delivery service and RAG verification integration.
- Worker D: final repair service and fallback factory.
- Worker E: Phase 6 handler wiring.
- Worker F: final response tests.

The integrator must verify that normal assistant messages cannot be created from raw output, verifier result, trace, tool receipt, or runtime summary.
