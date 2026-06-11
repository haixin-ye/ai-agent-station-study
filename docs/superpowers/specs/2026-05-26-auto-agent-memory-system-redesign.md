# AutoAgent Memory System Redesign Spec

## Goal

Rebuild AutoAgent memory around a simpler and more maintainable model:
MySQL is the source of truth, pgvector is the semantic index, Memory GC maintains derived memory, and artifact-based context is logically deprecated.

## Scope

This spec replaces the previous rolling conversation summary direction.
The new memory system contains exactly four chat-memory categories:

1. Turn full text
2. Turn summary
3. Long-term memory
4. Session task summary

Artifact objects may remain in code temporarily for compatibility, but the new memory and context flow must not depend on artifact candidates, artifact summaries, or artifact chunks.

## Core Storage Responsibilities

### MySQL

MySQL stores durable source records, lifecycle state, and debugging/audit data.

- Completed turn full text and references.
- Per-turn LLM summaries.
- Long-term memory source copies and lifecycle status.
- Session task summaries.
- Memory GC task status, retry state, and failure details.
- Memory events for create, update, disable, supersede, and governance actions.

### pgvector

pgvector stores semantic indexes only.

- Turn summary vectors with source metadata such as `sessionId`, `turnId`, `turnNo`, and `summaryId`.
- Long-term memory vectors with source metadata such as `memoryId`, `userId`, `sessionId`, and `memoryType`.

Session task summaries do not enter pgvector because they are deterministic session state loaded by `sessionId`.
Turn full text does not enter pgvector directly in this phase.

## Memory Categories

### 1. Turn Full Text

Each completed user-agent turn is saved in MySQL.

Responsibility:

- Preserve the exact user request and final agent answer.
- Provide the fixed recent context window.
- Allow debugging and historical reconstruction even if GC fails.

Lifecycle:

- Saved synchronously by Runtime/final delivery after a completed answer.
- Not owned by asynchronous GC for durability.
- Used by context preparation as the latest 6 completed turns.

### 2. Turn Summary

Each completed turn gets a Memory GC-generated LLM summary.

Responsibility:

- Provide compact historical context for older turns.
- Support semantic recall over previous conversation.
- Keep enough identifiers to trace back to the original turn.

Storage:

- MySQL: full summary payload and metadata.
- pgvector: summary text plus source metadata.

Lifecycle:

- Generated asynchronously after turn completion.
- Indexed into `vec_turn_summary`.
- Remains independently searchable; it is not overwritten by any rolling summary.

### 3. Long-Term Memory

Long-term memory means stable user profile, user habits, durable preferences, and long-running project background.

Responsibility:

- Capture durable user-specific or project-specific facts that are useful across future turns.
- Avoid recording ordinary Q&A, temporary requests, one-off tasks, or model inferences not confirmed by the user.

Storage:

- MySQL stores the authoritative copy and lifecycle state.
- pgvector stores the semantic index.

Extraction rules:

- The `MEMORY_EXTRACTOR` prompt must be strict.
- It may extract stable identity, role, persistent style preference, development habit, technical preference, explicit "remember this" facts, and long-running project context.
- It must not extract normal question content, temporary instructions, one-off answer constraints, generic knowledge, uncertain guesses, or the model's own unconfirmed inference.

Lifecycle:

- New extracted memories are stored normally.
- Insert-time merge/supersede is intentionally avoided.
- Periodic LLM-driven Memory Governance handles merge, supersede, correction, and disable.
- Recall uses only active memory by default.
- Conflict ranking prefers newer active memory when candidates are otherwise comparable.

### 4. Session Task Summary

Session task summary replaces rolling conversation summary.

Responsibility:

- Describe what this session is mainly doing.
- Track current/latest task state.
- Preserve key decisions and open questions for ContextPlanner.

Storage:

- MySQL only.
- Not indexed in pgvector.

Inputs:

- A bounded but large set of recent turn summaries for the session.
- The current active session task summary, if present.

Output shape:

- Main tasks in this session.
- Current/latest task.
- Important decisions.
- Latest progress.
- Open questions.
- Obsolete or completed tasks when useful.

Lifecycle:

- Created or updated periodically by Memory GC, for example every 5 completed turns.
- Keeps version history in MySQL.
- Context preparation loads the latest active summary by `sessionId`.

## Context Preparation And Injection

Context preparation runs once per new user request/run.
It does not rerun inside the same run after MainNode routes to RAG, MCP, tool calls, ASK_USER, or other deterministic modules.

### MySQL Recall Branch

For the current session:

- Latest 6 completed turn full texts.
- Turn summaries for turns 7-12.
- Latest active session task summary, if present.

For long-term memory:

- MySQL may provide active memory candidates if needed by deterministic filters.
- Long-term memory should primarily be semantically recalled by vector, but MySQL remains the source of truth for resolving and validating memory records.

### Vector Recall Branch

Using the current user message:

- Search turn summary vectors in the current session scope.
- Search long-term memory vectors by user scope, with current session/project scope as a preferred filter when available.

Vector hits must resolve back to MySQL source records before Planner sees them.

### Candidate Merge And Deduplication

Both recall branches produce unified context candidates.

Deduplication rules:

- For the same `turnId`, full text beats summary.
- Session task summary is default session state and should be injected unless unavailable.
- Recalled active long-term memories are injected as full memory content, subject to budget and relevance.
- Artifact candidates are not part of the new flow.

### ContextPlanner

ContextPlanner receives:

- User input.
- Recent full turns.
- Older turn summary candidates.
- Vector-resolved turn summary candidates.
- Vector-resolved long-term memory candidates.
- Latest session task summary.
- Available capabilities and token budget.

ContextPlanner selects what should be injected into MainNode, except for default session task summary and high-confidence long-term memory candidates, which may be treated as high-priority context.

### ContextMaterializer

The materializer turns the selected candidate list into `MainAgentStateView`.

MainNode receives the state view and continues the run.
Subsequent routing within the same run does not trigger another full context planning pass.

## Memory GC Responsibilities

Memory GC runs asynchronously and must not block the user-facing answer path.

### Every Completed Turn

After Runtime/final delivery saves the completed turn:

1. Create a `TURN_SUMMARY` task.
2. Generate the turn summary with LLM.
3. Save summary to MySQL.
4. Upsert summary into pgvector.
5. Strictly extract long-term memories with LLM when the summary indicates potential durable memory.
6. Save extracted long-term memory to MySQL and pgvector.

### Every N Completed Turns

For example every 5 completed turns in the session:

1. Create or update the session task summary.
2. Run LLM-driven Memory Governance for long-term memory.

### Session Task Summary Update

The worker reads session turn summaries in chronological order, bounded by a configured max count.
If a previous session task summary exists, it is included as the prior state.

The LLM decides whether the session task summary needs an update.
If it does, the worker writes a new active version and marks the previous version superseded.

### LLM-Driven Memory Governance

Memory Governance is responsible for long-term memory maintenance.

Inputs:

- Active long-term memories for the user/session/project scope.
- Recently created long-term memories.
- Recent turn summaries.
- Latest session task summary.

Allowed decisions:

- `KEEP`: keep memory active.
- `MERGE`: combine duplicate or near-duplicate memories.
- `SUPERSEDE`: mark an older memory replaced by a newer one.
- `DISABLE`: disable incorrect, temporary, low-quality, or mistakenly extracted memory.
- `UPDATE`: update summary/content/score while keeping the memory identity.
- `NOOP`: no change.

Java execution rules:

- LLM outputs structured JSON only.
- Java validates every referenced memory id.
- Java never physically deletes memory in this phase.
- Java updates MySQL lifecycle state first.
- Java then synchronizes pgvector: update active records and disable inactive records.
- Java writes `agent_memory_event` for every governance action.

## Data Model Changes

### Long-Term Memory

Extend `agent_long_term_memory` with lifecycle and source fields:

- `status`
- `source_run_id`
- `source_turn_id`
- `last_seen_at`
- `disabled_at`
- `superseded_by`
- `metadata_json`

The default status is `ACTIVE`.

### Session Task Summary

Add `agent_session_task_summary`:

- `summary_id`
- `session_id`
- `user_id`
- `summary_ref`
- `version_no`
- `source_turn_count`
- `source_latest_turn_id`
- `source_latest_turn_no`
- `status`
- `created_at`
- `updated_at`

Only the latest `ACTIVE` version is injected into planning context.

### Deprecated Conversation Rollup

The old `agent_conversation_summary`, `CONVERSATION_ROLLUP` worker, and related node/prompt are deprecated.
They should not be scheduled by Memory GC after this redesign.
They may be removed in a later cleanup after the new session task summary flow is stable.

## Artifact Deprecation

Artifact is logically deprecated for the new memory architecture.

Rules:

- New context preparation should not rely on artifact candidates.
- ContextPlanner prompt should stop encouraging artifact selection.
- Memory GC should not implement artifact summary or artifact chunk indexing.
- Frontend and API can return empty artifact lists for compatibility.
- Existing artifact code can remain temporarily to avoid broad breakage.

Physical removal of artifact tables, DTOs, controllers, tool argument materialization support, and state view fields is a separate cleanup project.

## Testing Requirements

Minimum tests:

- Turn full text remains available even when GC fails.
- Turn summary is saved to MySQL and pgvector after a completed turn.
- Long-term memory extraction rejects ordinary Q&A and one-off tasks through prompt/contract tests.
- Context preparation merges MySQL and vector candidates and dedupes same `turnId` by full text over summary.
- Session task summary is generated every configured turn threshold.
- Session task summary is injected into planning context.
- Memory Governance validates LLM decisions and refuses unknown memory ids.
- Memory Governance disables/supersedes MySQL memory and synchronizes vector index state.
- Artifact candidates are no longer used in the new context preparation path.

## Migration Strategy

1. Add new schema fields and `agent_session_task_summary`.
2. Keep existing conversation rollup code but stop scheduling it.
3. Add session task summary node, contract, worker, and repository.
4. Tighten MemoryExtractor prompt and contract behavior.
5. Add Memory Governance node and worker.
6. Update context preparation and planner prompt.
7. Mark artifact flow deprecated in prompts and new context code.
8. Verify with targeted tests and compile.

## Open Decisions

- Exact threshold for session task summary and Memory Governance defaults to 5 completed turns, configurable later.
- Project-level scope for long-term memory can be added when project identity exists in runtime state.
- Physical artifact removal is deferred.
