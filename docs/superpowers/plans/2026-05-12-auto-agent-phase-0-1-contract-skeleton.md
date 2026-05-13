# AutoAgent Phase 0-1 Contract Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the non-runtime foundation for the AutoAgent main-loop harness: package skeleton, typed configuration, enums, value objects, entities, structured contracts, parsers, validators, and minimal contract tests.

**Architecture:** This phase does not replace the old Node1-4 execution path and does not call any LLM, RAG, MCP, database, or SSE endpoint. It creates Java-owned protocol definitions that later Runtime, nodes, persistence, and frontend APIs must use. The canonical reference is `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Maven multi-module project, Lombok, Fastjson2, JUnit4, DDD package layout under `yhx.com`.

---

## 0. Execution Rules

- Do not commit unless the user explicitly asks.
- Do not delete or rewrite the old `service/execute/auto` harness in this phase.
- Do not add Spring AI node calls in this phase.
- Do not create database tables in this phase.
- Do not put runtime schema, parser rules, action enums, or StateDelta write scopes into database prompt text.
- Keep all Java files under package root `yhx.com`.
- Keep code focused and compile after each task group.

## 1. Source Of Truth

Use the English canonical spec only:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`

Do not use the Chinese review sample as an implementation source.

## 2. Phase Boundary

### In Scope

- Package skeleton for the new harness.
- Configuration property classes for `auto-agent.*`.
- Domain enums.
- Domain value objects and entities.
- Contract registry and parser/validator interfaces.
- Strict `MainAgentAction` StateDelta write-scope validation.
- ContextPlanner output validation.
- Raw JSON parse validation and safe extraction utility.
- Minimal targeted tests under the app module.

### Out Of Scope

- Runtime loop implementation.
- Context materialization.
- Prompt assembly.
- LLM invocation.
- RAG retrieval.
- MCP client invocation.
- Tool approval lifecycle.
- Persistence tables and MyBatis mappers.
- SSE controllers.
- Frontend changes.
- Old harness migration.

## 3. File Map

### 3.1 Domain Package Skeleton

Create these directories and `package-info.java` files:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/package-info.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/package-info.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/package-info.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/contract/package-info.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/execute/package-info.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/runtime/package-info.java`
- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/adapter/repository/package-info.java`

`service/execute` is kept for compatibility with existing DDD naming. New deterministic lifecycle classes are placed under `service/runtime` after Phase 1. Phase 0/1 only creates the package marker.

### 3.2 App Configuration Files

Create these classes:

- `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentRuntimeProperties.java`
- `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentContextProperties.java`
- `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentNodeProperties.java`
- `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentRagProperties.java`
- `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentMcpProperties.java`
- `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentCapabilityProperties.java`
- `ai-agent-station-study-app/src/main/java/yhx/com/config/AutoAgentDebugProperties.java`

No config class may perform runtime orchestration. These files only bind typed values from yml.

### 3.3 Enum Files

Create under `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/enums/`:

- `RunStatusEnumVO.java`
- `RuntimePhaseEnumVO.java`
- `MainAgentActionTypeEnumVO.java`
- `StateDeltaFieldEnumVO.java`
- `ContextLevelEnumVO.java`
- `ContextPlannerStatusEnumVO.java`
- `EvidenceTypeEnumVO.java`
- `PendingInputTypeEnumVO.java`
- `InputModeEnumVO.java`
- `ToolCallStatusEnumVO.java`
- `ToolInvocationStatusEnumVO.java`
- `ToolApprovalStatusEnumVO.java`
- `UserApprovalDecisionEnumVO.java`
- `VerificationStatusEnumVO.java`
- `FinalGuardFailureCodeEnumVO.java`
- `FailureCodeEnumVO.java`
- `RecoveryActionEnumVO.java`
- `TranscriptBlockTypeEnumVO.java`
- `PermissionModeEnumVO.java`
- `RequiredPermissionEnumVO.java`
- `ApprovalPolicyEnumVO.java`
- `PermissionDecisionTypeEnumVO.java`
- `McpTransportTypeEnumVO.java`
- `ToolArgumentSourceTypeEnumVO.java`
- `ToolArgumentContentModeEnumVO.java`

Each enum must expose:

```java
private final String code;
private final String info;
```

and methods:

```java
public String code();
public String info();
public static Optional<EnumType> ofCode(String code);
```

### 3.4 Value Object Files

Create under `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/`:

- `AgentStateVO.java`
- `MainAgentStateViewVO.java`
- `ContextPlannerInputVO.java`
- `ContextPlannerOutputVO.java`
- `MainAgentActionVO.java`
- `StateDeltaVO.java`
- `FinalAnswerCandidateVO.java`
- `ArtifactDraftVO.java`
- `ArtifactPatchVO.java`
- `RagRequestVO.java`
- `ToolIntentVO.java`
- `AskUserRequestVO.java`
- `AskUserOptionVO.java`
- `UserAnswerVO.java`
- `PlanDraftVO.java`
- `NextActionHintVO.java`
- `FailureVO.java`
- `ToolInvocationRequestVO.java`
- `ToolInvocationResultVO.java`
- `ToolReceiptVO.java`
- `PermissionDecisionVO.java`
- `ToolArgumentSourceVO.java`
- `RunTranscriptBlockVO.java`
- `RagVerifierInputVO.java`
- `VerificationResultVO.java`
- `FinalResponseGuardInputVO.java`
- `FinalResponseGuardResultVO.java`
- `FinalResponseVO.java`
- `DeveloperTraceVO.java`
- `AuditRecordVO.java`
- `PayloadRefVO.java`
- `EvidenceRefVO.java`
- `ArtifactRefVO.java`
- `CitationVO.java`

All VOs use Lombok:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
```

### 3.5 Entity Files

Create under `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/`:

- `AgentRunEntity.java`
- `AgentSessionEntity.java`
- `AgentMessageEntity.java`
- `AgentArtifactEntity.java`
- `AgentEvidenceEntity.java`
- `AgentPendingInputEntity.java`
- `AgentMemoryEntity.java`
- `ToolCallEntity.java`
- `ToolApprovalEntity.java`
- `RagQueryEntity.java`
- `AgentRunEventEntity.java`
- `AgentRunTraceEntity.java`
- `AgentRunAuditEntity.java`
- `AgentPayloadEntity.java`
- `AgentNodePromptEntity.java`

Entities are data carriers in this phase. Persistence annotations are not required.

### 3.6 Contract Layer Files

Create under `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/contract/`:

- `AgentComponentCode.java`
- `AgentNodeContract.java`
- `ContractRegistry.java`
- `RawOutputParser.java`
- `RawOutputParseResult.java`
- `ContractValidator.java`
- `ContractValidationResult.java`
- `ContractViolation.java`
- `StateDeltaScopeRules.java`
- `ContractRepairPolicy.java`
- `RecoveryPolicy.java`

### 3.7 Test Files

Create under `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/contract/`:

- `MainAgentActionContractTest.java`
- `ContextPlannerContractTest.java`
- `RawOutputParserTest.java`
- `ContractRegistryTest.java`

## 4. Enum Definitions

### 4.1 Required Constants

Use these exact constants.

`RunStatusEnumVO`:

```text
CREATED, RUNNING, WAITING_USER, COMPLETED, FAILED, CANCELLED
```

`RuntimePhaseEnumVO`:

```text
CREATED, PREPARING_CONTEXT, PLANNING_CONTEXT, BUILDING_STATE_VIEW,
CALLING_MAIN_NODE, VALIDATING_ACTION, HANDLING_ACTION,
EXECUTING_RAG, PREPARING_TOOL, INVOKING_TOOL_RUNTIME, VERIFYING_TOOL,
VERIFYING_RAG, VERIFYING_FINAL, REPAIRING_CONTRACT, REPAIRING_FINAL,
WAITING_USER, RESOLVING_USER_ANSWER, COMPLETED, FAILED, CANCELLED
```

`MainAgentActionTypeEnumVO`:

```text
FINAL, CREATE_ARTIFACT, UPDATE_ARTIFACT, RETRIEVE_RAG, CALL_TOOL,
ASK_USER, PLAN, CONTINUE, REPAIR_FINAL, FAIL
```

`StateDeltaFieldEnumVO`:

```text
FINAL_ANSWER_CANDIDATE, ARTIFACT_DRAFT, ARTIFACT_PATCH, RAG_REQUEST,
TOOL_INTENT, ASK_USER_REQUEST, PLAN_DRAFT, NEXT_ACTION_HINT, FAILURE
```

Each constant stores the JSON field name:

```text
finalAnswerCandidate, artifactDraft, artifactPatch, ragRequest,
toolIntent, askUserRequest, planDraft, nextActionHint, failure
```

`ContextLevelEnumVO`:

```text
NONE, METADATA_ONLY, SUMMARY_ONLY, SUMMARY_PLUS_SNIPPET, FULL_TEXT, CHUNKED_CONTEXT
```

`ContextPlannerStatusEnumVO`:

```text
READY, NEEDS_USER_CLARIFICATION, CONTEXT_OVER_BUDGET, FAILED
```

`EvidenceTypeEnumVO`:

```text
RAG, TOOL, MEMORY, USER_CONFIRMATION, ARTIFACT
```

`PendingInputTypeEnumVO`:

```text
CLARIFICATION, CONFIRMATION, TOOL_APPROVAL, CONTEXT_SELECTION, USER_ACTION_REQUIRED
```

`InputModeEnumVO`:

```text
SINGLE_CHOICE, MULTI_CHOICE, FREE_TEXT, SINGLE_CHOICE_OR_FREE_TEXT
```

`ToolCallStatusEnumVO`:

```text
REQUESTED, CALLED, SUCCESS, FAILED, NOT_CALLED, BLOCKED,
NEEDS_USER_ACTION, TOOL_NOT_AVAILABLE, INVALID_TOOL_INTENT,
PERMISSION_DENIED, PARTIAL_SUCCESS
```

`ToolInvocationStatusEnumVO`:

```text
SUCCESS, FAILED, NOT_CALLED, NEEDS_USER_ACTION, INVALID_TOOL_INTENT,
TOOL_NOT_AVAILABLE, PERMISSION_DENIED, PARTIAL_SUCCESS
```

`ToolApprovalStatusEnumVO`:

```text
PENDING, APPROVED, REJECTED, EXPIRED, CANCELLED
```

`UserApprovalDecisionEnumVO`:

```text
APPROVED, REJECTED, CANCELLED
```

`VerificationStatusEnumVO`:

```text
PASSED, FAILED, NEEDS_RETRY, NEEDS_USER, SKIPPED
```

`FinalGuardFailureCodeEnumVO`:

```text
FINAL_EMPTY, FINAL_INTERNAL_LEAK, FINAL_RAW_JSON_LEAK,
FINAL_FORMAT_VIOLATION, FINAL_INVALID_CITATION,
FINAL_FALSE_TOOL_CLAIM, FINAL_TOO_LONG
```

`FailureCodeEnumVO`:

```text
CONTRACT_INVALID_JSON, CONTRACT_MISSING_REQUIRED_FIELD,
CONTRACT_UNKNOWN_ACTION, CONTRACT_FORBIDDEN_FIELD,
CONTRACT_STATE_SCOPE_VIOLATION, CONTEXT_PLAN_INVALID,
CONTEXT_OVER_BUDGET, USER_INPUT_CANCELLED,
RAG_NO_HITS, RAG_UNSUPPORTED_CLAIM, RAG_GROUNDING_FAILED,
TOOL_NOT_CALLED, TOOL_RECEIPT_MISSING, TOOL_INVOCATION_FAILED,
TOOL_PERMISSION_DENIED, TOOL_APPROVAL_REJECTED, TOOL_NOT_AVAILABLE,
TOOL_INVALID_ARGUMENTS, FINAL_EMPTY, FINAL_INTERNAL_LEAK,
FINAL_RAW_JSON_LEAK, FINAL_FORMAT_VIOLATION, FINAL_INVALID_CITATION,
FINAL_FALSE_TOOL_CLAIM, FINAL_TOO_LONG, LOOP_LIMIT_EXCEEDED,
RUNTIME_INTERNAL_ERROR
```

`RecoveryActionEnumVO`:

```text
CONTRACT_REPAIR, SAFE_EXTRACTION, REPAIR_FINAL, RETRY_TOOL,
RETURN_FAILURE_EVIDENCE, ASK_USER, DENY_TOOL, RETRY_RAG,
COMPRESS_CONTEXT, SAFE_FAILURE, PARTIAL_RESULT
```

`TranscriptBlockTypeEnumVO`:

```text
USER_MESSAGE, CONTEXT_PLAN, STATE_VIEW_SUMMARY, ASSISTANT_ACTION,
TOOL_CALL_REQUEST, TOOL_RESULT, RAG_REQUEST, RAG_RESULT, ARTIFACT_REF,
USER_REPLY, VERIFIER_RESULT, FINAL_RESPONSE, COMPACTION_SUMMARY, ERROR
```

`PermissionModeEnumVO`:

```text
ALLOW, ASK_USER, DENY
```

`RequiredPermissionEnumVO`:

```text
READ_ONLY, WORKSPACE_READ, WORKSPACE_WRITE, EXTERNAL_READ,
EXTERNAL_WRITE, DANGEROUS
```

`ApprovalPolicyEnumVO`:

```text
NEVER, ASK_USER_BEFORE_EXECUTE, ASK_USER_ON_RISK, REQUIRE_EXISTING_APPROVAL
```

`PermissionDecisionTypeEnumVO`:

```text
ALLOW, ASK_USER, DENY
```

`McpTransportTypeEnumVO`:

```text
SSE, STDIO
```

`ToolArgumentSourceTypeEnumVO`:

```text
ARTIFACT, EVIDENCE, USER_INPUT, INLINE_VALUE
```

`ToolArgumentContentModeEnumVO`:

```text
METADATA_ONLY, SUMMARY_ONLY, FULL_TEXT_REQUIRED, INLINE_VALUE
```

## 5. Core Value Object Field Contracts

### 5.1 `MainAgentActionVO`

Fields:

```java
private MainAgentActionTypeEnumVO action;
private Double confidence;
private String userVisibleThought;
private String reasonCode;
private StateDeltaVO stateDelta;
private Map<String, Object> safety;
```

Rules:

- `action` is required.
- `stateDelta` is required.
- `confidence` must be between `0.0` and `1.0` when present.
- `userVisibleThought` is optional and must not contain internal trace, prompt, verifier, node, runtime, JSON schema, or raw model output text.

### 5.2 `StateDeltaVO`

Fields:

```java
private FinalAnswerCandidateVO finalAnswerCandidate;
private ArtifactDraftVO artifactDraft;
private ArtifactPatchVO artifactPatch;
private RagRequestVO ragRequest;
private ToolIntentVO toolIntent;
private AskUserRequestVO askUserRequest;
private PlanDraftVO planDraft;
private NextActionHintVO nextActionHint;
private FailureVO failure;
```

Rules:

- No lifecycle fields are allowed in `StateDeltaVO`.
- `runStatus`, `runtimePhase`, `loopIndex`, `trace`, `audit`, `toolReceipt`, `verifierResult`, and `nextState` are Runtime-owned and must not exist in node output payloads.

### 5.3 StateDelta Allowed Fields By Action

`StateDeltaScopeRules` must define this exact mapping:

| Action | Allowed StateDelta fields |
|---|---|
| `FINAL` | `finalAnswerCandidate` |
| `CREATE_ARTIFACT` | `artifactDraft`, `finalAnswerCandidate` |
| `UPDATE_ARTIFACT` | `artifactPatch`, `finalAnswerCandidate` |
| `RETRIEVE_RAG` | `ragRequest`, `nextActionHint` |
| `CALL_TOOL` | `toolIntent`, `nextActionHint` |
| `ASK_USER` | `askUserRequest`, `nextActionHint` |
| `PLAN` | `planDraft`, `nextActionHint` |
| `CONTINUE` | `nextActionHint` |
| `REPAIR_FINAL` | `finalAnswerCandidate` |
| `FAIL` | `failure`, `finalAnswerCandidate` |

`FAIL` may include `finalAnswerCandidate`, but the final text must still pass through the future `FinalResponseGuard`. Phase 1 only validates the allowed fields.

### 5.4 `FinalAnswerCandidateVO`

Fields:

```java
private String content;
private String format;
private List<CitationVO> citations;
private List<AskUserOptionVO> followUpOptions;
private String repairNotes;
```

Required for `FINAL` and `REPAIR_FINAL`:

- `content`

### 5.5 `RagRequestVO`

Fields:

```java
private String query;
private String knowledgeCode;
private Integer topK;
private Map<String, Object> filters;
private String expectedUse;
```

Required:

- `query`

### 5.6 `ToolIntentVO`

Fields:

```java
private String capabilityCode;
private String serverCode;
private String toolName;
private Map<String, Object> arguments;
private List<ToolArgumentSourceVO> argumentSources;
private String expectedOutcome;
private String riskReason;
```

Required:

- `capabilityCode`
- `toolName`

`serverCode` may be empty in Phase 1 because later `McpToolRegistry` may resolve the server from capability metadata.

### 5.7 `AskUserRequestVO`

Fields:

```java
private PendingInputTypeEnumVO pendingInputType;
private InputModeEnumVO inputMode;
private String question;
private List<AskUserOptionVO> options;
private Boolean allowFreeText;
private String sourceComponentCode;
private String continuationKey;
private Map<String, Object> continuationPayload;
```

Rules:

- `question` is required.
- `inputMode` is required.
- If `inputMode=SINGLE_CHOICE`, `allowFreeText` must be `false`.
- If `pendingInputType=TOOL_APPROVAL`, `inputMode` must be `SINGLE_CHOICE`, `allowFreeText` must be `false`, and options must include approve and reject values.

### 5.8 `UserAnswerVO`

Fields:

```java
private String pendingInputId;
private String selectedOptionId;
private Object selectedValue;
private String freeText;
private Boolean cancelled;
private String rawUserText;
```

Rules:

- This object is Java-normalized later by `UserReplyProcessor`.
- Phase 1 validates only basic field shape.
- No LLM-based user-input resolver exists in MVP.

### 5.9 `ContextPlannerOutputVO`

Fields:

```java
private ContextPlannerStatusEnumVO status;
private List<ContextSelectionVO> selectedContexts;
private AskUserRequestVO clarificationRequest;
private Integer estimatedTokens;
private String reasonCode;
private String notes;
```

Create nested or separate VO:

`ContextSelectionVO.java` with fields:

```java
private String sourceType;
private String sourceId;
private ContextLevelEnumVO contextLevel;
private Integer priority;
private String reason;
```

Rules:

- `status` is required.
- `READY` requires `selectedContexts`.
- `NEEDS_USER_CLARIFICATION` requires `clarificationRequest`.
- `CONTEXT_OVER_BUDGET` requires `estimatedTokens`.
- Context levels must use `ContextLevelEnumVO`.

### 5.10 Tool And Verification VOs

`ToolInvocationRequestVO` fields:

```java
private String toolInvocationId;
private String runId;
private String capabilityCode;
private String serverCode;
private String toolName;
private Map<String, Object> arguments;
private PermissionDecisionVO permissionDecision;
private String approvalId;
```

`ToolInvocationResultVO` fields:

```java
private String toolInvocationId;
private ToolInvocationStatusEnumVO status;
private ToolReceiptVO receipt;
private FailureCodeEnumVO failureCode;
private String failureMessage;
```

`VerificationResultVO` fields:

```java
private VerificationStatusEnumVO status;
private String verifierCode;
private FailureCodeEnumVO failureCode;
private String reason;
private Double confidence;
private Map<String, Object> evidence;
```

`FinalResponseGuardResultVO` fields:

```java
private Boolean passed;
private List<FinalGuardFailureCodeEnumVO> failureCodes;
private String safeContent;
private String repairInstruction;
```

## 6. Entity Field Minimums

Create these entities with id/status/timestamp fields only where needed in Phase 1. Full database mapping comes in Phase 2.

Required fields:

- `AgentRunEntity`: `runId`, `sessionId`, `status`, `runtimePhase`, `loopIndex`, `ragWasUsed`, `createdAt`, `updatedAt`
- `AgentSessionEntity`: `sessionId`, `userId`, `title`, `createdAt`, `updatedAt`
- `AgentMessageEntity`: `messageId`, `sessionId`, `runId`, `role`, `content`, `contentPayloadId`, `createdAt`
- `AgentArtifactEntity`: `artifactId`, `sessionId`, `runId`, `artifactType`, `title`, `version`, `payloadId`, `summary`, `createdAt`
- `AgentEvidenceEntity`: `evidenceId`, `runId`, `evidenceType`, `sourceId`, `payloadId`, `summary`, `createdAt`
- `AgentPendingInputEntity`: `pendingInputId`, `runId`, `pendingInputType`, `inputMode`, `status`, `question`, `optionsPayloadId`, `continuationKey`, `continuationPayloadId`, `createdAt`, `answeredAt`
- `AgentMemoryEntity`: `memoryId`, `sessionId`, `memoryType`, `summary`, `payloadId`, `createdAt`, `updatedAt`
- `ToolCallEntity`: `toolCallId`, `runId`, `toolInvocationId`, `capabilityCode`, `serverCode`, `toolName`, `status`, `receiptPayloadId`, `createdAt`
- `ToolApprovalEntity`: `approvalId`, `runId`, `toolCallId`, `approvalKey`, `argumentsHash`, `status`, `createdAt`, `decidedAt`
- `RagQueryEntity`: `ragQueryId`, `runId`, `query`, `knowledgeCode`, `hitsPayloadId`, `createdAt`
- `AgentRunEventEntity`: `eventId`, `runId`, `eventType`, `userVisible`, `payloadId`, `createdAt`
- `AgentRunTraceEntity`: `traceId`, `runId`, `componentCode`, `traceType`, `payloadId`, `payloadPreview`, `createdAt`
- `AgentRunAuditEntity`: `auditId`, `runId`, `actor`, `action`, `payloadId`, `createdAt`
- `AgentPayloadEntity`: `payloadId`, `payloadType`, `payloadText`, `createdAt`
- `AgentNodePromptEntity`: `promptId`, `componentCode`, `promptType`, `content`, `enabled`, `createdAt`, `updatedAt`

## 7. Contract Layer Design

### 7.1 `AgentComponentCode`

Create constants:

```java
public static final String CONTEXT_PLANNER = "CONTEXT_PLANNER";
public static final String MAIN_AGENT = "MAIN_AGENT";
public static final String RAG_VERIFIER = "RAG_VERIFIER";
public static final String FINAL_RESPONSE_GUARD = "FINAL_RESPONSE_GUARD";
public static final String FINAL_REPAIR = "FINAL_REPAIR";
public static final String CONTRACT_REPAIR = "CONTRACT_REPAIR";
public static final String TOOL_VERIFIER = "TOOL_VERIFIER";
```

### 7.2 `AgentNodeContract`

Fields:

```java
private String componentCode;
private String contractVersion;
private Set<String> requiredTopLevelFields;
private Set<String> forbiddenTopLevelFields;
private Set<String> allowedActionCodes;
private Map<MainAgentActionTypeEnumVO, Set<StateDeltaFieldEnumVO>> stateDeltaScopes;
```

### 7.3 `ContractRegistry`

Required methods:

```java
public AgentNodeContract resolve(String componentCode, String contractVersion);
public AgentNodeContract mainAgentV1();
public AgentNodeContract contextPlannerV1();
public AgentNodeContract ragVerifierV1();
public AgentNodeContract finalResponseGuardV1();
```

Rules:

- Use `componentCode`, not `nodeCode`, because Java verifiers and guards also have contracts.
- `MAIN_AGENT` contract owns all action scope rules.
- `CONTEXT_PLANNER` contract owns status shape rules.
- Unsupported component code throws `IllegalArgumentException`.

### 7.4 `RawOutputParser`

Required behavior:

- Parse strict JSON object when raw output is a JSON object.
- Extract a single JSON object from fenced code block when the rest of the text is empty or whitespace.
- Reject output that contains multiple JSON objects.
- Reject output with non-whitespace prose before or after the JSON object.
- Return `RawOutputParseResult` with `success`, `jsonObject`, `rawText`, `failureCode`, and `failureMessage`.

Allowed implementation:

- Use Fastjson2 `JSON.parseObject`.
- Use deterministic brace scanning for safe extraction.
- Do not ask an LLM to repair output in Phase 1.

### 7.5 `ContractValidator`

Required methods:

```java
public ContractValidationResult validateMainAgentAction(MainAgentActionVO action);
public ContractValidationResult validateContextPlannerOutput(ContextPlannerOutputVO output);
public ContractValidationResult validateToolInvocationResult(ToolInvocationResultVO output);
public ContractValidationResult validateVerificationResult(VerificationResultVO output);
public ContractValidationResult validateFinalResponseGuardResult(FinalResponseGuardResultVO output);
```

Required validation:

- Unknown action fails with `CONTRACT_UNKNOWN_ACTION`.
- Missing required field fails with `CONTRACT_MISSING_REQUIRED_FIELD`.
- Forbidden lifecycle/debug fields fail with `CONTRACT_FORBIDDEN_FIELD`.
- StateDelta out-of-scope fields fail with `CONTRACT_STATE_SCOPE_VIOLATION`.
- ContextPlanner invalid status or missing clarification details fails with `CONTEXT_PLAN_INVALID`.
- Tool approval free-text mismatch fails with `CONTRACT_MISSING_REQUIRED_FIELD` or `CONTRACT_STATE_SCOPE_VIOLATION`, depending on shape.

### 7.6 `ContractValidationResult`

Fields:

```java
private boolean passed;
private FailureCodeEnumVO failureCode;
private String message;
private List<ContractViolation> violations;
```

### 7.7 `ContractViolation`

Fields:

```java
private String fieldPath;
private String violationType;
private String message;
```

### 7.8 `RecoveryPolicy`

Create a static mapping from `FailureCodeEnumVO` to `RecoveryActionEnumVO` for every failure code listed in Section 4.1.

Minimum mapping:

- Contract parse/scope failures -> `CONTRACT_REPAIR`
- Context over budget -> `COMPRESS_CONTEXT`
- User cancelled -> `SAFE_FAILURE`
- RAG no hits -> `ASK_USER` or `PARTIAL_RESULT`
- RAG unsupported/grounding failures -> `REPAIR_FINAL`
- Tool not called/receipt missing/invocation failed -> `RETRY_TOOL`
- Tool permission denied/approval rejected -> `DENY_TOOL`
- Final guard failures -> `REPAIR_FINAL`
- Loop limit/runtime internal -> `SAFE_FAILURE`

The test must assert no `FailureCodeEnumVO` is unmapped.

## 8. App Configuration Design

### 8.1 `AutoAgentRuntimeProperties`

Prefix:

```java
@ConfigurationProperties(prefix = "auto-agent.runtime")
```

Fields:

```java
private Integer maxLoops = 8;
private Integer maxRepairAttempts = 2;
private Integer maxContractRepairAttempts = 1;
private Long pendingInputTimeoutSeconds = 1800L;
private Boolean enabled = false;
```

### 8.2 `AutoAgentContextProperties`

Prefix:

```java
@ConfigurationProperties(prefix = "auto-agent.context")
```

Fields:

```java
private Integer maxStateViewTokens = 12000;
private Integer maxArtifactInlineChars = 16000;
private Integer maxEvidenceSummaryChars = 4000;
private Integer recentMessageLimit = 12;
private Integer candidateArtifactLimit = 10;
private Integer candidateMemoryLimit = 10;
```

### 8.3 `AutoAgentNodeProperties`

Prefix:

```java
@ConfigurationProperties(prefix = "auto-agent.nodes")
```

Fields:

```java
private Map<String, NodeConfig> configs = new HashMap<>();

@Data
public static class NodeConfig {
    private String modelCode;
    private Double temperature = 0.2;
    private Integer maxOutputTokens = 2048;
    private String contractVersion = "v1";
    private Boolean enabled = true;
}
```

Required default keys:

```text
CONTEXT_PLANNER, MAIN_AGENT, RAG_VERIFIER, FINAL_REPAIR, CONTRACT_REPAIR
```

### 8.4 `AutoAgentRagProperties`

Prefix:

```java
@ConfigurationProperties(prefix = "auto-agent.rag")
```

Fields:

```java
private Boolean enabled = true;
private Integer defaultTopK = 5;
private Integer maxTopK = 10;
private Integer maxHitChars = 4000;
```

### 8.5 `AutoAgentMcpProperties`

Prefix:

```java
@ConfigurationProperties(prefix = "auto-agent.mcp")
```

Fields:

```java
private Boolean enabled = true;
private Map<String, McpServerConfig> servers = new HashMap<>();

@Data
public static class McpServerConfig {
    private String serverCode;
    private McpTransportTypeEnumVO transportType;
    private String endpoint;
    private List<String> command;
    private Map<String, String> environment = new HashMap<>();
    private Boolean enabled = true;
}
```

### 8.6 `AutoAgentCapabilityProperties`

Prefix:

```java
@ConfigurationProperties(prefix = "auto-agent.capabilities")
```

Fields:

```java
private Map<String, CapabilityConfig> tools = new HashMap<>();

@Data
public static class CapabilityConfig {
    private String capabilityCode;
    private String serverCode;
    private String toolName;
    private RequiredPermissionEnumVO requiredPermission;
    private PermissionModeEnumVO permissionMode;
    private ApprovalPolicyEnumVO approvalPolicy;
    private ToolArgumentContentModeEnumVO defaultContentMode;
    private Boolean enabled = true;
}
```

### 8.7 `AutoAgentDebugProperties`

Prefix:

```java
@ConfigurationProperties(prefix = "auto-agent.debug")
```

Fields:

```java
private Boolean debugApiEnabled = false;
private Boolean debugSseEnabled = false;
private Boolean debugPayloadPreviewEnabled = false;
private Integer debugPayloadPreviewMaxChars = 1000;
```

## 9. Targeted Tests

### 9.1 `MainAgentActionContractTest`

Required test cases:

1. `final_action_allows_only_final_answer_candidate`
2. `call_tool_rejects_final_answer_candidate`
3. `ask_user_tool_approval_rejects_free_text`
4. `unknown_action_is_rejected`
5. `runtime_owned_fields_are_rejected_from_raw_payload`

Test assertions:

- `FINAL + finalAnswerCandidate` passes.
- `CALL_TOOL + toolIntent` passes.
- `CALL_TOOL + finalAnswerCandidate` fails with `CONTRACT_STATE_SCOPE_VIOLATION`.
- `ASK_USER` with `pendingInputType=TOOL_APPROVAL`, `inputMode=SINGLE_CHOICE_OR_FREE_TEXT`, or `allowFreeText=true` fails.
- Raw JSON containing `runtimePhase`, `trace`, or `toolReceipt` fails before domain mutation.

### 9.2 `ContextPlannerContractTest`

Required test cases:

1. `ready_requires_selected_contexts`
2. `needs_user_clarification_requires_clarification_request`
3. `context_over_budget_requires_estimated_tokens`
4. `invalid_context_level_is_rejected`

### 9.3 `RawOutputParserTest`

Required test cases:

1. `parse_strict_json_object`
2. `parse_json_object_from_clean_fenced_block`
3. `reject_prose_before_json`
4. `reject_prose_after_json`
5. `reject_multiple_json_objects`
6. `reject_malformed_json`

### 9.4 `ContractRegistryTest`

Required test cases:

1. `main_agent_contract_contains_all_actions`
2. `main_agent_contract_contains_state_delta_scopes`
3. `all_failure_codes_have_recovery_action`
4. `unknown_component_code_is_rejected`

## 10. Execution Tasks

### Task 1: Add Package Skeleton

**Files:**

- Create all `package-info.java` files listed in Section 3.1.

- [ ] Create package marker files with a one-sentence package comment.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 2: Add Configuration Property Classes

**Files:**

- Create all classes listed in Section 3.2.

- [ ] Implement fields from Section 8.
- [ ] Annotate each class with `@Data` and `@ConfigurationProperties`.
- [ ] Do not add `@Component` unless the existing app config style requires it after inspection.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 3: Add Enums

**Files:**

- Create all enum classes listed in Section 3.3.

- [ ] Implement every constant exactly as listed in Section 4.
- [ ] Add `code`, `info`, `code()`, `info()`, and `ofCode(String code)`.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 4: Add Core Value Objects

**Files:**

- Create all VO classes listed in Section 3.4.

- [ ] Implement fields from Section 5.
- [ ] For fields not fully used until later phases, use `String`, enum, `PayloadRefVO`, `Map<String, Object>`, or `List<...>` as listed.
- [ ] Do not add validation annotations yet; validation belongs in `ContractValidator`.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 5: Add Domain Entities

**Files:**

- Create all entity classes listed in Section 3.5.

- [ ] Implement fields from Section 6.
- [ ] Do not add MyBatis annotations.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 6: Add Contract Registry And Scope Rules

**Files:**

- Create files listed in Section 3.6.

- [ ] Implement `AgentComponentCode`.
- [ ] Implement `AgentNodeContract`.
- [ ] Implement `StateDeltaScopeRules` with the exact table from Section 5.3.
- [ ] Implement `ContractRegistry` with component-code based lookup.
- [ ] Implement `RecoveryPolicy` with all `FailureCodeEnumVO` values mapped.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 7: Add Raw Output Parser

**Files:**

- `RawOutputParser.java`
- `RawOutputParseResult.java`

- [ ] Implement strict JSON object parsing.
- [ ] Implement clean fenced-code JSON extraction.
- [ ] Reject prose around JSON.
- [ ] Reject multiple JSON objects.
- [ ] Return structured failure code and message.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 8: Add Contract Validator

**Files:**

- `ContractValidator.java`
- `ContractValidationResult.java`
- `ContractViolation.java`

- [ ] Implement methods from Section 7.5.
- [ ] Enforce StateDelta scope table.
- [ ] Enforce ContextPlanner status-specific requirements.
- [ ] Enforce tool approval input mode rules.
- [ ] Enforce forbidden lifecycle/debug fields on raw parsed payloads.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 9: Add Contract Tests

**Files:**

- Create all tests listed in Section 3.7.

- [ ] Implement test cases listed in Section 9.
- [ ] Use JUnit4 style consistent with current app tests.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=MainAgentActionContractTest,ContextPlannerContractTest,RawOutputParserTest,ContractRegistryTest" test
```

Expected result:

```text
BUILD SUCCESS
```

### Task 10: Phase 0/1 Self Review

- [ ] Run:

```powershell
rg -n "UserInputResolverNode|ToolExecutionNode|tool_node_run_id|answerContract" ai-agent-station-study-domain ai-agent-station-study-app docs/architecture/auto-agent-main-loop-harness-redesign-spec.md
```

Expected:

```text
No matches in new Phase 0/1 code.
```

- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

## 11. Acceptance Checklist

- [ ] New package skeleton exists and compiles.
- [ ] New configuration classes exist and compile.
- [ ] Every required enum exists with exact constants.
- [ ] Every required VO and entity exists with Phase 1 fields.
- [ ] `ContractRegistry` uses component code.
- [ ] `StateDeltaScopeRules` covers every `MainAgentActionTypeEnumVO`.
- [ ] `RecoveryPolicy` covers every `FailureCodeEnumVO`.
- [ ] `RawOutputParser` rejects prose-wrapped JSON and multiple JSON objects.
- [ ] `ContractValidator` rejects Runtime-owned fields from node outputs.
- [ ] Contract tests pass.
- [ ] Old Node1-4 behavior is untouched.
- [ ] No runtime loop, LLM invocation, RAG, MCP, DB, SSE, or frontend behavior is added in this phase.

## 12. Worker Split Guidance

If using subagents, split work by non-overlapping file ownership:

- Worker A: app configuration property classes.
- Worker B: enum classes.
- Worker C: VO/entity classes.
- Worker D: contract registry, parser, validator.
- Worker E: contract tests.

Workers must not edit the same files concurrently. The integrator reviews all outputs against Sections 4-11 before compiling.

