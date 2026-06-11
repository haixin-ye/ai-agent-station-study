# AutoAgent Known Gaps Backlog

## Accepted MVP Gaps

| Id | Gap | Why Accepted | Future Phase |
| --- | --- | --- | --- |
| GAP-001 | Full monolithic end-to-end scenario runner is lightweight for several scenarios. | Phase 12 validates all fixtures and uses focused real tests for each module. Direct answer and clarification run through Runtime; other scenario semantics are covered by action/RAG/tool/final/context tests. | Add a richer fake-port scenario runner after MVP stabilization. |
| GAP-002 | Normal SSE currently replays persisted events on connect; live push from Runtime append callbacks is not yet fully integrated. | Mock SSE and event replay cover frontend development; user-visible event persistence is already in place. | Wire `RunEventPublisher` or repository append callbacks to live `SseEmitterRegistry.send`. |
| GAP-003 | Debug payload preview uses simple size limiting, not a full redaction engine. | Preview is disabled by default and debug endpoints are gated. | Add structured redaction policy for prompts, tool receipts, and sensitive payload keys. |
| GAP-004 | Context budget verification is mostly deterministic unit coverage, not real tokenizer-accurate full-flow stress testing. | MVP uses current estimator and policy tests to protect obvious overflow paths. | Add tokenizer-calibrated stress fixtures with large artifacts and memories. |

## Blockers

| Id | Blocker | Impact | Required Fix |
| --- | --- | --- | --- |
| None | No blocker found. | - | - |

## Deferred Features

| Id | Feature | Reason Deferred |
| --- | --- | --- |
| BACKLOG-001 | LLM safety guard beyond Java `FinalResponseGuard` | MVP uses Java rule-based final guard only. |
| BACKLOG-002 | Business-specific tool result verification | MVP `ToolVerifier` validates real invocation and basic receipt status only. |
| BACKLOG-003 | Sub-agent delegation | Design reserved but not in MVP implementation. |
| BACKLOG-004 | Coding agent specialization | Future capability family; not hard-coded into MVP Runtime. |
| BACKLOG-005 | Admin UI for capability/prompt/config management | MVP keeps these in Java/yml/database boundaries already designed. |
| BACKLOG-006 | Live run visualizer for debug trace graph | Debug data is persisted and queryable, but visual graph is a frontend future task. |

