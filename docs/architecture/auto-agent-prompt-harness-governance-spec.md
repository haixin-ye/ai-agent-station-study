# AutoAgent Prompt / Harness Governance Spec

## 1. Purpose
This spec defines the long-term development rules for AutoAgent prompt, harness, contract, and `DynamicContext` design.

It applies to:
- extending existing execute nodes
- adding new execute nodes
- evolving prompt / harness behaviors
- adding new structured state exchanged across nodes

This is a project-level spec, not a one-off refactor note.

## 2. DDD Layer Boundaries

### `domain`
- owns node workflow behavior
- owns node contract definitions
- owns output parsing and normalization rules
- owns recovery semantics such as `lowConfidence`
- owns `DynamicContext` state semantics and write-back rules

### `infrastructure`
- owns prompt persistence and repository access
- owns DAO / SQL / external configuration parsing
- must not own node decision logic

### `app`
- owns Spring bootstrapping and runtime assembly
- owns integration test wiring

### `docs`
- owns governance specs and refactor specs

## 3. Prompt Layering

### System Prompt
- stored in MySQL
- defines node role, responsibility, boundaries, and stable operating principles
- must not be the source of runtime schema truth

### Harness Prompt
- built in Java from node contract + `DynamicContext`
- defines runtime payload, output contract, and response rules
- must be rendered through the shared contract pipeline

### Output Contract
- defined in Java
- is the only source of truth for runtime fields, fallback semantics, and write-back behavior

## 4. Node Contract Rules
Every node must declare:
- node id
- contract version
- primary truth sources
- runtime prompt payload structure
- output parse mode
- recovery policy
- context write-back scope

Nodes may specialize behavior, but must not bypass the shared contract pipeline.

## 5. Recovery Rules

### Recovery Levels
- `FORMAT_NOISE`
- `STRUCTURE_RECOVERABLE`
- `SEMANTIC_UNCERTAIN`
- `EXECUTION_UNVERIFIED`
- `CONTRACT_VIOLATION`

### `lowConfidence`
- marks objects recovered through legacy, fallback, or high-risk repair paths
- prevents repaired output from being treated as normal truth by default
- must be preserved in downstream guardrail decisions

## 6. DynamicContext Write Ownership
- Node1 owns planning state such as `currentStepPlan`, `masterPlan`, `taskBoard`, `roundArchive`
- Node2 owns execution facts such as `executionOutcome`, `roundExecutionSummary`, `toolExecutionLog`
- Node3 owns acceptance and next-step state such as `acceptedResults`, `overallStatus`, `nextRoundDirective`
- Node4 owns final user-facing summary state

Nodes must not silently overwrite another node's core state.

## 7. Testing Rules
- every node change must cover one success-path contract case
- every node change must cover at least one high-risk recovery or guardrail case
- workflow changes must include a minimal but real verification command before merge

## 8. Prompt / DB Sync Rule
- MySQL system prompts and Java harness contracts must cooperate, not compete
- if Java output contract changes, verify MySQL prompt still matches role-only scope
- do not duplicate schema definitions across DB prompt and Java harness
