# AutoAgent Vector Memory Findings

## Current Architecture Understanding

- Runtime owns lifecycle, routing, persistence, SSE events, pending input, final delivery, and recovery behavior.
- MainAgentNode emits structured actions. It should not receive raw recall results.
- ContextPlanner judges candidate relevance. It should receive candidate content and metadata, not final injected context.
- Materialization turns selected candidates into final MainNode context.

## Memory System Understanding

- `agent_turn` stores full completed question/answer turns.
- `agent_turn_summary` stores per-turn summaries generated asynchronously.
- `agent_memory_task` is the base task table for future memory work.
- Recent full turns are fixed context. Older summaries and recalled candidates are planning candidates.
- The latest 6 full turns are default materialization inputs, not something Planner needs to rediscover.
- Existing recent full turns and summary recall should be treated as the MySQL candidate preparation
  path.
- Existing async turn summary generation is transitional. Long term it should become part of the Memory
  GC machine.

## MySQL Recall Role

MySQL recall is deterministic and rule based:

- recent full turns by session/user/time
- older summaries by session/user/time
- active long-term memories by user/status
- active user preferences by user/status
- recent artifacts by session/user/time
- later: keyword/type hints from user input can adjust scores and add candidate pools

MySQL recall can use fixed base scores because it has no semantic similarity score.

## Vector Recall Role

Vector recall is semantic:

- convert user input to embedding
- search selected collections
- return source type, source ID, score, and metadata
- resolve the source ID through MySQL or RAG storage
- produce the same candidate shape as MySQL recall

Vector storage is an index, not the business source of truth.

## Candidate Flow

The correct flow is:

```text
User input
 -> MySQL rule recall
 -> Vector semantic recall and source resolution
 -> unified candidate list
 -> candidate merge/dedupe/score normalization
 -> ContextPlanner selection
 -> Materializer final context loading
 -> MainNode
```

MySQL recall and vector recall are parallel branches. They should run concurrently with bounded
threads, timeouts, and branch-level fallback. MySQL deterministic recall should still proceed if vector
recall is unavailable or slow.

## Updated Development Sequence

1. Complete MySQL and vector table/infrastructure foundations.
2. Complete two parallel candidate preparers: MySQL candidate preparer and vector candidate preparer.
   These two preparers must execute concurrently, then join into one candidate merge step.
3. Complete candidate merge, ContextPlanner selection, and Materializer injection into MainNode.
4. Build the full Memory GC machine:
   - persist raw completed turns
   - generate turn summaries after each completed turn
   - generate rolling summaries by schedule or threshold
   - extract long-term memories and user preferences after turns
   - periodically merge, supersede, disable, or downgrade stale memories
   - create artifact summaries/chunks
   - synchronize MySQL source data with vector indexes

## Collection Intent

- `vec_turn_summary`: semantic search over individual turn summaries.
- `vec_conversation_summary`: semantic search over rolling multi-turn summaries.
- `vec_long_term_memory`: semantic search over stable user/project facts.
- `vec_user_preference`: semantic search over response and workflow preferences.
- `vec_artifact_summary`: semantic search over generated artifacts.
- `vec_artifact_chunk`: semantic search inside large generated artifacts.
- `vec_rag_document`: semantic search over external document summaries.
- `vec_rag_chunk`: semantic search over external document chunks.

## Chunking Decisions

- Turn summaries, rolling summaries, long-term memories, user preferences, and artifact summaries are
  already compact semantic units. They should not be chunked.
- Artifact bodies should be chunked only when the body is long enough to justify section-level recall.
- Artifact chunk vector `sourceId` must be chunk-specific, for example `artifact-123:chunk:001`.
- Artifact chunk metadata must include the parent `artifactId` and `chunkNo`, so materialization can
  either inject the chunk or load the full artifact from MySQL when the user asks to edit the whole
  artifact.
- RAG chunking is intentionally deferred. RAG may use a different storage and retrieval design later.

## Implementation Boundary

- Domain defines ports, VOs, policies, and orchestration.
- Infrastructure implements MySQL and vector adapters.
- App wires concrete beans.
- Tests should focus on behavior at the domain/app boundary.
