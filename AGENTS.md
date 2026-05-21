# Repository Guidelines

## Project Structure
This is a multi-module Maven project using Java 17 and Spring Boot 3.4.x.

- `ai-agent-station-study-api`: external service contracts, DTOs, and response wrappers.
- `ai-agent-station-study-trigger`: HTTP/job entry points, controllers, listeners, and web/SSE adapters.
- `ai-agent-station-study-domain`: AutoAgent business logic, runtime orchestration, domain services, entities, VOs, and domain repository ports.
- `ai-agent-station-study-infrastructure`: DAO/PO/MyBatis/repository adapters and external integration adapters.
- `ai-agent-station-study-app`: Spring Boot bootstrap, Java config, runtime assembly, application resources, and integration-style tests.
- `ai-agent-station-study-types`: shared constants, enums, and cross-module exceptions.
- `docs/dev-ops`: Docker Compose, SQL initialization, runtime seed SQL, and deployment scripts.

Main Java code lives under `src/main/java`. Tests are primarily under `ai-agent-station-study-app/src/test/java`.

## Current AutoAgent Architecture
- The active architecture is the AutoAgent main-loop Runtime, not the old fixed Node1-4 harness.
- Runtime is deterministic Java orchestration. It owns lifecycle phases, routing, persistence, SSE-visible events, pending input, recovery, and final delivery.
- `MainAgentNode` is the primary LLM decision/generation component. It emits one structured `MainAgentAction`.
- `ContextPlannerNode` is used for initial context selection/planning. Later loop iterations normally refresh state view without forced context replanning unless explicitly requested.
- RAG, MCP/tool calls, artifact operations, final delivery, pending input, and verification are Runtime-owned deterministic modules routed from `MainAgentAction`.
- `ASK_USER` is a Runtime pending-input pause/resume mechanism. Option clicks and free text are normalized by Java; the resumed source component decides semantic adequacy.
- High-risk tool approval uses deterministic `SINGLE_CHOICE` approve/reject options. Free text cannot authorize high-risk tool execution.
- Final user-visible answers must go through final delivery and guard logic. Normal UI must not expose raw prompts, contracts, traces, state view, tool receipts, or debug payloads.

## AutoAgent Package Rules
- LLM node entry services live under `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/node/<node>/`.
  - Examples: `service/node/contextplanner`, `service/node/mainagent`, `service/node/ragverifier`, `service/node/finalrepair`.
  - Future LLM nodes should follow the same pattern, for example `service/node/turnsummary/TurnSummaryNodeService.java`.
- Shared prompt builders live under `domain/agent/service/prompt`.
- Shared invocation pipeline, output mapping, parsing, validation, policies, routers, handlers, and builders stay under `domain/agent/service/**`.
- Data carriers do not belong in `service/**`.
  - `*VO`, `*Command`, `*Result`, `*Request`, `*Response`, and enums belong under `domain/agent/model/valobj/**` or `domain/agent/model/valobj/enums/**`.
  - Persistence entities belong under `domain/agent/model/entity/**`.
  - Domain repository ports belong under `domain/agent/adapter/repository/**`.
  - DAO interfaces and PO classes belong in `infrastructure`.
- Keep package names subdomain-aligned where practical: `runtime`, `context`, `invocation`, `prompt`, `rag`, `tool`, `memory`, `artifact`, `evidence`, `finalresponse`, `interaction`, and `contract`.

## Project-Level Spec Governance
- Treat `docs/architecture/auto-agent-prompt-harness-governance-spec.md` as the current long-lived governance spec for AutoAgent prompt, contract, node, Runtime, and state-view development.
- Treat `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md` as the main architecture reference.
- Treat `docs/superpowers/specs/2026-05-20-auto-agent-memory-lifecycle-design.md` as the current memory lifecycle design.
- Old Node1-4 and `DynamicContext` docs are historical references only. Do not use them as implementation guidance unless the user explicitly asks for legacy analysis.
- Java contract definitions are the source of truth. MySQL system prompts should contain role, responsibility, boundary, style, and stable behavior principles only; do not move runtime schemas, parser rules, fallback logic, or state write scopes into database prompt text.

## Development Rules
- Preserve DDD boundaries:
  - `domain`: business decisions, Runtime behavior, node entry services, contracts, validators, policies, domain VOs/entities, and repository ports.
  - `infrastructure`: persistence adapters, DAO/PO/MyBatis, external integration adapters.
  - `trigger`: HTTP/SSE/job entry points and DTO translation.
  - `app`: Spring assembly, runtime config, and integration-style tests.
- Prefer existing local patterns over new abstractions.
- Keep changes scoped to the requested behavior and surrounding ownership boundary.
- Use UTF-8. Avoid introducing mojibake. Historical mojibake in archived docs is not a reason to copy that style into new content.
- If a later feature exposes a missing interface, mapper, adapter, contract, or lifecycle boundary, implement the root-cause design instead of adding shortcut code.
- Prompt, contract, Runtime, and frontend/API changes must be checked together when they affect each other.

## Build, Test, And Timeout Policy
- Full compile: `mvn -q -DskipTests compile`
- App test command pattern: `mvn -q -pl ai-agent-station-study-app -am "-Dtest=SomeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- App module POM may skip tests by default; pass explicit test properties when needed.
- Suggested `timeout_ms`:
  - Maven compile or targeted tests: `180000`
  - full/integration-style test runs: `300000`
  - large `rg` scans or heavier git operations: `120000`

## Git Rules
- Work on a feature branch, not `master`, unless explicitly instructed.
- Keep commits focused and purpose-first, for example `agent: add turn summary node`.
- Do not commit local runtime artifacts, logs, caches, downloaded tools, or unrelated untracked files.
- Never revert user changes unless explicitly requested. If unrelated files are dirty, leave them alone.

## Assistant Output Contract
Before every assistant response, print:

```text
[Skills]
- used_this_turn: <comma-separated skill names or "none">
- used_in_session: <comma-separated skill names or "none">
```

Keep this block at the top of every commentary and final response.
