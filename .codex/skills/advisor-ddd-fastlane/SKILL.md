---
name: advisor-ddd-fastlane
description: Use when adding or modifying Spring AI advisors in this repository (ai-agent-station-study) and the change must follow the project's DDD layering, advisor enum/VO/infra wiring, and database registration flow with fast delivery and minimal required tests.
---

# Advisor Ddd Fastlane

## Overview
Implement new advisors in this repo with a fixed, repeatable path: advisor logic -> enum registration -> VO/extParam parsing -> node wiring -> DB script output.  
Optimize for delivery speed: run minimal required tests only, not full TDD by default.

## Repository Contract
Use these project-specific anchors:
- Advisor class path: `ai-agent-station-study-domain/src/main/java/cn/bugstack/ai/domain/agent/service/armory/factory/element/`
- Advisor type enum: `AiClientAdvisorTypeEnumVO`
- Advisor VO: `AiClientAdvisorVO`
- Advisor runtime build node: `AiClientAdvisorNode`
- ChatClient assembly node: `AiClientNode`
- Infra parsing: `AgentRepository#AiClientAdvisorVOByClientIds`
- DB tables: `ai_client_advisor`, `ai_client_config`

## Fast Workflow
1. Clarify advisor behavior
- Define input, output, fail policy, and `order` strategy.
- Confirm if the advisor needs extra model beans, vector store, or context params.

2. Implement advisor logic
- Add `<NewAdvisor>.java` under `factory/element`.
- Keep runtime behavior deterministic: clear fail path and clear pass path.
- If this advisor should run first, return low order (for example `0`).

3. Register advisor type
- Add enum constant in `AiClientAdvisorTypeEnumVO`.
- Implement `createAdvisor(...)` for this type.
- If extra config is required, validate required fields and throw explicit runtime errors.

4. Extend config VO
- Add nested config class in `AiClientAdvisorVO` when `ext_param` has custom fields.
- Keep names aligned with JSON keys used in DB.

5. Parse DB ext_param in infra
- Update `AgentRepository#AiClientAdvisorVOByClientIds`:
  - Parse new advisor type from `ext_param`.
  - Map parsed object into `AiClientAdvisorVO.builder()`.

6. Ensure runtime wiring
- If enum factory now requires extra dependencies (for example model provider), update `AiClientAdvisorNode` accordingly.
- Keep `AiClientNode` advisor order stable (`Advisor::getOrder` sorting).

7. Provide DB configuration output
- Always output SQL for:
  - insert advisor row (`ai_client_advisor`)
  - bind advisor to client (`ai_client_config`)
  - verify query
- Reuse templates from `references/advisor-db-recipes.md`.

## Minimal Testing Policy
Default: do not add broad test suites for every advisor.

Add tests only when one of these is true:
- Advisor has non-trivial branching (sanitize/reject, fallback, rewrite behavior).
- ext_param parsing adds new nested structure with required fields.
- Advisor mutates request/response payload in a way that can silently regress.

Recommended minimum:
- 1 happy-path unit test
- 1 critical fail-path unit test

Skip when change is purely declarative:
- enum mapping only
- simple VO field passthrough
- SQL/output docs only

## Constraints
- Preserve existing node parsing/output contracts; do not change section markers consumed by downstream parsing logic.
- Do not revert unrelated workspace changes.
- Keep changes aligned with DDD module boundaries already used in this repo.

## Final Response Template
When finishing an advisor task, report in this order:
1. Changed files (absolute paths).
2. Runtime behavior summary (trigger, order, fail policy).
3. DB SQL snippet for insert + bind + verify.
4. Tests executed (or explicitly skipped with reason).

## References
- DB templates and JSON examples: `references/advisor-db-recipes.md`