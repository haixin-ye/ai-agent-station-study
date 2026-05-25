# AutoAgent Memory Recall And GC Plan

## Goal

Build the memory recall and maintenance system for AutoAgent without changing the agreed Runtime
boundary: MySQL and vector storage are parallel candidate recall sources. Both produce context
candidates for ContextPlanner. Nothing from recall is injected directly into MainNode.

## Current Completed State

- AutoAgent main-loop Runtime is active.
- USER_ASK backend resume flow has been fixed enough for current tests.
- Final response persistence saves completed turns. This currently belongs to the MySQL memory
  persistence path, but will later be owned by Memory GC orchestration.
- `agent_turn`, `agent_turn_summary`, and `agent_memory_task` phase-one memory tables exist.
- `TURN_SUMMARY` node and async turn-summary processing exist. This is a transitional implementation;
  the long-term owner should be Memory GC.
- Context loading is split into fixed recent context and planning candidates. The fixed recent context
  and MySQL summary recall are part of the MySQL candidate preparation path.
- ContextPlanner is not supposed to decide whether the latest 6 full turns exist.
- ContextSelectionMergePolicy keeps the strongest context level for the same source.
- Recent full turns suppress duplicate summaries from the same turn.

## Design Decisions

- MySQL is the source of truth for business data, status, audit, and precise loading.
- Vector storage is a semantic index, not the source of truth.
- MySQL recall and vector recall run as parallel candidate sources.
- MySQL recall and vector recall must execute concurrently, not sequentially. Slow or failed vector
  recall must not block deterministic MySQL context from reaching ContextPlanner.
- Vector hits return source IDs and metadata, then the system resolves those IDs through MySQL or RAG storage before ContextPlanner sees them.
- ContextPlanner receives unified candidates and decides what should be injected.
- Materialization loads final full text, summaries, snippets, or chunks after Planner selection.
- RAG chunks are not forced into the same dedupe rules as MySQL-backed memory. RAG-specific rerank and dedupe will be handled later.

## Phase 1: Align Memory Architecture And Plan

Status: complete

- Record the current completed system state.
- Record the vector memory design constraints.
- Confirm next implementation scope before editing code.
- Reclassify existing recent full turns and summary recall as MySQL candidate preparation.
- Reclassify existing async summary generation as transitional behavior that will move under Memory GC.

## Phase 2: MySQL And Vector Table/Infrastructure Foundation

Status: complete

- Review and complete MySQL memory table design for turns, turn summaries, rolling summaries,
  long-term memories, user preferences, artifacts, artifact summaries/chunks, memory tasks, and vector
  index sync state.
- Add vector collection/source enums under domain model.
- Add value objects for vector index records, recall queries, recall hits, filters, and resolved
  candidates.
- Add domain repository ports for vector memory indexing/recall and vector index sync state.
- Keep all provider-specific details out of domain.

## Phase 3: MySQL And Vector Candidate Preparers

Status: complete

- Build/rename the MySQL candidate preparer boundary around existing recent full turn recall,
  historical summary recall, active long-term memory/preferences, and recent artifacts.
- Add VectorContextRecallPreselector.
- Run MySQL candidate preparation and vector candidate preparation in parallel with bounded executors,
  per-branch timeout, and graceful degradation.
- Query vector storage with user input, session/user filters, and collection filters.
- Resolve vector hit source IDs back to MySQL/RAG candidate content.
- Convert both MySQL and vector results into one candidate shape for ContextPlanner.

## Phase 4: Candidate Selection And MainNode Injection

Status: in_progress

- Merge MySQL rule candidates and vector semantic candidates.
- Deduplicate by source key and keep strongest context level.
- Preserve source channel, score, reason, and timestamp for Planner/debug visibility.
- Ensure ContextPlanner selects candidates and Materializer injects final full text, summaries, snippets,
  or chunks into MainNode.
- Current implementation already joins MySQL/vector candidate sources before ContextPlanner. Remaining
  work is deeper materialization alignment for future chunk/rolling-summary/RAG sources.

## Phase 5: Complete Memory GC Machine

Status: pending

- Move turn persistence and turn summary generation behind Memory GC orchestration where appropriate.
- After each completed turn, persist raw turn and generate/store turn summary.
- On schedule or by threshold, generate rolling conversation summaries.
- After each completed turn, extract long-term memory and user preference candidates when present.
- Periodically merge, supersede, disable, or downgrade stale memories.
- Generate artifact summaries/chunks and index them where needed.
- Upsert MySQL source records and vector indexes.
- Record task status, retries, and failure details in `agent_memory_task`.

## Phase 6: Verification And Checkpoint

Status: in_progress

- Add focused tests for table/repository boundaries, MySQL candidate preparation, vector candidate
  preparation, candidate merge, materialization, and GC task orchestration.
- Run targeted app tests.
- Run compile.
- Run diff check.
- Commit focused Git checkpoints per phase.

## Phase 7: Replan

Status: pending

- Review what the memory recall and GC foundation actually enables.
- Decide whether the next phase is real vector DB adapter, RAG indexing, MCP tool memory, or memory
  quality evaluation.

## Errors Encountered

| Error | Attempt | Resolution |
| --- | --- | --- |
| PowerShell sandbox intermittently failed with `CreateProcessWithLogonW failed: 1326` | Read planning and repo files | Continue with available git status output and persistent plan files; avoid relying on repeated shell reads until tool process recovers. |
| Test compile failed after adding `ITurnSummaryRepository.findSummaryById` | Targeted Maven test run | Added the new method to `AsyncTurnSummaryProcessorTest.FakeRepositories`. |
