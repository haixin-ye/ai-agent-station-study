# AutoAgent Memory Lifecycle Design

Date: 2026-05-20

## 1. Goal

Build a maintainable memory system for AutoAgent that supports long conversations, artifact reference resolution, user preference memory, semantic retrieval, and memory garbage collection without turning the runtime into a black box.

The design uses MySQL as the source of truth and vector storage as semantic indexes. Vector storage must never be the only copy of important memory, artifact, or conversation data.

## 2. Core Principles

1. MySQL owns truth, lifecycle, status, versioning, audit, permissions, and recovery.
2. Vector storage owns semantic recall only.
3. Recent conversational context is loaded deterministically from MySQL, not selected by vector search.
4. Reference phrases such as "上次那篇文章", "刚才那个方案", "第二版" are resolved primarily by structured session state, recency, artifact metadata, and ContextPlanner judgment, not by pure vector similarity.
5. MainAgentNode receives clean, bound context. It should know why an artifact, memory, or summary was selected.
6. Memory extraction, rolling summary, indexing, merge, and GC run outside the user-facing critical path.

## 3. Memory Layers

### 3.1 Short-Term Conversation Window

Purpose: give MainAgentNode immediate continuity.

Source: MySQL `agent_turn` + `agent_message`.

Default injection:

- Latest 6 complete turns as full user/assistant text.
- Previous 6 turns as turn summaries.

This context is always loaded for MainAgentNode and does not depend on ContextPlanner selection.

### 3.2 Turn Summary

Purpose: compact each completed user question and agent answer.

MySQL source table: `agent_turn_summary`.

Vector index: `vec_turn_summary`.

Usage:

- Recall older discussion by semantic topic.
- Help resolve references when recent full turns no longer cover the target.
- Provide compact context to ContextPlanner and MainAgentNode.

### 3.3 Conversation Rolling Summary

Purpose: summarize a continuous range of turns.

MySQL source table: `agent_conversation_summary`.

Vector index: `vec_conversation_summary`.

Usage:

- Long-session compression.
- Recover project/topic continuity across dozens of turns.
- Feed ContextPlanner when the user refers to older conversation sections.

### 3.4 Long-Term Memory

Purpose: store durable user/project facts and preferences.

MySQL source table: `agent_memory_item`.

Vector index: `vec_long_term_memory`.

Examples:

- User prefers detailed Chinese bullet-point answers.
- User is building an AutoAgent platform in Spring Boot.
- Project uses DDD module boundaries.
- User prefers MySQL as source of truth and vector DB as semantic index.

Non-goals:

- Do not store one-off facts.
- Do not store model guesses as stable memory.
- Do not store sensitive personal facts unless explicitly provided and useful.

### 3.5 Artifact Memory

Purpose: find and load generated durable outputs such as articles, plans, reports, prompts, and code drafts.

MySQL source tables:

- `agent_artifact`
- artifact payload or future `agent_artifact_chunk`

Vector indexes:

- `vec_artifact_summary`
- `vec_artifact_chunk`

Usage:

- Resolve "那篇 RAG 文章", "第二版方案", "上次生成的宣誓稿".
- Locate relevant sections inside long artifacts.
- Load selected artifact content into MainAgentStateView.

### 3.6 RAG Knowledge

Purpose: private or uploaded knowledge base retrieval.

MySQL source tables:

- future `rag_document`
- future `rag_chunk`

Vector index:

- `vec_rag_chunk`

Rule: RAG knowledge is separate from user conversation memory. It is queried only when the user asks for knowledge-base, uploaded-document, project-document, private, or citation-backed retrieval.

## 4. Vector Storage Design

Vector collections are semantic indexes, not primary storage.

Recommended collections:

| Collection | MySQL Source | Purpose |
| --- | --- | --- |
| `vec_turn_summary` | `agent_turn_summary` | Recall per-turn summaries. |
| `vec_conversation_summary` | `agent_conversation_summary` | Recall long-range session summaries. |
| `vec_long_term_memory` | `agent_memory_item` | Recall active user/project memories. |
| `vec_artifact_summary` | `agent_artifact` | Recall artifacts by title, topic, type, and summary. |
| `vec_artifact_chunk` | artifact chunks/payload chunks | Recall sections inside long artifacts. |
| `vec_rag_chunk` | RAG chunks | Retrieve external/private knowledge. |

Every vector record must include metadata:

- `sourceType`
- `sourceId`
- `userId`
- `sessionId`
- `runId`
- `turnId`
- `artifactId`
- `memoryId`
- `createdAt`
- `updatedAt`
- `status`
- `importanceScore`
- `confidence`
- `version`
- `visibility`

Runtime must use vector results only as candidates, then load canonical data from MySQL.

## 5. Candidate Generation And Scoring

Context candidates come from multiple sources:

1. MySQL recency candidates:
   - recent turns
   - recent artifacts
   - last mentioned artifact
   - active memory items
2. MySQL keyword candidates:
   - title/summary/type matching
   - tags and source request matching
3. Vector semantic candidates:
   - turn summaries
   - rolling summaries
   - long-term memories
   - artifact summaries/chunks
4. Current run evidence:
   - RAG evidence
   - tool evidence

Score should combine:

```text
finalScore =
  semanticScore * 0.45
+ recencyScore * 0.25
+ referenceScore * 0.15
+ importanceScore * 0.10
+ explicitKeywordScore * 0.05
- decayPenalty
```

For reference-heavy queries such as "上次那篇文章", semantic score may be weak. In those cases, recency, last-mentioned markers, artifact type, source turn, and ContextPlanner judgment carry more weight.

## 6. Reference Resolution And Context Binding

Reference resolution is a first-class runtime capability.

Examples:

- "上次那篇文章"
- "刚才那个方案"
- "第二版"
- "修改前面的那篇关于 RAG 的文章"
- "比较这两篇宣誓稿"

Runtime should generate candidate references with rich explanations:

```json
{
  "artifactId": "artifact-123",
  "title": "RAG 知识点总结",
  "artifactType": "ARTICLE",
  "version": 3,
  "sourceTurnId": "turn-18",
  "sourceUserRequest": "给我生成一篇关于 RAG 的文章",
  "lastRelatedUserRequest": "把这篇 RAG 文章修改得正式一些",
  "matchReasons": ["topic-matched:RAG", "recent-artifact", "last-mentioned-artifact"],
  "scores": {
    "semantic": 0.82,
    "recency": 0.94,
    "reference": 0.90,
    "final": 0.89
  }
}
```

ContextPlanner selects or asks the user only when target identity remains genuinely ambiguous.

MainAgentStateView should include `resolvedReferences`:

```json
{
  "userPhrase": "那篇关于 RAG 的文章",
  "resolvedType": "ARTIFACT",
  "resolvedId": "artifact-123",
  "title": "RAG 知识点总结",
  "resolutionReason": "Matched recent ARTICLE artifact about RAG.",
  "confidence": 0.89,
  "expectedUse": "Treat this artifact as the user-intended article to modify."
}
```

MainAgentNode prompt must state that selected artifacts, memories, and summaries are already resolved context for the current request, not random background information.

## 7. ContextPlanner Role

ContextPlanner receives:

- current user input
- compact recent turn references
- vector-recalled candidates
- artifact candidates with score reasons
- active long-term memory candidates
- selected summaries
- current run evidence candidates
- token budget
- pending action and user clarifications

ContextPlanner outputs:

- selected artifacts and desired materialization level
- selected memories
- selected summaries
- selected evidence
- resolved references
- short context binding notes
- `NEEDS_USER_CLARIFICATION` only when ambiguity blocks safe continuation

ContextPlanner does not answer the user, execute tools, mutate memory, or decide runtime lifecycle.

## 8. MainAgent Context

MainAgentNode receives:

1. Current user input.
2. Latest 6 turns full text.
3. Previous 6 turn summaries.
4. Resolved references.
5. Selected artifact metadata and content.
6. Selected memories.
7. Selected summaries.
8. RAG/tool evidence.
9. User clarifications.
10. ContextPlanner binding notes.

Prompt requirements:

- Treat `resolvedReferences` as Runtime/ContextPlanner-resolved user intent.
- Treat `artifactContent` as selected target content when expectedUse says so.
- Do not ignore selected context.
- Do not ask the same clarification again if resolvedReferences or userClarifications answer it.
- Ask the user only if the task itself remains ambiguous or unsafe.

## 9. Memory LLM Nodes

Dedicated nodes are required. MainAgentNode should not perform memory maintenance.

### 9.1 TurnSummaryNode

Trigger: after final answer is persisted.

Input:

- user message
- final answer
- selected artifact/evidence refs
- run metadata

Output:

- summary
- user intent
- entities
- topics
- artifact references
- importance score
- whether long-term memory extraction should run

### 9.2 MemoryExtractorNode

Trigger: after turn summary when extraction is useful.

Output candidates:

- user preference
- user identity/profile fact
- project fact
- persistent constraint
- long-term goal

Each candidate must include confidence, source turn, sensitivity, and retention suggestion.

### 9.3 MemoryMergeNode

Trigger: after memory candidates are extracted.

Decisions:

- create
- update
- merge
- ignore
- conflict
- supersede

It prevents duplicate memory pollution.

### 9.4 ConversationRollupNode

Trigger:

- every N turns
- daily scheduled task
- when session exceeds context budget

Output:

- rolling summary for a range of turns
- key decisions
- open tasks
- artifact refs
- memory hints

### 9.5 MemoryGCNode

Trigger:

- scheduled daily/weekly
- every N turns
- after explicit user correction

Actions:

- `KEEP`
- `DECAY`
- `MERGE`
- `SUPERSEDE`
- `ARCHIVE`
- `RETRACT`
- `DELETE_INDEX_ONLY`

GC updates MySQL lifecycle fields and synchronizes vector indexes.

## 10. Memory Lifecycle

### 10.1 After Final Answer

```text
FINAL_READY
-> persist messages and turn
-> async TurnSummaryNode
-> save agent_turn_summary
-> index vec_turn_summary
-> optional MemoryExtractorNode
-> MemoryMergeNode
-> save/update agent_memory_item
-> index vec_long_term_memory
-> if artifacts changed, index vec_artifact_summary/chunk
```

### 10.2 Scheduled Maintenance

```text
daily / every N turns
-> ConversationRollupNode
-> save agent_conversation_summary
-> index vec_conversation_summary
-> MemoryGCNode
-> update memory status, confidence, importance, decay
-> remove or update stale vector records
```

## 11. MySQL Tables

New or revised tables:

- `agent_turn`: one completed user-agent interaction.
- `agent_turn_summary`: per-turn summary and extracted metadata.
- `agent_conversation_summary`: rolling summary over turn ranges.
- `agent_memory_item`: long-term memory with lifecycle state.
- `agent_memory_event`: audit log for memory creation, update, merge, retraction, GC.
- `agent_vector_index_ref`: optional index sync table to track vector record ids.
- future `agent_artifact_chunk`: chunk metadata for long artifacts.
- future `rag_document` and `rag_chunk`: RAG corpus source tables.

Important memory lifecycle fields:

- `status`: `ACTIVE`, `DEPRECATED`, `ARCHIVED`, `RETRACTED`
- `confidence`
- `importance_score`
- `source_turn_id`
- `source_message_id`
- `last_used_at`
- `last_verified_at`
- `superseded_by`
- `expires_at`
- `gc_reason`

## 12. Error Handling And Observability

Memory tasks must not block user-facing final responses.

Failure rules:

- If summary extraction fails, mark async task failed and keep conversation intact.
- If vector indexing fails, MySQL remains truth and retry task is scheduled.
- If MemoryMergeNode fails, keep extracted candidate as pending or failed, not active memory.
- If GC fails, do not delete vector records blindly.

Observability:

- Log memory task id, run id, session id, turn id.
- Persist node inputs/outputs as debug payloads.
- Record vector index operation results.
- Debug panel should show memory lifecycle events for the session.

## 13. Development Phases

### Phase 1: Structured Turns And Summaries

- Add `agent_turn`.
- Add `agent_turn_summary`.
- Persist completed turns.
- Add TurnSummaryNode.
- Add async summary task.
- MainAgent fixed context becomes latest 6 turns full text plus previous 6 summaries.

### Phase 2: Vector Index Foundation

- Add vector index abstraction.
- Add `vec_turn_summary`.
- Add `vec_artifact_summary`.
- Add MySQL-to-vector sync tracking.
- ContextPlanner receives vector-recalled turn and artifact candidates.

### Phase 3: Reference Resolution

- Add richer artifact candidate metadata.
- Add recency/reference scoring.
- Add `resolvedReferences` to MainAgentStateView.
- Update ContextPlanner and MainAgent prompts.

### Phase 4: Long-Term Memory

- Add `agent_memory_item`.
- Add MemoryExtractorNode.
- Add MemoryMergeNode.
- Add `vec_long_term_memory`.
- Add memory confidence and lifecycle statuses.

### Phase 5: Rolling Summary And GC

- Add ConversationRollupNode.
- Add `vec_conversation_summary`.
- Add MemoryGCNode.
- Add scheduled maintenance.
- Add vector cleanup and stale index repair.

### Phase 6: RAG And Artifact Chunk Indexing

- Add `vec_artifact_chunk`.
- Add `vec_rag_chunk`.
- Keep RAG corpus separate from conversation memory.
- Integrate retrieval policy with MainAgent action selection.

## 14. Open Decisions

1. Vector backend choice: pgvector, Milvus, Qdrant, Elasticsearch vector, or another provider.
2. Exact turn window sizes: proposed 6 full turns plus 6 summary turns.
3. Async execution engine: Spring `@Async`, task table polling, MQ, or scheduled executor.
4. Whether memory candidates require user-visible approval before becoming active.
5. Whether to index raw message chunks or only summaries and artifacts.

## 15. Acceptance Criteria

1. MainAgent can answer follow-up questions using recent turns without invoking vector search.
2. ContextPlanner can resolve "上次那篇文章" using artifact metadata and recency.
3. Semantic recall can find older summaries and artifacts by topic.
4. Long-term memories have lifecycle state and can be superseded or retracted.
5. Failed memory tasks do not break user-facing chat.
6. Vector indexes can be rebuilt from MySQL.
7. Debug logs show memory extraction, indexing, merge, and GC decisions.
