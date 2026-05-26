# AutoAgent Memory System Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the simplified memory system: turn full text, turn summary, strict long-term memory, and session task summary, while logically deprecating artifacts and conversation rollup.

**Architecture:** Keep Runtime turn persistence synchronous. Memory GC owns derived records and asynchronous maintenance. MySQL remains the source of truth; pgvector remains a semantic index for turn summaries and long-term memories only.

**Tech Stack:** Java 17, Spring Boot, MyBatis, MySQL, pgvector, Spring AI node invocation pipeline.

---

## Testing Policy

Use minimal necessary tests:

- One focused test for each new domain behavior.
- Existing GC worker tests updated only when interface changes.
- Compile after each checkpoint.
- Avoid broad integration tests until the flow is stable.

## Checkpoint 1: Schema And Domain Foundation

**Files:**
- Modify: `docs/dev-ops/mysql/sql/auto-agent-main-loop-harness.sql`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/persistence/AgentSessionTaskSummaryEntity.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/model/entity/persistence/AgentMemoryEntity.java`
- Create: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/adapter/repository/ISessionTaskSummaryRepository.java`
- Modify: `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/adapter/repository/IMemoryRepository.java`
- Modify infra PO/DAO/mapper/repository for long-term memory and session task summary.

- [x] Add lifecycle/source columns to `agent_long_term_memory`.
- [x] Add `agent_session_task_summary`.
- [x] Add domain entity and repository port.
- [x] Add infra repository and mapper.
- [x] Add one repository-focused test or compile-only verification if mapper coverage is too heavy.
- [ ] Commit `agent: add session task summary persistence`.

## Checkpoint 2: Session Task Summary Node And Worker

**Files:**
- Add VO classes under `domain/agent/model/valobj/memory`.
- Add `SessionTaskSummaryNodeService`.
- Add prompt builder, output contract renderer, contract registry, output mapper entries.
- Add `SessionTaskSummaryGcWorker`.
- Modify `TurnSummaryGcWorker` to schedule `SESSION_TASK_SUMMARY` every threshold turns instead of `CONVERSATION_ROLLUP`.
- Modify `AutoAgentRuntimeConfig`.
- Modify `docs/dev-ops/mysql/sql/auto-agent-model-runtime.sql`.

- [ ] Add `SESSION_TASK_SUMMARY` component and memory task enum.
- [ ] Add minimal worker test: threshold creates session task summary task and worker saves active version.
- [ ] Stop scheduling `CONVERSATION_ROLLUP`.
- [ ] Keep old conversation rollup code present but unused.
- [ ] Compile.
- [ ] Commit `agent: add session task summary gc`.

## Checkpoint 3: Strict Long-Term Memory Extraction

**Files:**
- Modify `MemoryExtractionPromptBuilder` or relevant prompt provider.
- Modify SQL seed prompt for `MEMORY_EXTRACTOR`.
- Modify `LongTermMemoryGcWorker`.
- Modify `MemoryManager` / repository to store source fields and default status.

- [ ] Tighten extractor prompt to profile/preference/project-background only.
- [ ] Ensure extracted memory stores source run/turn and status `ACTIVE`.
- [ ] Add one focused prompt/worker test that ordinary Q&A output with empty memory list succeeds without saving memory.
- [ ] Compile.
- [ ] Commit `agent: tighten long term memory extraction`.

## Checkpoint 4: Context Preparation Without Artifacts

**Files:**
- Modify `ContextCandidatePreselector`.
- Modify `ContextPreparationService`.
- Modify context VO if needed for session task summary.
- Modify `DefaultRuntimeComponentPorts`.
- Modify `ContextPlannerPromptBuilder`.
- Modify SQL seed prompt for `CONTEXT_PLANNER`.

- [ ] Load latest active session task summary by session id.
- [ ] Inject session task summary into planning context/state view.
- [ ] Keep latest 6 full turns and 7-12 turn summaries.
- [ ] Keep vector turn summary and long-term memory recall.
- [ ] Stop producing artifact candidates in new context preparation path.
- [ ] Remove artifact guidance from ContextPlanner prompt.
- [ ] Add one context preparation test for session task summary injection and artifact candidate empty list.
- [ ] Compile.
- [ ] Commit `agent: align context preparation with memory redesign`.

## Checkpoint 5: LLM Memory Governance Skeleton

**Files:**
- Add `MemoryGovernanceNodeService`.
- Add governance input/output VOs.
- Add prompt/contract/mapper/registry/runtime config entries.
- Add `MemoryGovernanceGcWorker`.
- Extend repository methods to update memory status and disable vector indexes.

- [ ] Add LLM-driven governance contract with `KEEP`, `MERGE`, `SUPERSEDE`, `DISABLE`, `UPDATE`, `NOOP`.
- [ ] Validate referenced memory ids before applying actions.
- [ ] Update MySQL status first, then vector index state.
- [ ] Write memory events.
- [ ] Add one worker test for unknown memory id being rejected/ignored safely.
- [ ] Compile.
- [ ] Commit `agent: add memory governance gc skeleton`.

## Checkpoint 6: Verification

- [ ] Run targeted GC/context tests touched above.
- [ ] Run `mvn -q -DskipTests compile`.
- [ ] Run `git diff --check`.
- [ ] Update `task_plan.md` and `progress.md`.
