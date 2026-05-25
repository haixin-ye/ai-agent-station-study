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
