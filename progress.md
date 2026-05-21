# AutoAgent Current Progress

This file records the current implementation state. Older detailed checkpoint history lives in `docs/superpowers/progress.md` and Git history. Old Node1-4, `DynamicContext`, `ToolExecutionNode`, and `UserInputResolverNode` notes are historical only and are not current implementation guidance.

## Current Branch

- `feature/auto-agent-main-loop-harness`

## Recent Checkpoints

- `de13af9 agent: checkpoint user ask and runtime diagnostics`
  - Frontend ASK_USER/debug usability, async diagnostic logging, safer failure reporting, and SQL/runtime length patches.
- `944a744 agent: refine runtime context routing`
  - Split initial `prepareContext` from continued-loop `refreshContext`.
- `a0d1cfd agent: resume user ask from checkpoints`
  - Pending-input handlers honor continuation checkpoints and tool approvals resume from the correct phase.
- Runtime route policy refinement completed.
  - `RuntimeRoutePolicy` centralizes continued-loop phase decisions.
- `daafebb agent: organize llm node entrypoints`
  - Extracted MainAgent node entry and split FinalRepair/ContractRepair prompt builders.
- `91e8375 agent: move node entrypoints under service`
  - Moved node entry services to `domain/agent/service/node/<node>/`.

## Current Implemented State

- Runtime main-loop architecture is active.
- LLM node entry services are grouped under `domain/agent/service/node`.
- Prompt assembly uses Java-owned layered prompts plus DB role prompts.
- `CONTRACT_REPAIR` and `FINAL_REPAIR` are separate prompt builders and behaviors.
- Context planning is initial/explicit; continued loops refresh state view unless forced replanning is requested.
- USER_ASK uses Runtime pending input and checkpoint resume.
- RAG verification is fact-triggered by actual RAG usage.
- Tool approval is deterministic approve/reject, no free-text authorization for high-risk tools.
- Final delivery owns assistant-message persistence and guard/repair/fallback.
- Normal frontend/debug boundaries are separated.

## Current Verification Baseline

Recently verified:

- `mvn -q -pl ai-agent-station-study-app -am '-Dtest=PromptAssemblerTest,FinalRepairServiceTest,FinalDeliveryServiceTest,RagVerifierRoutingTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `mvn -q -DskipTests compile`

Known verification note:

- A full `mvn -q -pl ai-agent-station-study-app -am -DskipTests=false test` run timed out after 300 seconds during the memory/governance cleanup session. Treat full-suite verification as needing a split test matrix.

## Next Work

1. Implement memory phase 1 structured turns using the current `service/node/<node>` structure.
2. Add async turn summary processing and deterministic recent-turn context injection.
3. Continue RAG and MCP production testing after memory baseline is stable.
