# AutoAgent Phase 2 Persistence Repository Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the database schema, DAO, PO, domain repository interfaces, infrastructure repository adapters, and persistence boundary tests required by the AutoAgent main-loop harness.

**Architecture:** Runtime-owned data is split by responsibility instead of being stored in one large dynamic context blob. Normal frontend messages, replay transcript, debug trace, audit records, payloads, evidence, artifacts, pending input, RAG, and tool records are stored separately. Domain code depends on repository interfaces; infrastructure owns MyBatis DAOs, PO classes, mapper XML, and SQL scripts.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Maven multi-module project, MyBatis, MySQL-compatible DDL, Lombok, JUnit4, DDD package layout under `yhx.com`.

---

## 0. Execution Rules

- Start this phase only after Phase 0/1 compiles.
- Do not change Runtime behavior in this phase.
- Do not call LLM, RAG, MCP, or SSE in this phase.
- Do not expose debug trace, raw payload, prompt, model output, or tool receipt through normal message repository methods.
- Do not store full prompts, raw model output, raw tool receipts, full StateView, or large artifact bodies directly in normal event/message rows.
- Do not commit unless the user explicitly asks.

## 1. Source Of Truth

Use the English canonical spec only:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`

Primary spec sections:

- Section 7: Persistence Design
- Section 8: DDD Package Layout
- Section 11: Logging, Trace, Audit, And Observability
- Section 12: Testing Strategy

## 2. Phase Boundary

### In Scope

- SQL DDL for all Phase 2 tables.
- Infrastructure PO classes matching table columns.
- Infrastructure DAO interfaces.
- MyBatis mapper XML files.
- Domain repository interfaces.
- Infrastructure repository implementations.
- Payload storage for DB-backed text/JSON content.
- Boundary tests proving user-visible data and debug/internal data remain separated.

### Out Of Scope

- Runtime loop.
- Context planning.
- Artifact resolution policy.
- Prompt assembly.
- Node invocation.
- RAG execution.
- MCP tool invocation.
- SSE controllers.
- Frontend debug panel.
- Data migration from old node-trace log files.

## 3. Table Groups And Ownership

| Storage group | Tables | Normal frontend can read directly |
|---|---|---|
| Conversation | `agent_session`, `agent_message` | Yes, through conversation API only |
| Run lifecycle | `agent_run`, `agent_run_state_snapshot`, `agent_run_transcript` | No direct normal UI access |
| Memory | `agent_conversation_summary`, `agent_long_term_memory`, `agent_memory_event` | No direct normal UI access |
| Artifact | `agent_artifact`, `agent_artifact_alias`, `agent_artifact_relation` | Artifact summary and content APIs only |
| Payload | `agent_payload` | No direct normal UI access |
| Evidence | `agent_evidence` | No direct normal UI access |
| Pending input | `agent_pending_input` | Yes, only user-facing pending input projection |
| Tool | `agent_tool_call`, `agent_tool_approval`, `agent_tool_verification` | No direct normal UI access |
| RAG | `agent_rag_query`, `agent_rag_hit` | No direct normal UI access |
| Event/trace/audit | `agent_run_event`, `agent_run_trace`, `agent_run_audit` | Only `agent_run_event` in normal UI |
| Prompt | `agent_node_prompt` | No direct normal UI access |

The repository layer must preserve this separation. A method named `listRecentMessages` must never join trace, audit, payload, transcript, tool receipt, or verifier rows.

## 4. SQL File Plan

Create:

- `docs/dev-ops/mysql/sql/auto-agent-main-loop-harness.sql`

Modify only when the project convention requires adding this file to a master SQL script:

- `docs/dev-ops/mysql/sql/ai-agent-station-study.sql`

Preferred implementation:

- Keep the new harness DDL in `auto-agent-main-loop-harness.sql`.
- Add a short comment in the master SQL file only if existing project conventions include child SQL references.
- Do not mix old agent client config tables with new runtime persistence tables.

### 4.1 DDL Naming Rules

- Public ids use `varchar(64)`.
- Enum/status columns use `varchar(64)`.
- Summary/title/question columns use `varchar(512)` unless the spec requires `text`.
- Payload content uses `longtext`.
- Booleans use `tinyint(1)`.
- Timestamps use `datetime`.
- Primary key column is `id bigint unsigned not null auto_increment`.
- Every table uses `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`.

## 5. Required DDL Tables

The SQL file must create these tables in this order:

1. `agent_session`
2. `agent_message`
3. `agent_run`
4. `agent_run_state_snapshot`
5. `agent_run_transcript`
6. `agent_conversation_summary`
7. `agent_long_term_memory`
8. `agent_memory_event`
9. `agent_payload`
10. `agent_artifact`
11. `agent_artifact_alias`
12. `agent_artifact_relation`
13. `agent_evidence`
14. `agent_pending_input`
15. `agent_tool_call`
16. `agent_tool_approval`
17. `agent_tool_verification`
18. `agent_rag_query`
19. `agent_rag_hit`
20. `agent_run_event`
21. `agent_run_trace`
22. `agent_run_audit`
23. `agent_node_prompt`

### 5.1 Required Index Rules

Each table must include indexes from spec Section 7.

Additional mandatory indexes for Runtime lookups:

- `agent_pending_input`: `(pending_id, status)`
- `agent_tool_approval`: `(approval_key, status)`
- `agent_payload`: `(payload_id, payload_type)`
- `agent_run_transcript`: `(run_id, block_type, seq)`
- `agent_run_event`: `(run_id, seq)`
- `agent_run_trace`: `(run_id, seq)`

### 5.2 Payload Reference Rules

These columns must store payload ids, not raw content:

- `agent_message.content_ref`
- `agent_run_state_snapshot.state_ref`
- `agent_run_transcript.payload_ref`
- `agent_artifact.content_ref`
- `agent_pending_input.options_ref`
- `agent_pending_input.answer_schema_ref`
- `agent_pending_input.continuation_ref`
- `agent_pending_input.user_answer_ref`
- `agent_tool_call.input_schema_ref`
- `agent_tool_call.intent_ref`
- `agent_tool_call.arguments_ref`
- `agent_tool_call.receipt_ref`
- `agent_tool_approval.options_ref`
- `agent_tool_approval.user_answer_ref`
- `agent_tool_verification.detail_ref`
- `agent_rag_query.filters_ref`
- `agent_rag_hit.chunk_ref`
- `agent_run_trace.payload_ref`
- `agent_node_prompt.content_ref`

## 6. Infrastructure DAO And PO Files

### 6.1 DAO Interfaces

Create under:

- `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/`

Required files:

- `IAgentSessionDao.java`
- `IAgentMessageDao.java`
- `IAgentRunDao.java`
- `IAgentRunStateSnapshotDao.java`
- `IAgentRunTranscriptDao.java`
- `IAgentConversationSummaryDao.java`
- `IAgentLongTermMemoryDao.java`
- `IAgentMemoryEventDao.java`
- `IAgentPayloadDao.java`
- `IAgentArtifactDao.java`
- `IAgentArtifactAliasDao.java`
- `IAgentArtifactRelationDao.java`
- `IAgentEvidenceDao.java`
- `IAgentPendingInputDao.java`
- `IAgentToolCallDao.java`
- `IAgentToolApprovalDao.java`
- `IAgentToolVerificationDao.java`
- `IAgentRagQueryDao.java`
- `IAgentRagHitDao.java`
- `IAgentRunEventDao.java`
- `IAgentRunTraceDao.java`
- `IAgentRunAuditDao.java`
- `IAgentNodePromptDao.java`

Each DAO interface must:

- Use `@Mapper`.
- Use PO types only.
- Avoid domain entity types.
- Return `int` for update methods.
- Return nullable PO for single-row query methods.
- Return `List<PO>` for list methods.

### 6.2 PO Classes

Create under:

- `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/po/`

Required files:

- `AgentSessionPO.java`
- `AgentMessagePO.java`
- `AgentRunPO.java`
- `AgentRunStateSnapshotPO.java`
- `AgentRunTranscriptPO.java`
- `AgentConversationSummaryPO.java`
- `AgentLongTermMemoryPO.java`
- `AgentMemoryEventPO.java`
- `AgentPayloadPO.java`
- `AgentArtifactPO.java`
- `AgentArtifactAliasPO.java`
- `AgentArtifactRelationPO.java`
- `AgentEvidencePO.java`
- `AgentPendingInputPO.java`
- `AgentToolCallPO.java`
- `AgentToolApprovalPO.java`
- `AgentToolVerificationPO.java`
- `AgentRagQueryPO.java`
- `AgentRagHitPO.java`
- `AgentRunEventPO.java`
- `AgentRunTracePO.java`
- `AgentRunAuditPO.java`
- `AgentNodePromptPO.java`

All PO classes use:

```java
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
```

Use `LocalDateTime` for datetime columns.

### 6.3 Mapper XML Files

Create under the existing MyBatis mapper resource directory. If the directory does not exist yet, create:

- `ai-agent-station-study-infrastructure/src/main/resources/mybatis/mapper/`

Required mapper XML files:

- `AgentSessionDao.xml`
- `AgentMessageDao.xml`
- `AgentRunDao.xml`
- `AgentRunStateSnapshotDao.xml`
- `AgentRunTranscriptDao.xml`
- `AgentConversationSummaryDao.xml`
- `AgentLongTermMemoryDao.xml`
- `AgentMemoryEventDao.xml`
- `AgentPayloadDao.xml`
- `AgentArtifactDao.xml`
- `AgentArtifactAliasDao.xml`
- `AgentArtifactRelationDao.xml`
- `AgentEvidenceDao.xml`
- `AgentPendingInputDao.xml`
- `AgentToolCallDao.xml`
- `AgentToolApprovalDao.xml`
- `AgentToolVerificationDao.xml`
- `AgentRagQueryDao.xml`
- `AgentRagHitDao.xml`
- `AgentRunEventDao.xml`
- `AgentRunTraceDao.xml`
- `AgentRunAuditDao.xml`
- `AgentNodePromptDao.xml`

Mapper namespace must match DAO fully qualified class name.

## 7. Domain Repository Interfaces

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/adapter/repository/`

Required files:

- `IRunRepository.java`
- `IConversationRepository.java`
- `IMemoryRepository.java`
- `IArtifactRepository.java`
- `IEvidenceRepository.java`
- `IPendingInputRepository.java`
- `IToolRepository.java`
- `IRagExecutionRepository.java`
- `IEventTraceRepository.java`
- `IPayloadRepository.java`
- `INodePromptRepository.java`
- `IRunTranscriptRepository.java`

Repository interfaces must use domain entity and VO types only. They must not import infrastructure PO or DAO classes.

### 7.1 Required Repository Methods

`IRunRepository`:

```java
String createRun(AgentRunEntity run);
void updateRunPhase(String runId, RuntimePhaseEnumVO phase);
void updateRunStatus(String runId, RunStatusEnumVO status);
AgentRunEntity findRun(String runId);
void saveStateSnapshot(AgentRunStateSnapshotEntity snapshot);
void completeRun(String runId, String finalMessageId, String finalAnswerRef);
void failRun(String runId, FailureCodeEnumVO errorCode, String errorMessage);
```

`IConversationRepository`:

```java
String createSession(AgentSessionEntity session);
String saveMessage(AgentMessageEntity message);
List<AgentMessageEntity> listRecentMessages(String sessionId, int limit);
AgentSessionEntity findSession(String sessionId);
void updateSessionLastMessage(String sessionId);
```

`IMemoryRepository`:

```java
List<AgentMemoryEntity> findMemoryCandidates(String userId, String sessionId, String query, int limit);
void saveConversationSummary(AgentConversationSummaryEntity summary);
void saveLongTermMemory(AgentMemoryEntity memory);
void recordMemoryEvent(AgentMemoryEventEntity event);
```

`IArtifactRepository`:

```java
String saveArtifact(AgentArtifactEntity artifact);
void saveArtifactAlias(AgentArtifactAliasEntity alias);
void saveArtifactRelation(AgentArtifactRelationEntity relation);
AgentArtifactEntity findArtifactById(String artifactId);
List<AgentArtifactEntity> findArtifactCandidates(String sessionId, String userInput, int limit);
void updateLastMentioned(String artifactId);
```

`IEvidenceRepository`:

```java
String saveEvidence(AgentEvidenceEntity evidence);
List<AgentEvidenceEntity> listRunEvidence(String runId);
List<AgentEvidenceEntity> listEvidenceByType(String runId, EvidenceTypeEnumVO evidenceType);
void markUsedByFinal(String evidenceId);
```

`IPendingInputRepository`:

```java
String savePendingInput(AgentPendingInputEntity pendingInput);
AgentPendingInputEntity findPendingInput(String runId);
AgentPendingInputEntity findPendingInputById(String pendingId);
void markAnswered(String pendingId, String userAnswerRef);
void markCancelled(String pendingId);
void markExpired(String pendingId);
```

`IToolRepository`:

```java
String createToolCall(ToolCallEntity toolCall);
void updateToolCallStatus(String toolCallId, ToolCallStatusEnumVO status);
void saveToolReceipt(String toolCallId, String argumentsRef, String receiptRef);
void saveApproval(ToolApprovalEntity approval);
ToolApprovalEntity findPendingApproval(String runId);
ToolApprovalEntity findApprovalByApprovalKey(String approvalKey);
void markApprovalApproved(String approvalId, String userAnswerRef, LocalDateTime decidedAt);
void markApprovalRejected(String approvalId, String userAnswerRef, LocalDateTime decidedAt);
void markApprovalCancelled(String approvalId, String userAnswerRef, LocalDateTime decidedAt);
void markApprovalExpired(String approvalId, LocalDateTime decidedAt);
void saveToolVerification(AgentToolVerificationEntity verification);
```

`IRagExecutionRepository`:

```java
String saveRagQuery(RagQueryEntity query);
void saveRagHits(List<RagHitEntity> hits);
List<RagHitEntity> listRagHits(String runId);
```

`IEventTraceRepository`:

```java
String appendUserEvent(AgentRunEventEntity event);
String appendDeveloperTrace(AgentRunTraceEntity trace);
void saveAudit(AgentRunAuditEntity audit);
List<AgentRunEventEntity> listUserEvents(String runId);
List<AgentRunTraceEntity> listDeveloperTraces(String runId, Map<String, Object> query);
List<AgentRunTraceEntity> streamDeveloperTraces(String runId, Integer cursor, Integer limit);
List<AgentRunAuditEntity> listAuditRecords(String runId);
```

`IPayloadRepository`:

```java
String savePayload(AgentPayloadEntity payload);
AgentPayloadEntity loadPayload(String payloadId);
String loadPayloadSummary(String payloadId, int maxChars);
```

`INodePromptRepository`:

```java
List<AgentNodePromptEntity> listEnabledPrompts(String agentId, String nodeCode);
AgentNodePromptEntity findPromptByVersion(String agentId, String nodeCode, String promptVersion);
```

`IRunTranscriptRepository`:

```java
String appendBlock(RunTranscriptBlockVO block);
List<RunTranscriptBlockVO> listRunBlocks(String runId);
List<RunTranscriptBlockVO> listBlocksForCompaction(String runId, Integer beforeSeq);
String appendCompactionSummary(RunTranscriptBlockVO block);
```

## 8. Infrastructure Repository Implementations

Create under:

- `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/repository/`

Required files:

- `RunRepository.java`
- `ConversationRepository.java`
- `MemoryRepository.java`
- `ArtifactRepository.java`
- `EvidenceRepository.java`
- `PendingInputRepository.java`
- `ToolRepository.java`
- `RagExecutionRepository.java`
- `EventTraceRepository.java`
- `PayloadRepository.java`
- `NodePromptRepository.java`
- `RunTranscriptRepository.java`

Rules:

- Each implementation is annotated with `@Repository`.
- Each implementation depends on DAO interfaces only.
- Entity-to-PO conversion stays inside repository implementation.
- Domain does not import infrastructure packages.
- Mapper XML does not reference domain classes.

## 9. Payload Repository Details

`PayloadRepository.savePayload` must:

1. Generate `payloadId` when missing.
2. Compute `contentHash` for DB content when missing.
3. Default `storageType` to `DB`.
4. Default `compressed=false`.
5. Default `encrypted=false`.
6. Insert into `agent_payload`.
7. Return `payloadId`.

`PayloadRepository.loadPayloadSummary` must:

1. Load payload by `payloadId`.
2. Return empty string if not found.
3. Return content directly if length is less than or equal to `maxChars`.
4. Return `content.substring(0, maxChars)` when content is longer.
5. Never return `content_path` content in Phase 2.

Phase 2 supports DB payloads only. `FILE` and `OBJECT_STORAGE` are schema-ready but not implemented until a later storage plan.

## 10. Normal UI Boundary Rules

`ConversationRepository.listRecentMessages` must:

- Query only `agent_message`.
- Return rows ordered by `seq desc` or `created_at desc`, then reverse to chronological order if needed by caller.
- Never join `agent_run_trace`.
- Never join `agent_run_transcript`.
- Never join `agent_payload` automatically except for message `content_ref` when the method name explicitly says it loads content.
- Never return `message_type` values from debug tables.

`EventTraceRepository.listUserEvents` must:

- Query only `agent_run_event`.
- Return user-visible event summaries.
- Never include trace payloads.

`EventTraceRepository.listDeveloperTraces` must:

- Query `agent_run_trace`.
- Be considered debug-only.
- Return payload refs and compact summaries, not full payload bodies by default.

## 11. MyBatis Mapper Minimum Methods

Each mapper XML must implement only the methods needed by repository interfaces.

Examples:

`AgentPayloadDao.xml`:

- `insert`
- `queryByPayloadId`

`AgentMessageDao.xml`:

- `insert`
- `queryRecentBySessionId`
- `queryByMessageId`

`AgentRunDao.xml`:

- `insert`
- `updatePhase`
- `updateStatus`
- `queryByRunId`
- `completeRun`
- `failRun`

`AgentToolApprovalDao.xml`:

- `insert`
- `queryPendingByRunId`
- `queryByApprovalKey`
- `markApproved`
- `markRejected`
- `markCancelled`
- `markExpired`

`AgentRunTraceDao.xml`:

- `insert`
- `queryByRunId`
- `streamByRunId`

## 12. Required Tests

Create under:

- `ai-agent-station-study-app/src/test/java/yhx/com/test/infrastructure/agent/persistence/`

Required files:

- `PayloadRepositoryBoundaryTest.java`
- `ConversationRepositoryBoundaryTest.java`
- `ToolApprovalRepositoryBoundaryTest.java`
- `RunTranscriptRepositoryBoundaryTest.java`

### 12.1 `PayloadRepositoryBoundaryTest`

Required test cases:

1. `save_payload_generates_id_and_hash`
2. `load_payload_summary_truncates_content`
3. `load_payload_summary_returns_empty_for_missing_payload`

Use a fake DAO implementation inside the test class when a real test database is not available. The fake DAO must store PO objects in a `Map<String, AgentPayloadPO>`.

### 12.2 `ConversationRepositoryBoundaryTest`

Required test cases:

1. `list_recent_messages_does_not_read_trace_or_payload_tables`
2. `save_message_keeps_content_ref_as_reference`

Use fake `IAgentMessageDao`. Do not add fake trace DAO to this repository test. The absence of trace DAO dependency is part of the boundary assertion.

### 12.3 `ToolApprovalRepositoryBoundaryTest`

Required test cases:

1. `find_approval_by_approval_key_uses_unique_key`
2. `mark_approval_approved_sets_user_answer_and_decided_at`
3. `mark_approval_rejected_does_not_create_tool_receipt`

Use fake `IAgentToolApprovalDao` and fake `IAgentToolCallDao` only if the repository constructor requires both.

### 12.4 `RunTranscriptRepositoryBoundaryTest`

Required test cases:

1. `append_block_preserves_block_type_and_payload_ref`
2. `list_blocks_for_compaction_returns_blocks_before_seq`
3. `append_compaction_summary_uses_compaction_block_type`

## 13. Execution Tasks

### Task 1: Add SQL DDL

**Files:**

- Create: `docs/dev-ops/mysql/sql/auto-agent-main-loop-harness.sql`

- [ ] Add `create table if not exists` statements for all tables in Section 5.
- [ ] Add all indexes from Section 5.1 and spec Section 7.
- [ ] Keep comments short and UTF-8.
- [ ] Run:

```powershell
rg -n "create table if not exists agent_" docs\dev-ops\mysql\sql\auto-agent-main-loop-harness.sql
```

Expected:

```text
23 create-table matches.
```

### Task 2: Add PO Classes

**Files:**

- Create all files listed in Section 6.2.

- [ ] Add Lombok annotations.
- [ ] Use Java field names matching camelCase versions of column names.
- [ ] Use `LocalDateTime` for datetime.
- [ ] Use `String` for enum/status fields at PO level.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-infrastructure -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 3: Add DAO Interfaces

**Files:**

- Create all files listed in Section 6.1.

- [ ] Add `@Mapper`.
- [ ] Define methods required by Section 11.
- [ ] Keep method names aligned with mapper XML ids.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-infrastructure -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 4: Add Mapper XML

**Files:**

- Create all files listed in Section 6.3.

- [ ] Set namespace to DAO fully qualified class name.
- [ ] Implement insert/query/update statements from Section 11.
- [ ] Use explicit column lists.
- [ ] Use explicit result maps.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-infrastructure -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 5: Add Domain Repository Interfaces

**Files:**

- Create all files listed in Section 7.

- [ ] Add exact methods from Section 7.1.
- [ ] Import only domain entity, VO, enum, Java collection, and Java time types.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 6: Add Repository Implementations

**Files:**

- Create all files listed in Section 8.

- [ ] Annotate each with `@Repository`.
- [ ] Inject required DAO interfaces by constructor.
- [ ] Convert domain entity/VO to PO before DAO calls.
- [ ] Convert PO to domain entity/VO after DAO calls.
- [ ] Keep conversion private inside each repository implementation.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-infrastructure -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 7: Implement Payload Boundary

**Files:**

- Modify: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/repository/PayloadRepository.java`
- Modify: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/IAgentPayloadDao.java`
- Modify: `ai-agent-station-study-infrastructure/src/main/resources/mybatis/mapper/AgentPayloadDao.xml`

- [ ] Implement behavior from Section 9.
- [ ] Add null-safe summary loading.
- [ ] Do not implement file/object storage loading.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-infrastructure -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 8: Add Boundary Tests

**Files:**

- Create all files listed in Section 12.

- [ ] Implement fake DAO classes inside tests.
- [ ] Test repository behavior without requiring a live database.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=PayloadRepositoryBoundaryTest,ConversationRepositoryBoundaryTest,ToolApprovalRepositoryBoundaryTest,RunTranscriptRepositoryBoundaryTest" test
```

Expected result:

```text
BUILD SUCCESS
```

### Task 9: Cross-Spec Consistency Scan

- [ ] Run:

```powershell
rg -n "tool_node_run_id|ToolExecutionNode|UserInputResolverNode|answerContract" docs\architecture\auto-agent-main-loop-harness-redesign-spec.md ai-agent-station-study-domain ai-agent-station-study-infrastructure
```

Expected:

```text
No matches in English spec or new Phase 2 code.
```

- [ ] Run:

```powershell
rg -n "agent_run_trace|agent_payload|agent_run_transcript" ai-agent-station-study-infrastructure\src\main\java\yhx\com\infrastructure\adapter\repository\ConversationRepository.java
```

Expected:

```text
No matches.
```

### Task 10: Phase 2 Compile Gate

- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

## 14. Acceptance Checklist

- [ ] SQL file creates all 23 required tables.
- [ ] All table names and key columns match spec Section 7.
- [ ] All PO classes compile.
- [ ] All DAO interfaces compile.
- [ ] All mapper XML files use DAO namespaces.
- [ ] Domain repository interfaces import no infrastructure classes.
- [ ] Infrastructure repository implementations import no trigger/controller classes.
- [ ] Payload repository stores and loads DB payloads.
- [ ] Normal message repository does not read trace, transcript, audit, or raw payload tables.
- [ ] Tool approval repository uses `approval_key` for idempotency.
- [ ] Run transcript repository stores typed block records separately from frontend messages.
- [ ] Boundary tests pass.
- [ ] Old harness behavior remains untouched.

## 15. Worker Split Guidance

If using subagents, split work by non-overlapping file ownership:

- Worker A: SQL DDL file only.
- Worker B: PO classes only.
- Worker C: DAO interfaces and mapper XML only.
- Worker D: domain repository interfaces only.
- Worker E: infrastructure repository implementations only.
- Worker F: boundary tests only.

The integrator must compile after Workers B, C, D, and E, then run all Phase 2 tests after Worker F.

