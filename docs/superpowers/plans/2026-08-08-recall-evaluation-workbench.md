# RAG And Memory Recall Evaluation Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a dev-only workbench that manages isolated RAG/memory evaluation datasets, runs parameterized production-path retrieval with optional Context Planner selection, and reports quantitative and A/B metrics without entering MainNode or Runtime.

**Architecture:** Persist evaluation datasets, corpus, cases, runs, results, and hits in MySQL. Reuse existing RAG/memory source repositories and pgvector collections, adding exact metadata filtering and parameterized detailed recall. Expose a dev controller and an independent modular dashboard.

**Tech Stack:** Java 17, Spring Boot 3.4, MyBatis, MySQL, PostgreSQL/pgvector, Spring AI EmbeddingModel, Fastjson, vanilla HTML/CSS/JavaScript, JUnit 4.

---

### Task 1: Evaluation persistence contract and schema

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/evaluation/RecallEvaluationDatasetEntity.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/evaluation/RecallEvaluationCorpusItemEntity.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/evaluation/RecallEvaluationCaseEntity.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/evaluation/RecallEvaluationRunEntity.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/evaluation/RecallEvaluationCaseResultEntity.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/evaluation/RecallEvaluationHitEntity.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/adapter/repository/IRecallEvaluationRepository.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/po/RecallEvaluation*.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/dao/IRecallEvaluation*Dao.java`
- Create: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/repository/RecallEvaluationRepository.java`
- Create: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/recall_evaluation_*_mapper.xml`
- Modify: `docs/dev-ops/mysql/init/auto-agent-main-loop-harness.sql`
- Create: `docs/dev-ops/mysql/patches/auto-agent-recall-evaluation-workbench.sql`

- [ ] Define six focused domain entities with IDs, lifecycle status, counts, source refs, config/metrics JSON, result metrics, and timestamps exactly as the design specifies.
- [ ] Define repository operations for dataset CRUD, paged corpus/case listing, run lifecycle, case result/hit persistence, cancellation checks, and A/B source loading.
- [ ] Add MySQL schema with dataset-local unique keys `(dataset_id, external_id)`, run/result indexes, and foreign-key-free soft lifecycle consistent with the existing harness SQL.
- [ ] Implement PO/DAO/MyBatis mappings and a repository adapter that performs no metric or lifecycle decisions.
- [ ] Run `mvn -q -pl ai-agent-station-study-infrastructure -am -DskipTests compile` and expect exit code 0.
- [ ] Commit with `git commit -m "evaluation: add recall workbench persistence"`.

### Task 2: Exact vector dataset filtering and detailed recall options

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/evaluation/RecallExecutionOptionsVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/evaluation/DetailedRecallResultVO.java`
- Modify: `ai-agent-station-study-infrastructure/src/main/java/yhx/com/infrastructure/adapter/repository/PgVectorMemoryRepository.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/memory/VectorContextRecallPreselector.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/rag/RagContextRecallPreselector.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/evaluation/RecallEvaluationFlowTest.java`

- [ ] Add one focused test method proving an evaluation query passes `topK`, `minScore`, selected collections, lexical mode, and `evalDatasetId` metadata to the repository while the legacy `recall(command)` path keeps its existing defaults.
- [ ] Run the test and expect it to fail because the detailed recall entry points do not exist.
- [ ] Implement `metadata @> ?::jsonb` filtering in both vector and lexical SQL paths using `VectorRecallFilterVO.metadataFilters`; omit the clause when the map is empty.
- [ ] Add parameterized detailed recall methods returning raw vector/lexical hits, resolved candidates, elapsed time, and diagnostics; make legacy methods delegate to the new defaults.
- [ ] Preserve collection enum whitelisting and parameter binding; never interpolate metadata keys or values into SQL.
- [ ] Run `mvn -q -pl ai-agent-station-study-app -am "-Dtest=RecallEvaluationFlowTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` and expect pass.
- [ ] Commit with `git commit -m "evaluation: add parameterized detailed recall"`.

### Task 3: Dataset-scoped RAG and memory ingestion

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/evaluation/RecallCorpusImportItemVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/evaluation/RecallCorpusImportResultVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/evaluation/RecallEvaluationIngestionService.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/rag/RagAssetIngestionService.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/rag/RagVectorIndexingService.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/memory/MemoryVectorIndexingService.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/memory/LongTermMemoryService.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/adapter/repository/IRagAssetRepository.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/adapter/repository/IMemoryRepository.java`

- [ ] Extend the existing flow test with one RAG item and one memory item, asserting synthetic eval user/session scope, `evalDatasetId` metadata, source refs, and item-level partial failure.
- [ ] Add overloads to RAG and memory indexing that merge caller metadata with production metadata while leaving existing call sites unchanged.
- [ ] Implement real `LongTermMemoryService` ingestion: save content payload, save active memory row, then index it; support `LONG_TERM_MEMORY` and `USER_PREFERENCE` only.
- [ ] Implement `RecallEvaluationIngestionService` to validate external IDs, mark corpus lifecycle, invoke production ingestion, register document/chunk/memory source IDs, and persist structured failures.
- [ ] Add repository lifecycle methods required for disable/reindex/cleanup without exposing direct DAO access to domain services.
- [ ] Run the focused flow test and expect pass.
- [ ] Commit with `git commit -m "evaluation: add isolated corpus ingestion"`.

### Task 4: Cases, metrics, batch runner, and Context Planner cutoff

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/evaluation/RecallExpectedItemVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/evaluation/RecallEvaluationRunConfigVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/valobj/evaluation/RecallEvaluationMetricsVO.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/evaluation/RecallMetricsCalculator.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/evaluation/RecallEvaluationRunner.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/evaluation/RecallEvaluationComparisonService.java`
- Test: `ai-agent-station-study-app/src/test/java/yhx/com/test/domain/agent/evaluation/RecallMetricsCalculatorTest.java`

- [ ] Write one metrics test covering graded expected labels, parent-document matching, Precision@K, Recall@K, MRR, nDCG, MAP, no-hit, and percentile latency.
- [ ] Run the metrics test and expect failure because the calculator does not exist.
- [ ] Implement deterministic metric formulas with stable zero-denominator behavior and no model dependencies.
- [ ] Implement the runner state machine: create/run/cancel, case iteration, source-scope routing, candidate merge, optional single Context Planner call, per-case persistence, failure isolation, progress updates, and final aggregation.
- [ ] Ensure `plannerEnabled=false` never calls `ContextPlannerNodeService`; ensure enabled mode stops immediately after planner output and never references MainNode or Runtime services.
- [ ] Implement A/B comparison using persisted run metrics and per-case results, including parameter delta and only-left/only-right hits.
- [ ] Extend `RecallEvaluationFlowTest` to prove one failed case does not abort the next case and Planner can be disabled.
- [ ] Run both focused test classes and expect pass.
- [ ] Commit with `git commit -m "evaluation: run recall benchmarks and metrics"`.

### Task 5: Dev API and asynchronous assembly

**Files:**
- Create: `ai-agent-station-study-api/src/main/java/yhx/com/api/dto/agent/evaluation/*.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/evaluation/RecallEvaluationFacade.java`
- Create: `ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/RecallEvaluationController.java`
- Create: `ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/support/RecallEvaluationApiMapper.java`
- Create: `ai-agent-station-study-app/src/main/java/yhx/com/config/RecallEvaluationConfig.java`
- Modify: `ai-agent-station-study-app/src/main/resources/application-dev.yml`

- [ ] Define DTOs for dataset CRUD, paged corpus/cases, batch import, run config, run detail, results, cancellation, and comparison; use explicit nested fields rather than generic maps for primary UI data.
- [ ] Implement Facade methods that own validation and transaction-sized orchestration while Controller only maps HTTP DTOs and `Response<T>`.
- [ ] Add dev-profile Controller under `/api/v1/dev/recall-evaluations` with multipart RAG upload and structured batch endpoints.
- [ ] Add a bounded executor with configurable core/max/queue values and rejection mapped to a run-level failure code.
- [ ] Add upload/batch limits to `application-dev.yml` and validate before allocating full payload collections.
- [ ] Run `mvn -q -pl ai-agent-station-study-app -am -DskipTests compile` and expect exit code 0.
- [ ] Commit with `git commit -m "trigger: expose recall evaluation dev api"`.

### Task 6: Independent modular developer UI

**Files:**
- Create: `docs/dev-ops/nginx/html/recall_evaluation.html`
- Create: `docs/dev-ops/nginx/html/recall_evaluation.css`
- Create: `docs/dev-ops/nginx/html/recall_evaluation.js`
- Create: `docs/dev-ops/nginx/html/recall_evaluation_logic.js`
- Create: `docs/dev-ops/nginx/html/recall_evaluation_logic.test.js`
- Modify: `docs/dev-ops/nginx/html/index.html`

- [ ] Implement pure logic helpers for JSONL/CSV parsing, run metric projection, score/rank series, and A/B deltas.
- [ ] Write a Node test covering quoted CSV, invalid JSONL item reporting, zero-valued metrics, and positive/negative A/B deltas; run it and expect failure before helpers exist.
- [ ] Build a standalone dark glass dashboard with dataset rail, summary header, four workspaces, card-based corpus/case management, structured experiment form, progress state, KPI/trend/result views, and comparison mode.
- [ ] Make corpus files and case files importable via drag/drop or picker; show item-level parse/ingest errors before and after submission.
- [ ] Render case details with expected and actual hits aligned in the same rows, Planner selection badges on hit cards, and Raw JSON behind an explicit disclosure.
- [ ] Use DOM-safe text rendering for imported content and preserve user selection/filter state during polling.
- [ ] Add a dev-tools launcher from `index.html` without coupling the page to chat Runtime state.
- [ ] Run `node docs/dev-ops/nginx/html/recall_evaluation_logic.test.js` and expect pass.
- [ ] Commit with `git commit -m "frontend: add recall evaluation workbench"`.

### Task 7: Minimal verification and real dev acceptance

**Files:**
- Modify only files required by defects discovered during verification.

- [ ] Run focused Java tests:
  `mvn -q -pl ai-agent-station-study-app -am "-Dtest=RecallMetricsCalculatorTest,RecallEvaluationFlowTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`.
- [ ] Run frontend logic test:
  `node docs/dev-ops/nginx/html/recall_evaluation_logic.test.js`.
- [ ] Run app compile:
  `mvn -q -pl ai-agent-station-study-app -am -DskipTests compile`.
- [ ] Apply the MySQL patch and confirm current pgvector tables contain the metadata column already used by the repository.
- [ ] Start the dev app with existing Docker dependencies and create one dataset containing one RAG document, one long-term memory, one preference, and three labeled cases.
- [ ] Run Retrieval-only and Planner-enabled experiments; prove the former creates zero Planner invocations and both stay inside the target dataset.
- [ ] Open the UI and verify dataset switching, corpus/case drill-down, run progress, per-hit scores, aggregate metrics, and A/B comparison.
- [ ] Run `git diff --check` and inspect `git status --short` for unrelated files.
- [ ] Commit final verification fixes with `git commit -m "evaluation: verify recall workbench flow"`.

### Task 8: Completion audit

- [ ] Map every acceptance criterion in `docs/superpowers/specs/2026-08-08-recall-evaluation-workbench-design.md` to code, API output, test output, or browser evidence.
- [ ] Confirm no code path invokes MainNode, Runtime action handlers, tools, sub-agents, or final delivery.
- [ ] Confirm all new source and resource files are UTF-8 and no local logs, `.m2`, runtime artifacts, or unrelated parent-worktree files are tracked.
- [ ] Summarize delivered capabilities, verification evidence, known constraints, branch name, and exact worktree path.
