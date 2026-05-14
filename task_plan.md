# AutoAgent Harness Redesign Plan

Canonical working note:
`docs/superpowers/specs/2026-04-28-auto-agent-main-loop-harness-working-notes.md`

Future task backlog:
`docs/superpowers/future-dev-tasks.md`

## Current Goal

Redesign the AutoAgent harness from the current fixed multi-node ReAct-style chain into a main-loop architecture centered on `MainAgentNode` plus deterministic Java Runtime orchestration.

## Active Design Phases

- [x] Problem analysis: node coordination imbalance, intermediate-output leakage, context overflow, frontend trace noise.
- [x] Main architecture: `MainAgentNode`, Runtime, ContextPlanner, Verifiers, Memory, Artifacts, Evidence.
- [x] Memory lifecycle: session messages, summaries, long-term memory, run-level state.
- [x] Tool/RAG execution model: explicit RAG action; all external tool use goes through `CALL_TOOL` and Runtime-owned `ToolRuntime` backed by Spring AI MCP clients; Runtime captures receipts and evidence.
- [x] Logging and frontend display model: final response, visible events, developer trace, audit, evidence, artifacts.
- [x] Artifact resolution and context policy: artifact id is internal, ContextPlanner resolves references and decides content granularity.
- [ ] AgentState and StateView schema design.
- [x] Runtime loop detailed state machine.
- [x] Database table design and repository boundaries.
- [x] DDD package/module layout for implementation.
- [x] Prompt and output contract design for ContextPlanner and MainAgentNode.
- [x] Frontend API and SSE interaction design.
- [x] Verification and testing strategy.
- [x] Final spec consolidation and implementation plan completed: master plan plus Phase 0-12 executable plans created.
- [x] Final spec files created: English canonical spec and Chinese review sample.
- [x] Master implementation plan created: `docs/superpowers/plans/2026-05-12-auto-agent-main-loop-harness-master-plan.md`.
- [x] Phase 0/1 executable implementation plan created: `docs/superpowers/plans/2026-05-12-auto-agent-phase-0-1-contract-skeleton.md`.
- [x] Phase 2 executable implementation plan created: `docs/superpowers/plans/2026-05-12-auto-agent-phase-2-persistence-repository.md`.
- [x] Phase 3 executable implementation plan created: `docs/superpowers/plans/2026-05-12-auto-agent-phase-3-prompt-node-invocation.md`.
- [x] Phase 4 executable implementation plan created: `docs/superpowers/plans/2026-05-12-auto-agent-phase-4-context-artifact-memory.md`.
- [x] Phase 5 executable implementation plan created: `docs/superpowers/plans/2026-05-12-auto-agent-phase-5-runtime-pending-input.md`.
- [x] Phase 6 executable implementation plan created: `docs/superpowers/plans/2026-05-12-auto-agent-phase-6-main-action-handlers.md`.
- [x] Phase 7 executable implementation plan created: `docs/superpowers/plans/2026-05-12-auto-agent-phase-7-rag-runtime-verification.md`.
- [x] Phase 8 executable implementation plan created: `docs/superpowers/plans/2026-05-12-auto-agent-phase-8-tool-mcp-permission-approval.md`.
- [x] Phase 9 executable implementation plan created: `docs/superpowers/plans/2026-05-12-auto-agent-phase-9-final-response-guard-repair.md`.
- [x] Phase 10 executable implementation plan created: `docs/superpowers/plans/2026-05-12-auto-agent-phase-10-api-sse-debug-mock.md`.
- [x] Phase 11 executable implementation plan created: `docs/superpowers/plans/2026-05-12-auto-agent-phase-11-old-harness-isolation-cleanup.md`.
- [x] Phase 12 executable implementation plan created: `docs/superpowers/plans/2026-05-12-auto-agent-phase-12-mvp-verification-review.md`.

## Operating Rule

Before continuing design or implementation, read this file and the canonical working note to recover decisions.

## Development Execution Rule

- Use a dedicated feature branch for the harness redesign implementation.
- Record meaningful Git checkpoints in `progress.md` with branch name, commit hash, completed slice, verification command, and result.
- Treat Phase 0-12 as the full backlog and verification map, not as a rigid coding order.
- Implement by vertical slices: foundation skeleton, minimal end-to-end direct-answer flow, persistence, prompt/context, runtime actions, RAG/tool capabilities, final/API/debug, old-harness isolation, MVP verification.
- When a later-phase module is needed early, create a stable interface and fake/stub implementation first, then fill the production implementation later.
- Keep DDD package names explicit and subdomain-aligned:
  - external API contracts use `*Api`;
  - domain service contracts use `*DomainService`;
  - `domain/agent/model/entity`, `domain/agent/model/valobj`, and `domain/agent/model/valobj/enums` must use subdomain folders matching service domains where practical, such as `armory`, `rag`, `runtime`, `contract`, `tool`, `token`, and `memory`.
  - `domain/agent/service/**` must contain behavior-oriented services, strategies, builders, pipelines, validators, and policies only. Data carrier classes such as `*VO`, `*Command`, `*Result`, `*Request`, `*Response`, and enums must live under `domain/agent/model/valobj/**` or `domain/agent/model/valobj/enums/**` with matching subdomain folders.

## Implementation Status

- [x] Dedicated branch created: `feature/auto-agent-main-loop-harness`.
- [x] First foundation slice implemented locally: core contract registry, parser, validator, StateDelta scope rules, core enums, typed AutoAgent properties, and targeted tests.
- [x] First Git checkpoint commit recorded in `progress.md`: `e4094f3` (`agent: checkpoint foundation contracts`).
- [x] Second vertical slice implemented: minimal fake-node direct-answer Runtime path with final guard and 16 passing targeted tests.
- [x] Second Git checkpoint commit recorded in `progress.md`: `4261ab6` (`agent: add direct runtime slice`).
- [x] Third cleanup/DDD alignment slice implemented: old Node1-4 execute chain, old controller/API DTOs, old tests, and old Node1-4-only state objects removed; reusable contract/tool/token objects moved to `model/valobj`.
- [x] Third Git checkpoint commit recorded in `progress.md`: `8f8a49b` (`agent: remove legacy execute harness`).
- [x] Fourth DDD package normalization slice implemented: RAG API/domain naming separated and model entity/valobj/enums moved into subdomain folders.
- [x] Fourth Git checkpoint commit recorded in `progress.md`: `09beb99` (`agent: normalize ddd package layout`).
- [x] Source mojibake cleanup checkpoint implemented and recorded: `5d09538` (`agent: clean source mojibake`), with historical SQL seed files intentionally left untouched.
- [x] Fifth checkpoint started and first persistence boundary slice committed: `4cd3252` (`agent: add persistence boundary contracts`), covering domain persistence entities, persistence enums, repository interfaces, and boundary tests before full DAO/mapper/DDL implementation.
- [x] Sixth checkpoint committed: `d0f882b` (`agent: add core persistence infrastructure`), including full new harness DDL plus core PO/DAO/MyBatis mapper/repository adapters for payload, conversation, run, artifact, evidence, pending input, and event/trace/audit.
- [x] Seventh checkpoint committed: `3625eff` (`agent: add remaining persistence adapters`), covering remaining persistence infrastructure for memory, tool call/approval/verification, RAG query/hit, node prompt, and run transcript.
- [x] Eighth checkpoint committed: `0da8870` (`agent: add prompt invocation pipeline`), covering prompt assembly and node invocation pipeline, including layered Java-owned prompt construction, output contract rendering, node client port, parser/mapper/validator pipeline, bounded repair, Spring AI adapter skeleton, and prompt/invocation tests.
- [x] Ninth checkpoint committed: `56bc3a4` (`agent: add context preparation layer`), covering context/artifact/memory/evidence preparation layer, including compact candidate preselection, artifact ranking/policy/resolution/loading, memory and evidence selection, ContextPlanner wrapper/status handling, context materialization, budget shrink, MainAgentStateView building, and targeted context tests.
