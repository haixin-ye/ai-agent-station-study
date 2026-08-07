# AutoAgent Current Effective Prompt Baseline

Snapshot date: 2026-07-29

This directory records the current effective `TEXT_JSON` prompt messages before prompt-content
optimization begins.

## Directory Contract

- `en/`: English prompt snapshots assembled from the active database role prompts and the current
  Java-owned prompt layers. `MAIN_AGENT.prompt.txt` contains the actual `system` and `user` messages
  sent for the `PLANNING`, `EXECUTING`, and `DELIVERING` stages. These files are the behavioral
  baseline and preserve the actual layer order produced by `PromptAssembler`.
- `zh-CN/`: Chinese translations retained for components that were previously documented there.
  MainAgent Chinese review text is discussed directly in the development conversation and is not
  stored as a runtime or documentation prompt file.
- MainAgent review input uses deterministic representative `RunContextEnvelope` JSON for each stage.
  The system message is exact for the current code; the user message has the same shape as the
  runtime envelope and demonstrates the loop timeline, task ledger, runtime outcome, and resolved
  payloads that are passed between MainAgent calls.
- JSON field names, enum values, action names, capability codes, contract versions, and JSON examples
  remain unchanged in Chinese review files.

## Active Chat Components

1. `CONTEXT_PLANNER`
2. `MAIN_AGENT`
3. `GENERIC_SUB_AGENT`
4. `RAG_VERIFIER`
5. `FINAL_REPAIR`
6. `CONTRACT_REPAIR`
7. `TURN_SUMMARY`
8. `MEMORY_EXTRACTOR`
9. `SESSION_TASK_SUMMARY`
10. `MEMORY_GOVERNANCE`
11. `CONVERSATION_ROLLUP`

`CONTRACT_REPAIR` is represented by multiple files because its output-contract layer changes with
the original component contract. `RAG_ASSET_ANALYZER` is absent because the exported runtime has no
active model binding and falls back to deterministic Java analysis. `VECTOR_EMBEDDING` is not a chat
prompt component.

## Snapshot Sources

- Active bindings exported in `prompt1.csv` by the user.
- Active role prompts exported in `prompt2.csv` by the user.
- Shared/component/contract layers from the current repository code.
- Invocation mode: `TEXT_JSON`.

These snapshots intentionally preserve current duplication and contradictions. They are evidence for
the upcoming content redesign, not proposed final prompts.
