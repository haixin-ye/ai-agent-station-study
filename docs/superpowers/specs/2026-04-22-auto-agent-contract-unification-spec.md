# 2026-04-22 AutoAgent Contract Unification Spec

## Summary
This refactor unifies the execute chain around a shared contract pipeline while preserving current node responsibilities and external capabilities.

The migration scope is limited to Node1-4 and their prompt / harness / recovery behaviors.

## Goals
- keep existing planner / executor / supervisor / responder roles intact
- standardize prompt envelopes with contract metadata
- standardize recovery semantics with `lowConfidence`
- centralize node trace and contract versioning
- keep system prompts role-oriented instead of schema-oriented

## Implementation Rules

### Shared Contract Layer
- add a `domain`-level contract package for node metadata and prompt wrapping
- store contract version and truth-source metadata once per node
- record per-node trace into `DynamicContext`

### Node1
- keep JSON-first planning flow
- mark legacy / fallback / high-risk repaired plans as `lowConfidence`
- persist recovery metadata on `StepExecutionPlanVO`

### Node2
- keep evidence-first execution verification
- move runtime execution prompt into the shared prompt envelope format
- record contract trace after execution

### Node3
- keep hard-rule-first verification
- treat low-confidence semantic plans as conservative replan candidates
- record verification trace

### Node4
- keep accepted-results-first final response behavior
- move final summary prompt into the shared prompt envelope format

## Non-Goals
- no advisor-wide redesign in this refactor
- no large UI or SSE protocol rewrite
- no full rewrite of MySQL prompt architecture

## Verification
- targeted unit tests for Node1 recovery, Node2 prompt envelope, Node3 low-confidence guardrail
- minimal compile / test verification only
