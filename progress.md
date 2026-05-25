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
