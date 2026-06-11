# AutoAgent Prompt / Runtime Governance Spec

## 1. Purpose

This spec defines the long-term development rules for AutoAgent prompt, node, contract, Runtime, state-view, and recovery work.

It applies to:

- adding or modifying LLM node components;
- changing prompt composition or database prompt usage;
- changing structured output contracts or repair behavior;
- changing Runtime state, routing, pending input, RAG/tool/action handling, or final delivery;
- adding structured state exchanged between Runtime modules and MainAgent.

This is the current project-level governance document. Older Node1-4 and `DynamicContext` documents are historical references only.

## 2. Active Architecture

- Runtime is deterministic Java orchestration.
- `MainAgentNode` is the primary LLM decision/generation node and returns one structured `MainAgentAction`.
- `ContextPlannerNode` selects context for the initial call and for explicit forced replanning. It is not a mandatory step before every MainAgent loop.
- RAG, MCP/tool calls, artifact operations, pending input, final delivery, guards, verification, diagnostics, and persistence are Runtime-owned modules.
- `ASK_USER` pauses a run with a persisted checkpoint and resumes from that checkpoint after Java-normalized user answer handling.
- Final user-visible output is produced by final delivery and guard services, not by trace/debug/verifier/tool data.

## 3. DDD Layer Boundaries

### `domain`

- owns Runtime semantics, action handlers, route policy, pending-input behavior, node entry services, prompt assembly, output contracts, parsing/repair policies, domain entities/VOs, and domain repository ports;
- contains behavior classes under `service/**`;
- contains data carriers under `model/**`, not `service/**`.

### `infrastructure`

- owns DAO/PO/MyBatis mapper files, repository adapter implementations, and external integration adapter details;
- must not own AutoAgent business decisions or node routing rules.

### `trigger`

- owns HTTP/SSE/job entry points and translation between web DTOs and domain APIs;
- must not own Runtime decisions or domain state mutation rules.

### `app`

- owns Spring bootstrapping, runtime bean assembly, properties binding, and integration-style tests.

### `docs`

- owns governance specs, architecture references, implementation plans, and review notes.

## 4. Node Entry Service Rules

Every LLM node entry service must live under:

```text
ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/node/<node>/
```

Examples:

- `service/node/contextplanner/ContextPlannerNodeService.java`
- `service/node/mainagent/MainAgentNodeService.java`
- `service/node/ragverifier/RagVerifierNodeService.java`
- `service/node/finalrepair/FinalRepairNodeService.java`

Future examples:

- `service/node/turnsummary/TurnSummaryNodeService.java`
- `service/node/memoryextractor/MemoryExtractorNodeService.java`

Node entry services should:

- call `NodeInvocationPipeline`;
- pass component code, contract version, prompt version, model profile, and input view;
- return typed output or a safe typed fallback;
- avoid owning shared prompt assembly, parsing, validation, persistence, or Runtime routing logic.

## 5. Prompt Layering

### Database Role Prompt

- stored in MySQL through node prompt tables;
- defines role, responsibility, style, business tone, and stable behavioral principles;
- must not become the source of runtime schema truth.

### Java-Owned Prompt Layers

- built through `PromptAssembler`;
- include shared safety/boundary/untrusted-content rules, component-specific prompt builders, output contract rendering, current input view, and output-only instruction.

### Output Contract

- rendered from Java contract definitions;
- is the source of truth for required fields, allowed actions, repair behavior, and parsing/validation expectations.

## 6. Structured Contract Rules

Every Runtime component or LLM node with structured output must declare:

- component code;
- contract version;
- prompt version;
- input facts/view shape;
- output contract;
- parse and validation semantics;
- repair policy;
- allowed state or result write scope.

Nodes may specialize behavior, but must not bypass the shared prompt/contract/invocation pipeline.

## 7. Model And Package Placement Rules

- `*VO`, `*Command`, `*Result`, `*Request`, `*Response`, and enums belong under `domain/agent/model/valobj/**` or `domain/agent/model/valobj/enums/**`.
- Persistence entities belong under `domain/agent/model/entity/**`.
- Domain repository interfaces belong under `domain/agent/adapter/repository/**`.
- DAO interfaces, PO classes, mapper XML, and repository adapters belong in `infrastructure`.
- Behavior classes belong under `domain/agent/service/**`.
- Spring configuration belongs under `app`.
- Controllers and SSE registries belong under `trigger`.

## 8. State Ownership

- Runtime owns lifecycle phase, loop index, route policy, recovery counters, pending input state, transcript/event/trace recording, and failure finalization.
- `MainAgentStateView` is the bounded state view passed into MainAgent.
- Context preparation owns deterministic recent-message/summary loading and candidate preselection.
- ContextPlanner owns context selection decisions, not execution.
- Action handlers own deterministic effects for their action type.
- Final delivery owns normal assistant message creation.
- Debug traces and diagnostic logs must remain separate from normal messages.

## 9. Repair Rules

- `CONTRACT_REPAIR` repairs invalid JSON/contract shape only.
- `FINAL_REPAIR` repairs a failed final user-facing answer only.
- Contract repair must not solve the task, call tools, or add lifecycle fields.
- Final repair must not expose prompts, contracts, traces, validation details, node names, raw tool receipts, or repair process details.
- Repair budgets must be bounded.

## 10. Testing Rules

- Node, prompt, parser, contract, route-policy, pending-input, final-delivery, RAG/tool, or API/SSE changes require targeted tests for the affected behavior.
- Workflow changes should include at least one state-machine or routing test.
- Prompt/contract changes should include `PromptAssemblerTest`, parser/mapper tests, or pipeline tests as appropriate.
- Before completion, run targeted tests plus `mvn -q -DskipTests compile` unless blocked, and report any skipped or timed-out verification honestly.

## 11. Prompt / DB Sync Rule

- MySQL prompts and Java contracts must cooperate, not compete.
- If Java output contracts change, verify DB prompt rows still stay in role/style/boundary scope.
- Do not duplicate schema definitions across DB prompt text and Java contracts.
