# AutoAgent Phase 4 Context Artifact Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the context, memory, artifact, and evidence preparation layer that produces safe `ContextPlannerInput` and budget-approved `MainAgentStateView`.

**Architecture:** Runtime does not pass full backend state to LLM nodes. Java preselection creates compact candidates, `ContextPlannerNode` chooses references and context levels, and Java materialization resolves only selected references into `MainAgentStateView`. Artifacts, memories, evidence, and payloads are loaded through domain managers and repository interfaces with strict token/length budgets.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Maven multi-module project, Fastjson2, Lombok, JUnit4, DDD package layout under `yhx.com`.

---

## 0. Execution Rules

- Start this phase only after Phase 0/1 compiles. Phase 2 repository interfaces should exist before production adapters are wired.
- Do not implement full Runtime lifecycle in this phase.
- Do not call `MainAgentNode` in this phase.
- Do not call RAG retrieval or MCP tools in this phase.
- Do not load raw prompts, raw model outputs, full traces, raw tool receipts, or debug payloads into `MainAgentStateView`.
- Do not pass `ContextPlannerOutput` directly to `MainAgentNode`.
- Do not commit unless the user explicitly asks.

## 1. Source Of Truth

Use the English canonical spec only:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`

Primary spec sections:

- Section 3.6: `ContextPlannerStatus` handling
- Section 4.2: `AgentState` logical areas
- Section 4.4: `ContextPlannerInput`
- Section 4.5: `MainAgentStateView`
- Section 4.8: `RagVerifierInput` exclusions
- Section 5.6: `ContextPlannerNode` prompt
- Section 5.7: `ContextPlannerOutput` contract
- Section 5.8: Runtime context materialization
- Section 7: repository-backed storage groups
- Section 12: context and artifact tests

## 2. Phase Boundary

### In Scope

- Java candidate preselection for messages, summaries, artifacts, memories, evidence, capabilities, and pending action.
- `ContextPlannerInput` builder.
- `ContextPlannerNode` invocation wrapper using `NodeInvocationPipeline`.
- `ContextPlannerOutput` handling for `READY`, `NO_RELEVANT_CONTEXT`, `NEEDS_USER_CLARIFICATION`, `CONTEXT_OVER_BUDGET`, and `FAILED`.
- `ContextBudgetManager`.
- `ArtifactResolver`.
- `ArtifactContextPolicy`.
- `ContextMaterializer`.
- `MainAgentStateView` builder.
- Artifact persistence helper for create/update/version/alias/relation.
- MVP memory candidate and summary services.
- Evidence candidate and evidence pack services.
- Unit tests with fake repositories and fake node pipeline.

### Out Of Scope

- Full `AutoAgentRuntime`.
- Pending input persistence and API implementation.
- ToolRuntime.
- RagRuntime.
- FinalResponseGuard.
- SSE controllers.
- Frontend rendering.
- Advanced vector search for memory.
- Advanced semantic artifact search.

## 3. File Map

### 3.1 Domain Context Package

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/context/`

Required files:

- `ContextPreparationService.java`
- `ContextCandidatePreselector.java`
- `ContextPlannerNodeService.java`
- `ContextPlannerStatusHandler.java`
- `ContextMaterializer.java`
- `ContextBudgetManager.java`
- `ContextTokenEstimator.java`
- `MainAgentStateViewBuilder.java`
- `ContextSelectionValidator.java`
- `ContextOverBudgetPolicy.java`
- `ContextPlannerPendingInputHandler.java`

### 3.2 Domain Artifact Package

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/artifact/`

Required files:

- `ArtifactResolver.java`
- `ArtifactContextPolicy.java`
- `ArtifactManager.java`
- `ArtifactVersionService.java`
- `ArtifactAliasService.java`
- `ArtifactCandidateRanker.java`
- `ArtifactPayloadLoader.java`

### 3.3 Domain Memory Package

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/memory/`

Required files:

- `MemoryManager.java`
- `MemoryCandidatePreselector.java`
- `ConversationSummaryService.java`
- `LongTermMemoryService.java`

### 3.4 Domain Evidence Package

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/evidence/`

Required files:

- `EvidenceManager.java`
- `EvidenceCandidatePreselector.java`
- `EvidencePackBuilder.java`
- `ToolReceiptSummarizer.java`

### 3.5 Domain Value Objects

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/`

Required files when Phase 0/1 did not already create them:

- `ContextCandidateBundleVO.java`
- `MessageCandidateVO.java`
- `SummaryCandidateVO.java`
- `ArtifactCandidateVO.java`
- `MemoryCandidateVO.java`
- `EvidenceCandidateVO.java`
- `CapabilityCandidateVO.java`
- `TokenBudgetVO.java`
- `ContextIntentVO.java`
- `ContextSelectionVO.java`
- `SelectedArtifactContextVO.java`
- `MaterializedArtifactContentVO.java`
- `MaterializedMemoryVO.java`
- `MaterializedEvidenceVO.java`
- `ConversationViewVO.java`
- `PendingActionViewVO.java`
- `VerifierFeedbackViewVO.java`
- `ArtifactCreateCommandVO.java`
- `ArtifactUpdateCommandVO.java`
- `ArtifactChunkVO.java`

### 3.6 Tests

Create under:

- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/context/`

Required test files:

- `ContextCandidatePreselectorTest.java`
- `ContextPlannerStatusHandlerTest.java`
- `ContextMaterializationTest.java`
- `ArtifactContextPolicyTest.java`
- `ArtifactResolverTest.java`
- `EvidencePackBuilderTest.java`
- `MemoryCandidatePreselectorTest.java`

Test helpers:

- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/context/support/FakeContextRepositories.java`
- `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/context/support/FakeContextPlannerPipeline.java`

## 4. Core Data Shapes

### 4.1 `ContextCandidateBundleVO`

Fields:

```java
private RunMetaVO runMeta;
private UserInputVO userInput;
private List<MessageCandidateVO> recentMessages;
private List<SummaryCandidateVO> sessionSummaries;
private List<ArtifactCandidateVO> artifactCandidates;
private List<MemoryCandidateVO> memoryCandidates;
private List<EvidenceCandidateVO> evidenceCandidates;
private List<CapabilityCandidateVO> availableCapabilities;
private PendingActionViewVO pendingAction;
private TokenBudgetVO tokenBudget;
```

Rules:

- This object is Java-built before `ContextPlannerNode`.
- It contains compact candidates only.
- It must not contain full artifact body, raw trace, raw prompt, raw model output, or raw tool receipt.

### 4.2 `ArtifactCandidateVO`

Fields:

```java
private String artifactId;
private String artifactType;
private String title;
private String summary;
private List<String> aliases;
private String contentRef;
private Integer tokenCount;
private Integer version;
private String status;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
private LocalDateTime lastMentionedAt;
private Double recencyScore;
private Double aliasScore;
private Double titleScore;
private Double totalScore;
private List<String> reasons;
```

Candidate ranking is heuristic Java logic. It is not expected to perfectly understand user intent. `ContextPlannerNode` receives ranked candidates and decides final context selection or asks the user.

### 4.3 `ContextSelectionVO`

Fields:

```java
private String sourceType;
private String sourceId;
private ContextLevelEnumVO contextLevel;
private Integer priority;
private Double confidence;
private String reason;
```

Allowed `sourceType` values:

- `MESSAGE`
- `SUMMARY`
- `ARTIFACT`
- `MEMORY`
- `EVIDENCE`

### 4.4 `MaterializedArtifactContentVO`

Fields:

```java
private String artifactId;
private ContextLevelEnumVO contextLevel;
private String title;
private String summary;
private String contentRef;
private String content;
private List<ArtifactChunkVO> chunks;
private Integer tokenCount;
private Boolean truncated;
```

Rules:

- `METADATA_ONLY` sets `content=null` and `chunks=[]`.
- `SUMMARY_ONLY` sets `content=null` and includes summary.
- `SUMMARY_PLUS_SNIPPET` includes bounded `content`.
- `FULL_TEXT` includes full content only when within budget.
- `CHUNKED_CONTEXT` includes selected chunks only.

### 4.5 `TokenBudgetVO`

Fields:

```java
private Integer maxStateViewTokens;
private Integer reservedOutputTokens;
private Integer currentCandidateTokens;
private Integer selectedContextTokens;
private Integer remainingTokens;
private Integer maxArtifactInlineChars;
private Integer maxEvidenceSummaryChars;
private Boolean overBudget;
```

## 5. Candidate Preselection

### 5.1 `ContextCandidatePreselector`

Dependencies:

- `IConversationRepository`
- `IMemoryRepository`
- `IArtifactRepository`
- `IEvidenceRepository`
- `IPendingInputRepository`
- capability metadata provider from a later phase or fake provider in tests
- `ContextTokenEstimator`
- `ArtifactCandidateRanker`
- `MemoryCandidatePreselector`
- `EvidenceCandidatePreselector`

Required method:

```java
ContextCandidateBundleVO buildCandidates(ContextPreparationCommand command);
```

Create `ContextPreparationCommand` with fields:

```java
private String runId;
private String sessionId;
private String userId;
private String agentId;
private String userMessageId;
private String userInput;
private Integer loopIndex;
private Integer recentMessageLimit;
private Integer artifactCandidateLimit;
private Integer memoryCandidateLimit;
private Integer evidenceCandidateLimit;
```

### 5.2 Recent Message Preselection

Rules:

- Load only user-visible recent messages through `IConversationRepository.listRecentMessages`.
- Include current user input as `userInput`, not as a duplicated recent message.
- Include compact content only.
- If a message has `contentRef`, use repository-provided summary or `IPayloadRepository.loadPayloadSummary`, never full payload by default.

### 5.3 Artifact Candidate Preselection

Rules:

- Load recent artifacts by session and user input through `IArtifactRepository.findArtifactCandidates`.
- Rank candidates with `ArtifactCandidateRanker`.
- Ranking signals:
  - exact title mention
  - alias mention
  - artifact type word mention, such as article/code/plan/table
  - recency
  - last mentioned time
  - same run or recent run
  - user wording contains "this", "that", "previous", "latest", "second", "version"
- Keep top `artifactCandidateLimit`.
- Candidate contains only id, title, summary, aliases, contentRef, tokenCount, version, status, timestamps, scores, and reasons.

### 5.4 Memory Candidate Preselection

Rules:

- Load long-term memory candidates by user/session/query.
- Include memory only if it can affect current response, preference, project context, or follow-up resolution.
- Keep top `memoryCandidateLimit`.
- MVP may use simple keyword overlap plus recency score.

### 5.5 Evidence Candidate Preselection

Rules:

- Load run evidence and recent session evidence.
- Include RAG evidence, tool evidence, artifact evidence, memory evidence, and user confirmation evidence.
- Do not include raw tool receipts.
- Tool evidence candidate summary may mention call-level status, returned URL, returned id, or error summary when available.

## 6. Artifact Context Policy

### 6.1 `ArtifactContextPolicy`

Required method:

```java
ContextLevelEnumVO decideLevel(ArtifactContextPolicyInput input);
```

Create `ArtifactContextPolicyInput` with fields:

```java
private String userInput;
private ArtifactCandidateVO artifact;
private String requestedOperation;
private Integer maxInlineTokens;
private Boolean toolWillMaterializeLater;
```

### 6.2 Decision Rules

Use these deterministic defaults before or after `ContextPlannerNode` selection validation:

| User intent | Default context level |
|---|---|
| publish/upload/archive/delete/move existing artifact | `METADATA_ONLY` |
| summarize/list/title suggestion/light evaluation | `SUMMARY_PLUS_SNIPPET` |
| review/rewrite/polish/restructure/compare/modify short artifact | `FULL_TEXT` |
| review/rewrite/polish/restructure/compare/modify long artifact | `CHUNKED_CONTEXT` |
| unclear target artifact | ask through `NEEDS_USER_CLARIFICATION` |
| simple follow-up not requiring artifact content | `SUMMARY_ONLY` |

If `ContextPlannerNode` selects `FULL_TEXT` but the payload exceeds budget, `ContextMaterializer` must downgrade to `CHUNKED_CONTEXT` or return over-budget recovery metadata. It must not silently include oversized full text.

## 7. ContextPlanner Invocation Wrapper

### 7.1 `ContextPlannerNodeService`

Dependencies:

- `NodeInvocationPipeline`
- `ContractValidator`

Required method:

```java
ContextPlannerOutputVO plan(ContextPlannerInputVO input);
```

Rules:

- Component code is `CONTEXT_PLANNER`.
- Contract version defaults to `context-planner-output-v1`.
- The wrapper does not create pending input.
- The wrapper does not materialize content.
- If node invocation fails, return status `FAILED` or propagate structured failure to `ContextPlannerStatusHandler`.

## 8. ContextPlanner Status Handling

### 8.1 `ContextPlannerStatusHandler`

Required method:

```java
ContextPlannerHandlingResult handle(ContextPlannerOutputVO output, ContextCandidateBundleVO candidates);
```

Create `ContextPlannerHandlingResult` with fields:

```java
private String nextStep;
private MainAgentStateViewVO stateView;
private AskUserRequestVO askUserRequest;
private FailureVO failure;
private List<ContextSelectionVO> effectiveSelections;
```

Allowed `nextStep` values:

- `BUILD_STATE_VIEW`
- `ASK_USER`
- `BUILD_MINIMAL_STATE_VIEW`
- `COMPRESS_OR_ASK`
- `SAFE_FAILURE`

### 8.2 Status Rules

| Status | Handling |
|---|---|
| `READY` | Validate selected ids and context levels, materialize requested context, build `MainAgentStateView`. |
| `NO_RELEVANT_CONTEXT` | Build minimal `MainAgentStateView` with current user input, selected recent conversation if any, and available capabilities. |
| `NEEDS_USER_CLARIFICATION` | Validate `clarificationRequest`, return `ASK_USER` result for later Runtime/UserInteractionManager handling. |
| `CONTEXT_OVER_BUDGET` | Return `COMPRESS_OR_ASK`; do not call `MainAgentNode` with oversized context. |
| `FAILED` | Return `BUILD_MINIMAL_STATE_VIEW` when Java preselection can safely proceed, otherwise `SAFE_FAILURE`. |

Phase 4 does not persist pending input. It returns `AskUserRequestVO` so Phase 5 can wire it through `UserInteractionManager`.

## 9. Context Materialization

### 9.1 `ContextMaterializer`

Dependencies:

- `IConversationRepository`
- `IMemoryRepository`
- `IArtifactRepository`
- `IEvidenceRepository`
- `IPayloadRepository`
- `ContextBudgetManager`
- `ArtifactPayloadLoader`
- `EvidencePackBuilder`

Required method:

```java
MainAgentStateViewVO materialize(ContextMaterializationCommand command);
```

Create `ContextMaterializationCommand` with fields:

```java
private ContextCandidateBundleVO candidates;
private ContextPlannerOutputVO plannerOutput;
private List<ContextSelectionVO> forcedSelections;
private TokenBudgetVO tokenBudget;
```

### 9.2 Materialization Rules

- Validate every selected id exists in the candidate bundle or repository.
- Load messages and summaries as compact text.
- Load memories as selected memory summaries.
- Load artifacts according to `contextLevel`.
- Load evidence as summaries or bounded snippets.
- Summarize tool receipt facts into `evidencePack`.
- Never load raw prompt, raw model output, trace payload, audit payload, or unbounded tool receipt.
- Run `ContextBudgetManager` after materialization.
- If over budget, downgrade artifact content where legal, then re-check.
- If still over budget, return over-budget failure metadata instead of building unsafe state view.

### 9.3 `MainAgentStateViewBuilder`

Required method:

```java
MainAgentStateViewVO build(MainAgentStateViewBuildCommand command);
```

Required output fields:

- `runMeta`
- `userInput`
- `conversation`
- `memoryPack`
- `resolvedArtifacts`
- `artifactContent`
- `evidencePack`
- `availableCapabilities`
- `pendingAction`
- `currentPlan`
- `lastVerifierFeedback`
- `outputContractVersion`

`outputContractVersion` must default to `main-agent-action-v1`.

## 10. Context Budget Manager

### 10.1 `ContextTokenEstimator`

Required methods:

```java
int estimateTextTokens(String text);
int estimateObjectTokens(Object object);
```

MVP implementation:

- Return `0` for null.
- Estimate Chinese/English mixed text as `ceil(length / 2.0)`.
- Estimate JSON object by serializing to JSON and applying text estimate.

This is intentionally approximate. Later phases may replace it with model-specific tokenizer support.

### 10.2 `ContextBudgetManager`

Required methods:

```java
TokenBudgetVO evaluate(MainAgentStateViewVO stateView, TokenBudgetVO budget);
MainAgentStateViewVO shrinkToFit(MainAgentStateViewVO stateView, TokenBudgetVO budget);
```

Shrink order:

1. Remove nonessential snippets from evidence.
2. Downgrade `SUMMARY_PLUS_SNIPPET` artifacts to `SUMMARY_ONLY`.
3. Downgrade oversized `FULL_TEXT` artifacts to `CHUNKED_CONTEXT`.
4. Reduce chunk count.
5. Reduce recent messages.
6. Return over-budget result if still too large.

Never remove:

- current user input
- selected artifact identity
- required evidence identity
- pending action identity
- available capability identity required by the current task

## 11. Artifact Manager

### 11.1 `ArtifactManager`

Dependencies:

- `IArtifactRepository`
- `IPayloadRepository`
- `IEvidenceRepository`
- `ArtifactAliasService`
- `ArtifactVersionService`

Required methods:

```java
AgentArtifactEntity createArtifact(ArtifactCreateCommandVO command);
AgentArtifactEntity updateArtifact(ArtifactUpdateCommandVO command);
AgentArtifactEntity findArtifact(String artifactId);
```

### 11.2 Create Rules

- Store body in `agent_payload` through `IPayloadRepository`.
- Store metadata in `agent_artifact`.
- Store aliases from title, suggested aliases, and user wording.
- Create artifact evidence with `evidenceType=ARTIFACT`.
- Return artifact entity with `artifactId` and `contentRef`.

### 11.3 Update Rules

- Validate target artifact exists.
- Store updated body as a new payload.
- Create new version when `updateMode=CREATE_VERSION` or configured version policy requires it.
- Record relation `VERSION_OF` or `DERIVED_FROM` when a new artifact id is created.
- Update alias and `lastMentionedAt`.
- Create artifact evidence for the update.

Phase 4 only implements the domain helper behavior and fake repository tests. Runtime action handlers will call these methods in Phase 6.

## 12. Memory Manager

### 12.1 `MemoryManager`

Required methods:

```java
List<MemoryCandidateVO> selectMemoryCandidates(String userId, String sessionId, String userInput, int limit);
void saveConversationSummary(AgentConversationSummaryEntity summary);
void saveLongTermMemory(AgentMemoryEntity memory);
```

MVP rules:

- Use repository candidate methods and simple scoring.
- Do not generate summaries with an LLM in Phase 4.
- Accept externally provided summary text from later Runtime phases.
- Keep summaries compact.

## 13. Evidence Manager

### 13.1 `EvidenceManager`

Required methods:

```java
String saveEvidence(AgentEvidenceEntity evidence);
List<EvidenceCandidateVO> selectEvidenceCandidates(String runId, String sessionId, String userInput, int limit);
List<MaterializedEvidenceVO> buildEvidencePack(List<ContextSelectionVO> selections);
```

### 13.2 `ToolReceiptSummarizer`

Required method:

```java
MaterializedEvidenceVO summarizeToolEvidence(AgentEvidenceEntity evidence, AgentPayloadEntity receiptPayload);
```

Rules:

- Extract only bounded user-useful facts such as status, returned URL, returned id, error summary, and timestamp.
- Do not include full raw receipt JSON.
- Do not include credentials, cookies, auth headers, or hidden tool parameters.

## 14. Important Scenarios

### 14.1 "Publish this RAG article to CSDN"

Expected flow:

1. Java preselector finds recent artifact candidates.
2. ContextPlanner selects the most likely article with `METADATA_ONLY`.
3. Materializer includes artifact id, title, summary, aliases, version, and contentRef.
4. `MainAgentStateView.artifactContent` does not include full article body.
5. Later `CALL_TOOL` can request `contentSource` with `FULL_TEXT_REQUIRED`; ToolArgumentMaterializer loads full text in Phase 8.

### 14.2 "Improve the structure of this article"

Expected flow:

1. Java preselector finds artifact candidates.
2. ContextPlanner selects target artifact with `FULL_TEXT` when token count fits.
3. Materializer loads full artifact body.
4. If full body exceeds budget, materializer uses `CHUNKED_CONTEXT`.
5. If chunking is still insufficient, status handler returns ask/compress path instead of calling `MainAgentNode` with oversized context.

### 14.3 Follow-up: "Then what about MCP?"

Expected flow:

1. Java preselector includes recent messages and session summary about RAG.
2. ContextPlanner marks history dependency.
3. Materializer includes selected summary/recent messages.
4. MainAgentNode receives enough context to answer MCP in relation to previous RAG discussion.

### 14.4 Ambiguous Artifact: "Publish the second one"

Expected flow:

1. Java preselector returns multiple plausible artifacts.
2. ContextPlanner returns `NEEDS_USER_CLARIFICATION` with `SINGLE_CHOICE_OR_FREE_TEXT`.
3. Phase 4 status handler returns `ASK_USER` result.
4. Phase 5 will persist pending input and resume the same run.

## 15. Required Tests

### 15.1 `ContextCandidatePreselectorTest`

Required test cases:

1. `build_candidates_includes_recent_messages_summaries_artifacts_memories_evidence`
2. `artifact_candidates_are_ranked_by_alias_title_and_recency`
3. `candidate_bundle_does_not_include_full_artifact_body`
4. `message_content_ref_loads_summary_not_full_payload`

### 15.2 `ArtifactContextPolicyTest`

Required test cases:

1. `publish_intent_uses_metadata_only`
2. `rewrite_short_artifact_uses_full_text`
3. `rewrite_long_artifact_uses_chunked_context`
4. `summary_request_uses_summary_plus_snippet`

### 15.3 `ContextMaterializationTest`

Required test cases:

1. `metadata_only_artifact_does_not_load_body`
2. `full_text_artifact_loads_payload_within_budget`
3. `oversized_full_text_downgrades_to_chunked_context`
4. `raw_tool_receipt_is_summarized_into_evidence_pack`
5. `trace_prompt_and_model_output_payloads_are_never_loaded`
6. `over_budget_after_shrink_returns_failure_metadata`

### 15.4 `ContextPlannerStatusHandlerTest`

Required test cases:

1. `ready_builds_state_view`
2. `no_relevant_context_builds_minimal_state_view`
3. `needs_user_clarification_returns_ask_user_result`
4. `context_over_budget_returns_compress_or_ask`
5. `failed_uses_minimal_fallback_when_safe`

### 15.5 `ArtifactResolverTest`

Required test cases:

1. `resolves_exact_artifact_id`
2. `resolves_by_alias_when_one_candidate_is_strong`
3. `ambiguous_alias_returns_multiple_candidates`
4. `updates_last_mentioned_when_resolved`

### 15.6 `EvidencePackBuilderTest`

Required test cases:

1. `rag_evidence_keeps_summary_and_bounded_snippet`
2. `tool_evidence_keeps_status_url_and_id_only`
3. `tool_evidence_drops_raw_receipt_sensitive_fields`

### 15.7 `MemoryCandidatePreselectorTest`

Required test cases:

1. `follow_up_question_selects_recent_topic_summary`
2. `irrelevant_memory_is_not_selected`
3. `preference_memory_is_selected_when_user_request_depends_on_preference`

## 16. Execution Tasks

### Task 1: Add Context/Artifact/Memory/Evidence Packages

**Files:**

- Create package directories and `package-info.java` files for Sections 3.1-3.4.

- [ ] Create package markers.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 2: Add Context Value Objects

**Files:**

- Create files listed in Section 3.5 when missing.

- [ ] Add Lombok annotations.
- [ ] Implement fields from Section 4.
- [ ] Use existing Phase 0/1 enums.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 3: Add Candidate Preselection

**Files:**

- `ContextCandidatePreselector.java`
- `ArtifactCandidateRanker.java`
- `MemoryCandidatePreselector.java`
- `EvidenceCandidatePreselector.java`

- [ ] Implement Section 5 rules.
- [ ] Keep candidates compact.
- [ ] Add scoring reasons.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 4: Add Artifact Context Policy And Resolver

**Files:**

- `ArtifactContextPolicy.java`
- `ArtifactResolver.java`
- `ArtifactPayloadLoader.java`

- [ ] Implement Section 6 policy table.
- [ ] Implement exact id, alias, title, and candidate-based resolution.
- [ ] Implement payload loading by context level.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 5: Add ContextPlanner Wrapper And Status Handler

**Files:**

- `ContextPlannerNodeService.java`
- `ContextPlannerStatusHandler.java`
- `ContextSelectionValidator.java`
- `ContextPlannerPendingInputHandler.java`

- [ ] Use `NodeInvocationPipeline` for `CONTEXT_PLANNER`.
- [ ] Validate selected ids and context levels.
- [ ] Return `ASK_USER` result instead of persisting pending input.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 6: Add Context Materialization And Budgeting

**Files:**

- `ContextMaterializer.java`
- `ContextBudgetManager.java`
- `ContextTokenEstimator.java`
- `ContextOverBudgetPolicy.java`
- `MainAgentStateViewBuilder.java`

- [ ] Implement Section 9 materialization rules.
- [ ] Implement Section 10 token estimate and shrink order.
- [ ] Ensure debug/raw payload exclusions.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 7: Add Artifact, Memory, And Evidence Managers

**Files:**

- `ArtifactManager.java`
- `ArtifactVersionService.java`
- `ArtifactAliasService.java`
- `MemoryManager.java`
- `ConversationSummaryService.java`
- `LongTermMemoryService.java`
- `EvidenceManager.java`
- `EvidencePackBuilder.java`
- `ToolReceiptSummarizer.java`

- [ ] Implement Section 11 artifact create/update helpers.
- [ ] Implement Section 12 memory MVP helpers.
- [ ] Implement Section 13 evidence helpers.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 8: Add Context Tests

**Files:**

- Create tests listed in Section 3.6.

- [ ] Use fake repositories.
- [ ] Use fake context planner pipeline.
- [ ] Implement all Section 15 tests.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=ContextCandidatePreselectorTest,ContextPlannerStatusHandlerTest,ContextMaterializationTest,ArtifactContextPolicyTest,ArtifactResolverTest,EvidencePackBuilderTest,MemoryCandidatePreselectorTest" test
```

Expected result:

```text
BUILD SUCCESS
```

### Task 9: Cross-Spec Consistency Scan

- [ ] Run:

```powershell
rg -n "raw model output|raw prompt|agent_run_trace|tool receipt" ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent\service\context ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent\service\artifact ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent\service\evidence
```

Expected:

```text
Matches exist only in exclusion checks or comments that forbid loading raw/debug data.
```

- [ ] Run:

```powershell
rg -n "ContextPlannerOutput.*MainAgent|pass.*ContextPlannerOutput" ai-agent-station-study-domain\src\main\java\yhx\com\domain\agent
```

Expected:

```text
No code path passes ContextPlannerOutput directly to MainAgentNode.
```

### Task 10: Phase 4 Compile Gate

- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

## 17. Acceptance Checklist

- [ ] `ContextPlannerInput` contains compact candidates only.
- [ ] Artifact candidate preselection uses title, alias, recency, version, and reference wording signals.
- [ ] `ContextPlannerNodeService` uses `NodeInvocationPipeline`.
- [ ] `ContextPlannerOutput` is never passed directly to `MainAgentNode`.
- [ ] `ContextMaterializer` builds `MainAgentStateView`.
- [ ] `METADATA_ONLY` does not load artifact body.
- [ ] `FULL_TEXT` loads body only within budget.
- [ ] `CHUNKED_CONTEXT` loads selected chunks only.
- [ ] Raw tool receipts are summarized into evidence.
- [ ] Raw prompts, raw model outputs, trace payloads, and debug payloads are excluded from `MainAgentStateView`.
- [ ] Ambiguous context returns `AskUserRequestVO`, not an invented selection.
- [ ] Context budget overflow has deterministic shrink or ask/fail result.
- [ ] Tests pass.

## 18. Worker Split Guidance

If using subagents, split work by non-overlapping file ownership:

- Worker A: context VOs and candidate preselector.
- Worker B: artifact resolver, artifact policy, artifact manager.
- Worker C: memory and evidence managers.
- Worker D: context planner wrapper, status handler, materializer, budget manager.
- Worker E: context tests and fake repositories.

The integrator must review that no raw debug or payload data enters `MainAgentStateView` before accepting the phase.

