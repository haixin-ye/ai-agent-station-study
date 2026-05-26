# AutoAgent Vector Memory Progress

## 2026-05-25

- Started vector memory foundation planning.
- Confirmed the user-approved architecture: MySQL and vector database are parallel recall sources.
- Confirmed that neither MySQL nor vector recall should feed MainNode directly.
- Created persistent plan files for plan-execute-replan workflow.
- Re-aligned plan after user clarification:
  - existing recent full turns and summary recall belong to MySQL candidate preparation
  - existing async summary generation is transitional and should move under Memory GC
  - implementation order is now table/infrastructure foundation, two candidate preparers, planner-to-main
    injection, then full Memory GC
- Added performance requirement: MySQL candidate preparation and vector candidate preparation must run
  concurrently with timeout/fallback behavior, rather than sequentially.
- Implemented vector memory domain foundation:
  - vector collection/source/status enums
  - vector record/query/filter/hit VOs
  - vector memory and vector index repository ports
  - Noop vector memory repository
- Implemented vector hit source resolution:
  - vector hits resolve back through turn summary, artifact, and memory repositories before Planner
  - unresolved hits are dropped
  - RAG hits are intentionally left for the RAG pipeline
- Added MySQL vector index sync state:
  - `agent_vector_index` DDL
  - DAO, PO, mapper, and repository adapter
- Added pgvector collection DDL for:
  - `vec_turn_summary`
  - `vec_conversation_summary`
  - `vec_long_term_memory`
  - `vec_user_preference`
  - `vec_artifact_summary`
  - `vec_artifact_chunk`
  - `vec_rag_document`
  - `vec_rag_chunk`
- Updated context preparation to run MySQL and vector candidate preparation concurrently.
- Verification:
  - targeted tests passed: 23 tests, 0 failures
  - compile passed: `mvn -q -DskipTests compile`
  - `git diff --check` passed with line-ending warnings only
- Next step: commit this checkpoint, then continue materialization alignment and later Memory GC.

## 2026-05-25 PgVector Adapter

- Implemented real pgvector-backed vector memory repository:
  - `PgVectorMemoryRepository implements IVectorMemoryRepository`
  - uses existing `pgVectorJdbcTemplate`
  - uses shared `autoAgentEmbeddingModel`
  - writes/searches the dedicated `vec_*` collection tables
  - returns `VectorRecallHitVO` with source type, source id, score, metadata, and snippet
- Updated `AutoAgentRagVectorConfig`:
  - exposes `autoAgentEmbeddingModel` as a reusable bean
  - `PgVectorStore` and memory vector repository now share the same embedding configuration
- Runtime behavior:
  - if pgvector/embedding beans exist, Spring wires `PgVectorMemoryRepository`
  - otherwise `AutoAgentRuntimeConfig` falls back to `NoopVectorMemoryRepository`
- Verification:
  - compile passed: `mvn -q -DskipTests compile`
  - targeted tests passed: 5 tests, 0 failures
  - `git diff --check` passed with line-ending warning only

## 2026-05-25 Chunking Decision

- Confirmed chunking design:
  - no chunking for turn summaries, rolling summaries, long-term memories, user preferences, or artifact summaries
  - chunk long artifact bodies into `vec_artifact_chunk`
  - use chunk-specific vector source IDs, for example `artifact-123:chunk:001`
  - include parent `artifactId` and `chunkNo` in metadata
  - defer RAG chunk storage/retrieval details until RAG-specific design

## 2026-05-25 Memory GC Phase 1

- Upgraded the existing async turn summary processor into the first Memory GC task path.
- After each completed turn:
  - load turn user/assistant payloads
  - call `TURN_SUMMARY`
  - save summary payload
  - save `agent_turn_summary`
  - upsert summary into `vec_turn_summary`
  - save/update `agent_vector_index`
- Verification:
  - targeted tests passed: 7 tests, 0 failures
  - compile passed: `mvn -q -DskipTests compile`
  - `git diff --check` passed with line-ending warnings only

## 2026-05-25 Memory GC Replacement Design Note

- Added design constraint for future GC work:
  - summaries must be detailed index cards with intent/action/version/relation information
  - GC must manage replacement relationships for history, artifacts, long-term memories, and preferences
  - superseded records should be downgraded/marked, not blindly deleted, so comparison/history questions
    still work
  - vector indexes must stay aligned with source record state

## 2026-05-25 Recall To Materialization Phase 4

- Added artifact chunk candidate support in the recall-to-materialization chain:
  - `ArtifactCandidateVO` can now carry `matchedChunks`
  - `ArtifactChunkVO` carries `chunkId` and `sourceId`
  - vector `ARTIFACT_CHUNK` hits resolve parent `artifactId` from metadata
  - Planner selections can reference either artifact ID or chunk ID
  - `ContextSelectionValidator` accepts matched chunk IDs
  - `ContextSelectionMergePolicy` dedupes artifact chunk selections under the parent artifact key
  - `ContextMaterializer` can materialize `ARTIFACT_CHUNK`
  - `ArtifactPayloadLoader` injects matched chunks without loading full artifact body
- Verification:
  - targeted tests passed: 15 tests, 0 failures
  - compile passed: `mvn -q -DskipTests compile`
  - `git diff --check` passed with line-ending warnings only

## 2026-05-25 Planner Chunk Selection Policy

- Closed the remaining Planner-facing policy gap for vector artifact chunks:
  - ContextPlanner Java-owned prompt now explains `sourceChannel`, `sourceScore`, and `sourceReasons`
  - ContextPlanner Java-owned prompt now explains `artifactCandidates.matchedChunks`
  - Planner is instructed to select `ARTIFACT_CHUNK` by chunk id for local fragment tasks
  - Planner is instructed to select parent `ARTIFACT` for whole-artifact rewrite, compare, expand,
    publish, restructure, or update operations
  - MySQL runtime seed prompt is synced with the same stable behavior principles
- Verification:
  - targeted tests passed: 24 tests, 0 failures
  - compile passed: `mvn -q -DskipTests compile`
  - `git diff --check` passed with line-ending warnings only

## 2026-05-25 Memory Vector Indexing Service

- Added a reusable Memory GC indexing foundation:
  - `MemoryVectorIndexingService` upserts source records into vector collections
  - turn summaries index into `vec_turn_summary`
  - rolling conversation summaries index into `vec_conversation_summary`
  - long-term memories index into `vec_long_term_memory`
  - user preferences index into `vec_user_preference`
  - every successful vector upsert also saves/updates `agent_vector_index`
- Refactored `AsyncTurnSummaryProcessor` to reuse the shared indexing service instead of owning
  one-off vector upsert logic.
- Wired the indexing service and `MemoryManager` in runtime config so later Memory GC tasks can reuse
  the same path.
- Verification:
  - targeted tests passed: 5 tests, 0 failures
  - compile passed: `mvn -q -DskipTests compile`

## 2026-05-25 Memory GC Orchestrator Skeleton

- Added the first standalone Memory GC runtime skeleton:
  - `MemoryGcOrchestrator` is now the `TurnCompletionPublisher` entry point
  - `MemoryGcTaskDispatcher` dispatches memory tasks through the memory executor
  - `MemoryGcTaskWorker` defines task worker extension points
  - `TurnSummaryGcWorker` owns the turn-summary task behavior
  - `MemoryTaskTypeEnumVO` defines the planned GC task types
- Added `IMemoryTaskRepository.findByTaskId` and MyBatis query support so workers can resolve task
  payloads instead of relying on out-of-band method arguments.
- Runtime wiring now publishes completed turns to the GC orchestrator, not directly to the legacy async
  summary processor.
- Verification:
  - targeted tests passed: 6 tests, 0 failures
  - compile passed: `mvn -q -DskipTests compile`
  - `git diff --check` passed with line-ending warnings only

## 2026-05-25 Memory Extraction GC Worker

- Extended Memory GC from turn summary only to summary-driven memory extraction:
  - `TURN_SUMMARY` now schedules `LONG_TERM_MEMORY_EXTRACTION` when the summary marks
    `requiresLongTermExtraction=true`
  - added `MemoryGcFollowupScheduler` for worker-created follow-up tasks
  - added `LongTermMemoryGcWorker`
  - added `MEMORY_EXTRACTOR` node service, prompt builder, contract renderer, and output mapper
  - extracted `LONG_TERM_MEMORY` and `USER_PREFERENCE` records are saved through `MemoryManager`
  - `MemoryManager` continues to route saved memories through `MemoryVectorIndexingService`
- Runtime SQL seed now includes the `MEMORY_EXTRACTOR` model binding and DB prompt.
- Verification:
  - targeted tests passed: 18 tests, 0 failures
  - compile passed: `mvn -q -DskipTests compile`

## 2026-05-25 Conversation Rollup GC Worker

- Added rolling conversation summary capability:
  - `ConversationRollupGcWorker` consumes active turn summaries for a session
  - `CONVERSATION_ROLLUP` node compresses multiple turn summaries into one session-level summary
  - rollup output is saved through `MemoryManager.saveConversationSummary`
  - saved rollups are indexed through `MemoryVectorIndexingService` into `vec_conversation_summary`
  - `CONVERSATION_ROLLUP` prompt, contract renderer, output mapper, runtime config, and SQL seed are registered
- This is currently a worker capability that can be triggered by GC tasks; threshold and scheduled
  automatic triggering are still future work.
- Verification:
  - targeted tests passed: 12 tests, 0 failures

## 2026-05-25 Conversation Rollup Auto Trigger

- Added automatic conversation rollup scheduling from `TurnSummaryGcWorker`:
  - after a turn summary is saved, active summaries for the session are counted up to the rollup threshold
  - when the threshold is reached, GC creates a `CONVERSATION_ROLLUP` follow-up task
  - open `PENDING`/`RUNNING` rollup tasks for the same session prevent duplicate scheduling
- Added rollup compaction behavior:
  - `ConversationRollupGcWorker` marks consumed turn summaries as `ROLLED_UP` after saving the rolling summary
  - this keeps later rollup thresholds based on uncompressed active summaries
- Infrastructure changes:
  - `IMemoryTaskRepository.hasOpenTask`
  - `ITurnSummaryRepository.markSummariesRolledUp`
  - MyBatis mapper updates for open task counting and summary status updates
  - MySQL index `idx_agent_memory_task_session_type`
- Verification:
  - targeted tests passed: 8 tests, 0 failures

## 2026-05-25 Memory GC Retry Entry

- Added a reusable failed-task retry entry:
  - `MemoryGcRetryService.retryFailedTasks(maxAttempts, limit)` scans retryable failed tasks
  - eligible tasks are `FAILED` with `attempt_count < maxAttempts`
  - retry dispatch uses the existing `MemoryGcTaskDispatcher`, so workers keep their normal lifecycle
- Infrastructure changes:
  - `IMemoryTaskRepository.listRetryableFailedTasks`
  - MyBatis query for retryable failed tasks ordered by oldest update time
  - MySQL index `idx_agent_memory_task_retry`
  - Spring bean registration in `AutoAgentRuntimeConfig`
- Verification:
  - targeted GC tests passed: 9 tests, 0 failures
  - compile passed: `mvn -q -DskipTests compile`

## 2026-05-25 Memory GC Manual Retry HTTP Entry

- Added trigger-layer manual retry endpoint:
  - `POST /agent/memory-gc/retry-failed?maxAttempts=3&limit=20`
  - calls `MemoryGcRetryService.retryFailedTasks`
  - returns only operational counters, not internal payloads or worker details
- Added trigger-layer task visibility endpoint:
  - `GET /agent/memory-gc/tasks?status=FAILED&limit=50`
  - returns task type, status, run/session/turn ids, attempt count, failure code/message, and timing fields
  - backed by `MemoryGcTaskQueryService`, keeping controller out of repository details
- Verification:
  - targeted tests passed: 2 tests, 0 failures
  - compile passed: `mvn -q -DskipTests compile`

## 2026-05-26 Memory Redesign Implementation Start

- Added implementation plan:
  - `docs/superpowers/plans/2026-05-26-auto-agent-memory-system-redesign.md`
- Completed Checkpoint 1 foundation:
  - extended `agent_long_term_memory` with lifecycle/source fields
  - added `agent_session_task_summary` table
  - added `AgentSessionTaskSummaryEntity`
  - added `ISessionTaskSummaryRepository`
  - added session task summary DAO/PO/MyBatis mapper/repository adapter
  - updated long-term memory PO/mapper/repository mapping for new lifecycle fields
- Verification:
  - compile passed: `mvn -q -DskipTests compile`

## 2026-05-26 Session Task Summary GC

- Added `SESSION_TASK_SUMMARY` as a node component and Memory GC task type.
- Added session task summary node contract, prompt builder, node service, output mapper, static fallback prompt, and runtime SQL seed rows.
- Added `SessionTaskSummaryGcWorker`:
  - reads recent active turn summaries for the session
  - includes the previous active session task summary when present
  - calls the bounded `SESSION_TASK_SUMMARY` node
  - supersedes the old active session task summary and saves a new active version when `shouldUpdate=true`
- Updated `TurnSummaryGcWorker`:
  - threshold follow-up now schedules `SESSION_TASK_SUMMARY`
  - old `CONVERSATION_ROLLUP` worker/code remains available but is no longer scheduled from turn summary GC
- Verification:
  - targeted tests passed: `TurnSummaryGcWorkerTest`, `SessionTaskSummaryGcWorkerTest`
  - compile passed: `mvn -q -DskipTests compile`

## 2026-05-26 Strict Long-Term Memory Extraction

- Tightened MemoryExtractor Java prompt and SQL seed prompt:
  - only profile, preference, habit, project background, constraints, identity, and stable ongoing-work facts qualify
  - ordinary public-knowledge Q&A, generated content, one-off tasks, temporary instructions, and weak guesses must return `memories: []`
- Updated `LongTermMemoryGcWorker` persistence:
  - extracted memories now carry `status=ACTIVE`
  - extracted memories carry `sourceRunId`, `sourceTurnId`, and `lastSeenAt`
  - optional extracted `content` is stored as a payload and referenced by `contentRef`
- Verification:
  - targeted test passed: `LongTermMemoryGcWorkerTest`

## 2026-05-26 Context Preparation Memory Redesign Alignment

- Added `SessionTaskSummaryViewVO` and injected active session task summary into:
  - `ContextCandidateBundleVO` for ContextPlanner input
  - `ConversationViewVO` for MainAgent state view
- Updated MySQL context preparation:
  - active session task summary is loaded by `ISessionTaskSummaryRepository`
  - latest 6 full turns and older 6 turn summaries remain in the turn window path
  - artifact candidates are no longer produced by the new context preselector path
- Updated vector recall:
  - recall collections are limited to turn summaries, long-term memories, and user preferences
  - artifact hits are ignored by the memory recall path
- Updated ContextPlanner Java prompt and SQL seed prompt:
  - session task summary is described as default task-state context
  - artifact candidate guidance is marked deprecated/removed from selection policy
- Verification:
  - targeted tests passed: `ContextCandidatePreselectorTest`, `ContextMaterializationTest`, `VectorContextRecallPreselectorTest`, `PromptAssemblerTest`, `ContextPreparationServiceTest`
  - compile passed: `mvn -q -DskipTests compile`

## 2026-05-26 Memory Governance GC Skeleton

- Added `MEMORY_GOVERNANCE` component and Memory GC task type.
- Added governance node service, prompt builder, output contract, output mapper, runtime config, and SQL seed rows.
- Added governance VO contract:
  - input: active session memories
  - output: conservative actions for `KEEP`, `DISABLE`, `SUPERSEDE`, and `NOOP`
- Added `MemoryGovernanceGcWorker`:
  - loads active memories by session
  - ignores unknown memory ids from LLM output
  - updates MySQL memory lifecycle before disabling vector indexes
  - records governance memory events
- Infrastructure:
  - `IMemoryRepository.listActiveMemoriesBySession`
  - `IMemoryRepository.updateMemoryLifecycle`
  - MyBatis DAO/mapper support for active session memory scan and lifecycle update
- Verification:
  - targeted test passed: `MemoryGovernanceGcWorkerTest`
  - compile passed: `mvn -q -DskipTests compile`

## 2026-05-26 Memory GC Scheduling Policy

- Updated automatic Memory GC follow-up scheduling:
  - every 5 active turn summaries triggers `SESSION_TASK_SUMMARY`
  - every 15 active turn summaries triggers `MEMORY_GOVERNANCE`
  - open same-session follow-up tasks are still deduped by `MemoryGcFollowupScheduler`
- Updated `SessionTaskSummaryGcWorker` summary selection window:
  - limit formula is `min(max(30, ceil(activeTurnSummaryCount * 0.7)), activeTurnSummaryCount)`
  - Spring runtime config now uses 30 as the minimum summary window
- Added active turn summary counting through repository/DAO/MyBatis.
- Verification:
  - targeted tests passed: `TurnSummaryGcWorkerTest`, `SessionTaskSummaryGcWorkerTest`
  - compile passed: `mvn -q -DskipTests compile`
