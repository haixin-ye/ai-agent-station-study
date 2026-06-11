# AutoAgent Phase 7 RAG Runtime Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement explicit RAG retrieval, RAG evidence persistence, `ragWasUsed` fact handling, `RagVerifierInput` building, and LLM-backed grounding verification.

**Architecture:** `MainAgentNode` requests retrieval by emitting `RETRIEVE_RAG`. Runtime marks `ragWasUsed=true`, calls deterministic `RagRuntime`, persists query/hits/evidence, and continues the loop. Before final delivery, Runtime invokes `RagVerifier` only when the run fact `ragWasUsed=true`. Verification uses bounded RAG evidence summaries/snippets and outputs `VerificationResult`; it must not rewrite the answer or retrieve new documents.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring AI advisor/vector components through existing repository/service boundaries, Maven multi-module project, Lombok, Fastjson2, JUnit4, DDD package layout under `yhx.com`.

---

## 0. Execution Rules

- Start this phase only after Phase 6 action handlers compile.
- Do not trigger `RagVerifier` by scanning final answer keywords.
- Do not use fuzzy text matching to decide whether verification runs.
- Do not let `RagVerifier` answer the user, rewrite the answer, call tools, or retrieve new documents.
- Do not put raw retrieved documents, raw prompts, raw model outputs, traces, or tool receipts into `RagVerifierInput`.
- Do not expose verifier internals in normal frontend output.
- Do not commit unless the user explicitly asks.

## 1. Source Of Truth

Use the English canonical spec only:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`

Primary spec sections:

- Section 2.5: RAG flow
- Section 4.8: `RagVerifierInput`
- Section 5.14: `RETRIEVE_RAG` action
- Section 6.1: `VerificationResult` contract
- Section 6.6: `RagVerifier`
- Section 6.7: recovery policy for RAG failures
- Section 7.8: evidence table
- Section 7.11: RAG tables
- Section 13.10: Phase 7 tasks

## 2. Phase Boundary

### In Scope

- `RagRuntime` service.
- `RagRetrieverPort` abstraction.
- RAG query and hit persistence.
- `ragWasUsed` run flag persistence.
- RAG hit to evidence conversion.
- Bounded snippet/summary creation.
- `RagVerifierInputBuilder`.
- `RagVerifierNodeService` through `NodeInvocationPipeline`.
- `RagVerificationRouter` invoked before final delivery when `ragWasUsed=true`.
- RAG recovery result mapping.
- Tests for RAG execution, evidence, verifier trigger, and recovery.

### Out Of Scope

- FinalResponseGuard implementation details.
- ToolRuntime.
- MCP tool calls.
- Frontend SSE/API implementation.
- Admin UI for knowledge base configuration.
- Advanced reranking and query rewriting beyond one retry hook.

## 3. File Map

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/rag/runtime/`

Required files:

- `RagRuntime.java`
- `RagRetrieverPort.java`
- `RagRetrievalCommand.java`
- `RagRetrievalResult.java`
- `RagHitVO.java`
- `RagEvidenceConverter.java`
- `RagEvidenceSnippetPolicy.java`
- `RagVerifierInputBuilder.java`
- `RagVerifierNodeService.java`
- `RagVerificationRouter.java`
- `RagRecoveryHandler.java`

Create under:

- `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/port/`

Required files:

- `SpringAiRagRetrieverAdapter.java`

Create tests under:

- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/rag/`

Required test files:

- `RagRuntimeTest.java`
- `RagEvidenceConverterTest.java`
- `RagVerifierInputBuilderTest.java`
- `RagVerifierRoutingTest.java`
- `RagRecoveryHandlerTest.java`

## 4. RAG Runtime Contract

### 4.1 `RagRetrievalCommand`

Fields:

```java
private String runId;
private String sessionId;
private Integer loopIndex;
private RagRequestVO ragRequest;
private Integer topK;
private Integer maxHitChars;
private Map<String, Object> runtimeFilters;
```

Rules:

- `ragRequest.query` is required.
- `topK` defaults to `auto-agent.rag.defaultTopK`.
- `topK` must not exceed `auto-agent.rag.maxTopK`.

### 4.2 `RagRetrievalResult`

Fields:

```java
private String ragQueryId;
private String runId;
private String status;
private List<RagHitVO> hits;
private List<String> evidenceIds;
private FailureCodeEnumVO failureCode;
private String failureMessage;
```

Allowed status values:

```text
SUCCESS, NO_HIT, FAILED
```

### 4.3 `RagHitVO`

Fields:

```java
private String ragHitId;
private String sourceType;
private String sourceId;
private String title;
private String chunkText;
private String chunkRef;
private Double score;
private Integer rankNo;
private Map<String, Object> metadata;
```

Rules:

- `chunkText` is held temporarily during runtime conversion.
- Persist long chunk text through `IPayloadRepository` and store `chunkRef`.
- Do not put unbounded `chunkText` into `MainAgentStateView`.

## 5. RagRuntime Procedure

`RagRuntime.retrieve(command)` must:

1. Validate `ragRequest.query`.
2. Persist `agent_run.rag_was_used=1` before retrieval starts.
3. Persist `agent_rag_query` with status `REQUESTED`.
4. Call `RagRetrieverPort.retrieve`.
5. Persist every returned hit in `agent_rag_hit`.
6. Store each chunk text as `agent_payload` with payload type `RAG_CHUNK`.
7. Convert usable hits into `agent_evidence` with `evidenceType=RAG`.
8. Update RAG query status to `SUCCESS`, `NO_HIT`, or `FAILED`.
9. Emit user-visible safe progress events through `RunEventPublisher`.
10. Write developer trace for query, hit count, and errors.
11. Return `RagRetrievalResult`.

Important rule:

- Set `ragWasUsed=true` as soon as `RETRIEVE_RAG` is accepted for execution, even if retrieval later returns no hit or fails.

## 6. RagRetrieverPort

Interface:

```java
public interface RagRetrieverPort {
    List<RagHitVO> retrieve(RagRetrievalCommand command);
}
```

Rules:

- Domain depends on this port only.
- Infrastructure adapter may use existing Spring AI RAG/vector components.
- Port returns candidate hits only; evidence conversion belongs to domain `RagEvidenceConverter`.

`SpringAiRagRetrieverAdapter`:

- compiles behind the port
- may use existing `IRagService` or vector store integration if already available
- must not call `MainAgentNode`
- must not build final answers

## 7. Evidence Conversion

### 7.1 `RagEvidenceConverter`

Required method:

```java
List<AgentEvidenceEntity> convert(String runId, String sessionId, String ragQueryId, List<RagHitVO> hits);
```

Rules:

- Create one evidence record per usable hit.
- Evidence title uses hit title or source id.
- Evidence summary uses bounded summary/snippet from `RagEvidenceSnippetPolicy`.
- Evidence payload ref points to `RAG_CHUNK` payload.
- Evidence confidence derives from retrieval score when present.
- Evidence `usedByFinal=false` initially.

### 7.2 `RagEvidenceSnippetPolicy`

Required methods:

```java
String summarizeHit(RagHitVO hit, int maxChars);
String boundedSnippet(String chunkText, int maxChars);
```

MVP behavior:

- Do not call LLM.
- Use first `maxChars` characters as bounded snippet.
- Preserve source title and rank.
- Drop empty or whitespace-only chunks.

## 8. RagVerifierInputBuilder

Required method:

```java
RagVerifierInputVO build(RagVerifierInputBuildCommand command);
```

Create `RagVerifierInputBuildCommand` with fields:

```java
private String runId;
private String sessionId;
private Integer loopIndex;
private String userMessageId;
private String userInput;
private FinalAnswerCandidateVO finalAnswerCandidate;
private Boolean requiresKnowledgeBaseGrounding;
private List<RagQueryEntity> ragQueries;
private List<AgentEvidenceEntity> ragEvidence;
private Integer maxEvidenceSnippetChars;
```

Rules:

- Build only when `ragWasUsed=true`.
- Include bounded evidence summaries/snippets only.
- Include citations from final answer candidate.
- `claimsKnowledgeBaseGrounding` is passed as an input field when available, but never used as the trigger.
- Do not include unrelated memories, unrelated artifacts, raw prompt, raw model output, raw tool receipt, or developer trace.

## 9. RagVerifierNodeService

Dependencies:

- `NodeInvocationPipeline`
- `ContractValidator`

Required method:

```java
VerificationResultVO verify(RagVerifierInputVO input);
```

Rules:

- Component code is `RAG_VERIFIER`.
- Contract version defaults to `verification-result-v1`.
- It returns `VerificationResultVO`.
- If node invocation fails contract validation, route to bounded contract repair.
- It must not rewrite the final answer.

## 10. RagVerificationRouter

Required method:

```java
RagVerificationRouteResult verifyIfRequired(RagVerificationRouteCommand command);
```

Trigger rule:

```text
if run.ragWasUsed == true:
  build RagVerifierInput
  call RagVerifierNodeService
else:
  skip RagVerifier
```

Explicit non-trigger rules:

- Do not trigger because final answer says "knowledge base".
- Do not trigger because final answer contains citations.
- Do not trigger because user mentioned documents.
- Do not trigger because of keyword or fuzzy text matching.

`RagVerificationRouteResult` fields:

```java
private boolean verificationRequired;
private VerificationResultVO verificationResult;
private FailureCodeEnumVO failureCode;
private String message;
```

## 11. Recovery Handling

### 11.1 `RagRecoveryHandler`

Required method:

```java
MainActionHandlerResult handleVerificationFailure(VerificationResultVO result, RuntimeExecutionContext context);
```

Mapping:

| Failure | Recovery |
|---|---|
| `RAG_NO_EVIDENCE` | request final repair to remove unsupported knowledge-base claims or disclose missing evidence |
| `RAG_NO_HIT` | allow one query rewrite when retry budget remains; otherwise repair final answer to say no relevant evidence was found |
| `RAG_UNGROUNDED` | request final repair using only supplied evidence |
| `RAG_CONTRADICTION` | request final repair; safe failure if contradiction cannot be resolved |
| `FINAL_INVALID_CITATION` | request final repair to remove or correct missing citation |
| `CONTRACT_INVALID` | bounded contract repair |

Rules:

- Never retry indefinitely.
- Never invent evidence.
- Never expose verifier details to normal frontend.

## 12. Runtime Integration Points

Phase 7 updates these boundaries:

- `RetrieveRagActionHandler` calls real `RagRuntime`.
- `FinalDeliveryPort` or pre-final orchestration calls `RagVerificationRouter` before final guard when `ragWasUsed=true`.
- `ContextMaterializer` can include RAG evidence summaries in `evidencePack`.
- `RecoveryPolicy` maps RAG verification failures to final repair or safe failure.

Phase 7 must not implement full `FinalResponseGuard`; it only returns verification results and recovery decisions for Phase 9 final delivery.

## 13. Required Tests

### 13.1 `RagRuntimeTest`

Required test cases:

1. `retrieve_sets_rag_was_used_before_retriever_call`
2. `retrieve_success_persists_query_hits_payloads_and_evidence`
3. `retrieve_no_hit_still_keeps_rag_was_used_true`
4. `retrieve_failure_records_trace_and_failed_query_status`

### 13.2 `RagEvidenceConverterTest`

Required test cases:

1. `convert_hit_to_rag_evidence`
2. `empty_chunk_is_dropped`
3. `long_chunk_is_bounded_by_snippet_policy`
4. `confidence_uses_retrieval_score`

### 13.3 `RagVerifierInputBuilderTest`

Required test cases:

1. `builder_includes_only_bounded_rag_evidence`
2. `builder_excludes_raw_prompt_trace_tool_receipt_and_unrelated_artifact`
3. `builder_copies_final_candidate_citations`
4. `builder_requires_rag_was_used_route_before_invocation`

### 13.4 `RagVerifierRoutingTest`

Required test cases:

1. `rag_verifier_runs_when_rag_was_used_true`
2. `rag_verifier_skips_when_rag_was_used_false_even_if_answer_mentions_knowledge_base`
3. `rag_verifier_skips_when_rag_was_used_false_even_if_answer_has_citations`
4. `rag_verifier_failure_returns_verification_result`

### 13.5 `RagRecoveryHandlerTest`

Required test cases:

1. `rag_ungrounded_routes_to_final_repair`
2. `rag_no_hit_uses_retry_when_budget_remains`
3. `rag_no_hit_routes_to_final_repair_when_retry_exhausted`
4. `final_invalid_citation_routes_to_final_repair`
5. `rag_contradiction_without_repair_budget_fails_safely`

## 14. Execution Tasks

### Task 1: Add RAG Runtime Types And Port

**Files:**

- Create files listed in Sections 3 and 4.

- [ ] Implement command/result/hit VOs.
- [ ] Implement `RagRetrieverPort`.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 2: Implement RagRuntime

**Files:**

- `RagRuntime.java`
- `RagEvidenceConverter.java`
- `RagEvidenceSnippetPolicy.java`

- [ ] Set `ragWasUsed=true` before retrieval.
- [ ] Persist query, hits, payloads, and evidence through repository interfaces.
- [ ] Emit safe user event and developer trace.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 3: Add Infrastructure Retriever Adapter

**Files:**

- `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/port/SpringAiRagRetrieverAdapter.java`

- [ ] Implement `RagRetrieverPort`.
- [ ] Use existing RAG service/vector integration if stable.
- [ ] If wiring is not stable, provide a compile-safe skeleton that throws `UnsupportedOperationException("RAG retriever is not wired yet")`.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-infrastructure -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 4: Implement RagVerifier Input And Node Service

**Files:**

- `RagVerifierInputBuilder.java`
- `RagVerifierNodeService.java`

- [ ] Build bounded `RagVerifierInputVO`.
- [ ] Invoke `NodeInvocationPipeline` with component code `RAG_VERIFIER`.
- [ ] Validate `VerificationResultVO`.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 5: Implement Router And Recovery

**Files:**

- `RagVerificationRouter.java`
- `RagRecoveryHandler.java`

- [ ] Trigger only from `run.ragWasUsed=true`.
- [ ] Map failures according to Section 11.
- [ ] Do not inspect final answer keywords to decide invocation.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 6: Update Phase 6 Port Wiring

**Files:**

- `RetrieveRagActionHandler.java`
- `FinalDeliveryPort` integration point or final delivery pre-check adapter created in Phase 6.

- [ ] Replace fake `RagRuntimePort` implementation with `RagRuntime` adapter where available.
- [ ] Ensure final delivery orchestration can call `RagVerificationRouter`.
- [ ] Keep FinalResponseGuard internals out of this phase.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 7: Add RAG Tests

**Files:**

- Create tests listed in Section 13.

- [ ] Use fake retriever.
- [ ] Use fake repositories.
- [ ] Use fake node invocation pipeline for RagVerifier.
- [ ] Implement all Section 13 test cases.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=RagRuntimeTest,RagEvidenceConverterTest,RagVerifierInputBuilderTest,RagVerifierRoutingTest,RagRecoveryHandlerTest" test
```

Expected result:

```text
BUILD SUCCESS
```

### Task 8: Cross-Spec Consistency Scan

- [ ] Run:

```powershell
rg -n "knowledge base|citation|retrieved|document" ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent\service\rag
```

Expected:

```text
Matches are allowed in verifier input fields, prompts, tests, and comments, but not as Runtime trigger logic.
```

- [ ] Run:

```powershell
rg -n "ragWasUsed|RAG_VERIFIER|RAG_UNGROUNDED|RAG_NO_HIT|RAG_NO_EVIDENCE|FINAL_INVALID_CITATION" ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent
```

Expected:

```text
ragWasUsed controls verifier invocation; RAG failure codes map to recovery.
```

## 15. Acceptance Checklist

- [ ] `RETRIEVE_RAG` sets and persists `ragWasUsed=true` before retrieval.
- [ ] RAG query is persisted.
- [ ] RAG hits are persisted.
- [ ] RAG chunks are stored through payload references.
- [ ] RAG evidence records are created.
- [ ] `RagVerifierInput` includes only bounded RAG evidence.
- [ ] `RagVerifier` runs only when `ragWasUsed=true`.
- [ ] `RagVerifier` does not run from keyword/citation scanning.
- [ ] `RagVerifier` output uses `VerificationResultVO`.
- [ ] RAG failures map to deterministic recovery.
- [ ] Normal frontend cannot see raw verifier details.
- [ ] Tests pass.

## 16. Worker Split Guidance

If using subagents, split work by non-overlapping file ownership:

- Worker A: RAG runtime command/result/hit types and `RagRetrieverPort`.
- Worker B: `RagRuntime`, evidence converter, snippet policy.
- Worker C: infrastructure retriever adapter skeleton.
- Worker D: verifier input builder, verifier node service, verification router.
- Worker E: recovery handler and Phase 6 integration wiring.
- Worker F: RAG tests and fake retriever/repositories.

The integrator must verify that no code path invokes `RagVerifier` based on final-answer text scanning.

