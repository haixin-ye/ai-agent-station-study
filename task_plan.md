# AutoAgent Current Development Plan

## Current Goal

Continue building AutoAgent as a production-grade main-loop agent system with clear DDD boundaries, observable Runtime behavior, reliable pending-input handling, and a memory system based on MySQL truth plus vector semantic indexes.

## Canonical References

- Current governance: `docs/architecture/auto-agent-prompt-harness-governance-spec.md`
- Main-loop architecture: `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`
- Memory lifecycle design: `docs/superpowers/specs/2026-05-20-auto-agent-memory-lifecycle-design.md`
- Memory phase 1 implementation plan: `docs/superpowers/plans/2026-05-21-auto-agent-memory-phase-1-structured-turns.md`
- Current findings: `findings.md`
- Historical progress: `progress.md` and `docs/superpowers/progress.md`

Historical Node1-4 and `DynamicContext` documents are archive material only and are not current implementation guidance.

## Current Implemented Baseline

- [x] AutoAgent main-loop Runtime exists and is wired through Spring config.
- [x] `MainAgentNode` action output goes through `NodeInvocationPipeline`.
- [x] Context preparation separates initial ContextPlanner planning from later state-view refresh.
- [x] `RuntimeRoutePolicy` centralizes continued-loop routing.
- [x] USER_ASK resumes from continuation checkpoints.
- [x] RAG runtime and fact-triggered RAG verification exist.
- [x] Tool/MCP permission and approval flow exists.
- [x] Final delivery, final guard, and final repair exist.
- [x] Normal frontend/debug boundary and async diagnostic log writing exist.
- [x] Node entry services have been moved to `domain/agent/service/node/<node>/`.
- [x] `FINAL_REPAIR` and `CONTRACT_REPAIR` prompt builders are split.

## Current Package Rules

- LLM node entry services:
  - `domain/agent/service/node/contextplanner/ContextPlannerNodeService.java`
  - `domain/agent/service/node/mainagent/MainAgentNodeService.java`
  - `domain/agent/service/node/ragverifier/RagVerifierNodeService.java`
  - `domain/agent/service/node/finalrepair/FinalRepairNodeService.java`
- Future LLM nodes:
  - add node entry service under `domain/agent/service/node/<node>/`;
  - add component prompt builder under `domain/agent/service/prompt`;
  - add input/output VOs under `domain/agent/model/valobj/<subdomain>`;
  - add component enum under `domain/agent/model/valobj/enums/contract`;
  - update `PromptAssembler`, `OutputContractPromptRenderer`, and `NodeOutputMapper` when structured output is needed;
  - add app config/model binding only at the assembly layer.
- `domain/agent/service/**` must contain behavior only.
- Data carriers such as `*VO`, `*Command`, `*Result`, `*Request`, `*Response`, and enums must live under `domain/agent/model/valobj/**` or `domain/agent/model/valobj/enums/**`.
- Persistence entities live under `domain/agent/model/entity/**`.
- Repository ports live under `domain/agent/adapter/repository/**`.
- DAO/PO/mapper/repository adapter implementations live in `infrastructure`.

## Development Workflow

- Use focused Git checkpoints.
- Keep unrelated dirty files untouched.
- Prefer vertical slices that prove real behavior.
- If a missing repository method, mapper, adapter, lifecycle boundary, prompt contract, or state transition is discovered, implement the root-cause design rather than a shortcut.
- For prompt/contract/node changes, verify Java-owned contract, DB prompt scope, parser/mapper behavior, and tests together.
- For frontend/API changes, check backend DTOs, SSE payload shape, and normal/debug boundary together.

## Next Work Queue

1. Implement memory phase 1 structured turns.
2. Add async turn summary node and task processing.
3. Inject latest full turns and previous summaries deterministically into context preparation.
4. Prepare memory vector index and GC phases after phase 1 is stable.
5. Continue RAG and MCP production testing after memory baseline is in place.
