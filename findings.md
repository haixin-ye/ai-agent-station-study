# AutoAgent Current Findings

This file records current implementation guidance. Historical Node1-4 and `DynamicContext` notes are archived in older specs and should not be treated as active development rules.

## Current Architecture

- AutoAgent uses a deterministic Java Runtime main loop.
- `MainAgentNode` emits one structured `MainAgentAction`; Runtime routes the action to deterministic handlers.
- `ContextPlannerNode` is used for initial context planning and selected explicit replanning, not as a mandatory step before every MainAgent call.
- Continued-loop routing is centralized in `RuntimeRoutePolicy`.
- RAG/tool/artifact modules return evidence, receipts, or state updates to Runtime; Runtime then returns to state-view building or MainAgent according to route policy.
- `ASK_USER` is a Runtime pending-input pause/resume mechanism. It should resume from the stored checkpoint, not restart the user request from scratch.
- Final answers are delivered only through final delivery and guard services. Trace/debug/verifier/tool data must not become normal assistant messages.

## Current Node Layout

- Node entry services belong under `domain/agent/service/node/<node>/`.
- Current node entry services:
  - `service/node/contextplanner/ContextPlannerNodeService.java`
  - `service/node/mainagent/MainAgentNodeService.java`
  - `service/node/ragverifier/RagVerifierNodeService.java`
  - `service/node/finalrepair/FinalRepairNodeService.java`
- Future LLM node entry services should follow the same location, for example `service/node/turnsummary/TurnSummaryNodeService.java`.
- Node entry services call `NodeInvocationPipeline`; they should not own shared parsing, contract validation, prompt assembly, or persistence logic.

## DDD Package Rules

- `service/**` contains behavior: services, handlers, routers, policies, builders, validators, pipelines, and node entry services.
- Data carriers belong in `model/**`, not `service/**`.
- `*VO`, `*Command`, `*Result`, `*Request`, `*Response`, and enums belong under `model/valobj/**` or `model/valobj/enums/**`.
- Persistence entities belong under `model/entity/**`.
- Domain repository ports belong under `adapter/repository/**`.
- DAO, PO, mapper XML, and repository adapter implementations belong in `infrastructure`.
- Spring bean assembly belongs in `app/config`.
- HTTP/SSE controllers and web registries belong in `trigger`.

## Prompt And Contract Boundary

- Java-owned contracts are the source of truth for structured outputs.
- Database prompts are role/style/behavior configuration only.
- `PromptAssembler` composes DB role prompts, shared Java-owned prompt layers, component prompt builders, output contracts, input view, and output-only instructions.
- `CONTRACT_REPAIR` repairs JSON/contract shape only.
- `FINAL_REPAIR` repairs the final user-facing answer only.
- Do not merge `FinalRepairPromptBuilder` and `ContractRepairPromptBuilder` back into one generic repair prompt.

## Runtime And UI Boundary

- Normal frontend consumes safe messages, user-visible run events, pending input, artifacts, and final response.
- Debug details belong in debug APIs/logs/traces only.
- Backend failures must produce terminal run events or safe failure output instead of leaving the frontend waiting indefinitely.
- SSE reconnect/replay should preserve user view state and should not force-close expanded thinking/debug panels.

## Memory Direction

- MySQL is the source of truth for conversation turns, summaries, artifacts, memory lifecycle, status, audit, and recovery.
- Vector storage is semantic index only, never the only copy of important memory.
- Recent context should be injected deterministically from MySQL before semantic recall:
  - latest completed turns as full text;
  - older recent turns as summaries.
- ContextPlanner selects additional relevant artifacts, summaries, memory items, and evidence; MainAgent receives clean context with selection reasons.
- Memory extraction, rolling summaries, vector indexing, merge, and GC should run asynchronously outside the user-facing critical path.
