# Auto Agent Plan-and-Execute Harness Redesign

## Context

The current auto-agent harness uses a four-node loop:

- `Node1 -> Node2 -> Node3 -> Node1 ... -> Node4`

This shape is acceptable and should be preserved. The instability comes from weak contracts between nodes, over-reliance on natural language summaries, and the absence of a trustworthy distinction between:

- planning
- execution
- verification
- final user delivery

The most visible failure mode is false success:

- `Node2` may fail to call MCP tools, call them incorrectly, or hallucinate a successful side effect.
- `Node3` may accept a natural-language claim instead of a verified execution fact.
- `Node4` may generate a polished answer that does not match real execution.

This redesign keeps the four-node topology but turns it into a `Plan-and-Execute with Verification and Final Composition` harness.

## Goals

- Preserve the existing four-node execution topology.
- Make `Node1` the only planner and dispatcher.
- Make `Node2` the only real executor.
- Make `Node3` the only acceptance gate for execution outputs.
- Make `Node4` produce the final answer only from accepted facts.
- Separate runtime capability assembly from business orchestration state.
- Replace free-form node-to-node text handoff with structured state objects in `DynamicContext`.
- Eliminate false completion when tool execution did not actually succeed.

## Non-Goals

- Do not redesign Spring AI client assembly itself.
- Do not move MCP/advisor wiring into business state.
- Do not add new runtime nodes to the execution topology.
- Do not make `Node3` directly route to `Node2`.

## Architectural Principle

The system has two distinct layers and they must not be mixed.

### Runtime Capability Layer

This is assembled through Spring AI and database-driven configuration:

- chat clients
- MCP tool attachments
- advisors
- RAG integration
- memory

These are environment capabilities. They are available to nodes at runtime, but they are not themselves business-state fields exchanged between nodes.

### Business Orchestration Layer

This is the actual harness state:

- session goals
- plan objects
- current round task
- execution records
- verification results
- accepted outputs
- overall completion state

This is what `DynamicContext` must carry.

## Target Execution Shape

The topology remains:

- `Node1 -> Node2 -> Node3 -> Node1 ... -> Node4`

The semantic responsibilities become:

- `Node1`: bootstrap planner and round planner
- `Node2`: executor
- `Node3`: verifier and next-round directive producer
- `Node4`: final composer

## Node Responsibilities

### Node1

`Node1` has two modes.

#### Mode A: Bootstrap Planner

Active only when the session starts and no `masterPlan` exists.

Responsibilities:

- understand the original user request
- determine whether the task is simple or complex
- build a `masterPlan`
- define step-level completion criteria
- produce the first `currentRound`

#### Mode B: Round Planner

Active on all later rounds.

Responsibilities:

- read the prior round verification result
- inspect `taskBoard`, `roundArchive`, `overallStatus`, and accepted outputs
- decide whether to continue the same main step or advance to the next one
- produce a new `currentRound`

Constraints:

- `Node1` is the only planner and dispatcher
- `Node1` does not claim execution truth
- `Node1` does not decide actual MCP argument payloads

### Node2

`Node2` is the only execution node.

Responsibilities:

- read the current round task assigned by `Node1`
- execute the round task
- decide whether to use MCP tools
- decide which MCP tool to use
- decide how to build MCP input payloads
- consume runtime capabilities such as MCP, advisors, RAG, and memory
- produce execution output and real tool execution records

Constraints:

- `Node2` only works on the current round task
- `Node2` is not a global planner
- `Node2` may reference the original user request and runtime injected context
- `Node2` natural-language claims are never authoritative facts by themselves

### Node3

`Node3` is the only verification and acceptance gate.

Responsibilities:

- inspect the round task from `Node1`
- inspect the execution output from `Node2`
- inspect the real tool execution records
- evaluate whether the current round task passed
- evaluate whether the current main step is complete
- evaluate whether the overall session goal is complete
- write accepted outputs into durable orchestration state
- emit a next-round directive for `Node1`

Constraints:

- `Node3` cannot directly route to `Node2`
- `Node3` only emits a directive that `Node1` reads on the next loop
- `Node3` is the only node allowed to promote candidate outputs into accepted results

### Node4

`Node4` is the final response node.

Responsibilities:

- read the original raw user request
- read the sanitized goal
- read accepted outputs
- read task completion state
- read overall completion state
- compose the final user-facing answer

Constraints:

- `Node4` may use the raw request to shape style and response framing
- `Node4` may use the sanitized goal to preserve task boundaries
- `Node4` may only use accepted outputs as factual truth
- `Node4` must not invent facts from unverified execution text

## DynamicContext Redesign

`DynamicContext` becomes a structured orchestration state carrier rather than a free-form string bag.

### 1. sessionGoal

Fields:

- `rawUserInput`
- `sanitizedGoal`
- `successCriteria`
- `maxRounds`
- `failurePolicy`

Purpose:

- immutable or mostly-stable session-level target definition

### 2. masterPlan

Fields:

- `planVersion`
- `mainSteps[]`

Each `mainStep` contains:

- `stepId`
- `title`
- `goal`
- `completionCriteria`
- `status`
- `dependencies[]`

Purpose:

- top-level plan created initially by `Node1`
- stable enough to anchor the session
- still allows bounded re-planning when needed

### 3. taskBoard

Task-centered view keyed by `stepId`.

Each entry contains:

- `status`
- `lastRoundTask`
- `acceptedOutputs[]`
- `lastFailureReason`
- `attemptCount`

Purpose:

- canonical work-progress board for main steps

### 4. currentRound

Fields:

- `roundIndex`
- `currentStepId`
- `roundTask`
- `suggestedTools[]`
- `plannerNotes`
- `expectedEvidence`

Purpose:

- the only valid round assignment consumed by `Node2`

### 5. roundArchive

Round-centered audit view.

Each round stores:

- `node1PlanSnapshot`
- `node2ExecutionSnapshot`
- `node3VerificationSnapshot`

Purpose:

- traceability
- debugging
- front-end timeline rendering

### 6. toolExecutionLog

Each record contains:

- `roundIndex`
- `stepId`
- `toolName`
- `requestPayload`
- `responsePayload`
- `normalizedOutcome`
- `success`
- `errorType`
- `errorMessage`
- `timestamp`

Purpose:

- stores real tool execution facts
- provides the evidence base for `Node3`

### 7. acceptedResults

Each record contains:

- `stepId`
- `resultType`
- `content`
- `evidenceRefs`
- `acceptedByRound`
- `acceptedReason`

Purpose:

- contains only outputs accepted by `Node3`
- serves as the fact source for `Node4`

### 8. overallStatus

Fields:

- `state`
- `completedSteps`
- `remainingSteps`
- `blockedReasons`
- `finalDecision`

Purpose:

- canonical overall session state

## Node Input and Output Contracts

### Node1 Contract

Reads:

- `sessionGoal`
- `masterPlan`
- `taskBoard`
- `roundArchive`
- `overallStatus`
- prior `Node3` verification directive

Writes:

- `masterPlan` in bootstrap mode
- `currentRound`
- planner snapshot into `roundArchive`

Output semantics:

- structured planning output
- not a tool-execution result
- not a task-truth claim

### Node2 Contract

Reads:

- `currentRound`
- `sessionGoal`
- runtime injected MCP/advisor/RAG capabilities

Writes:

- `node2ExecutionSnapshot`
- `toolExecutionLog`

Output semantics:

- execution narrative is a candidate explanation, not accepted truth
- tool records are mandatory whenever tools are actually called

### Node3 Contract

Reads:

- `currentRound`
- `node2ExecutionSnapshot`
- `toolExecutionLog`
- `masterPlan`
- `taskBoard`
- `sessionGoal`

Writes:

- `node3VerificationSnapshot`
- updates to `taskBoard`
- updates to `acceptedResults`
- updates to `overallStatus`
- `nextRoundDirective`

Output semantics:

- explicit round, step, and overall decisions
- accepted result promotion only after evidence verification

### Node4 Contract

Reads:

- `sessionGoal.rawUserInput`
- `sessionGoal.sanitizedGoal`
- `acceptedResults`
- `taskBoard`
- `overallStatus`

Writes:

- final response only

Output semantics:

- user-facing synthesis based on accepted truth only

## Routing Model

The physical node topology must remain:

- `Node1 -> Node2 -> Node3 -> Node1 ... -> Node4`

`Node3` never directly routes to `Node2`.

Instead, `Node3` emits one of these directives:

- `REPLAN_SAME_STEP`
- `ADVANCE_NEXT_STEP`
- `FINISH_SUCCESS`
- `FINISH_PARTIAL`
- `FINISH_FAILED`

Then `Node1` reads the directive in the next cycle and decides how to produce the next `currentRound`.

This preserves the existing router shape while making state transitions explicit.

## Tool Truth Model

Tool truth is split into three levels.

### toolIntent

What `Node2` intended to call.

This is not truth. It is only execution intent.

### toolExecutionRecord

What actually happened when a tool was called.

This is the primary evidence source for verification.

### acceptedResult

What `Node3` accepted after verification.

This is the only stable truth source for final response generation.

## Verification Rules

`Node3` verifies in this order:

1. Does the current round task require tool-backed evidence?
2. If yes, is there a corresponding tool execution record?
3. Did the tool execution succeed?
4. Does the result satisfy `expectedEvidence` and step completion criteria?
5. If yes, promote the result into `acceptedResults`
6. Otherwise emit a replan or continuation directive

This prevents false success where `Node2` merely claims a side effect.

## Error Taxonomy

These error classes should be modeled explicitly:

- `TOOL_NOT_CALLED`
- `TOOL_CALL_FAILED`
- `TOOL_OUTPUT_INVALID`
- `EVIDENCE_MISSING`
- `STEP_NOT_COMPLETE`
- `OVERALL_NOT_COMPLETE`

These should feed both verification decisions and front-end trace display.

## Prompt Strategy

### Node1 Prompt

Two modes:

- bootstrap planning mode
- round planning mode

Output should be structured enough for stable parsing, but must not over-constrain `Node2` by specifying exact MCP payloads.

Recommended fields:

- `planDecision`
- `currentStepId`
- `roundTask`
- `suggestedTools`
- `expectedEvidence`
- `replanReason`

### Node2 Prompt

Must be narrow:

- only work on the assigned `currentRound.roundTask`
- may use runtime-injected context
- decides tool usage itself

Recommended output layers:

- `executionNarrative`
- `candidateOutputs`
- `toolExecutionRecords`

### Node3 Prompt

Must be an acceptance prompt, not a generic quality-scoring prompt.

It should answer:

- did the round task pass?
- did the current step complete?
- did the overall goal complete?

Recommended fields:

- `roundDecision`
- `stepDecision`
- `overallDecision`
- `acceptedResultsDelta`
- `issues`
- `nextRoundDirective`

### Node4 Prompt

Must only consume:

- original user request
- sanitized goal
- accepted results
- board state
- overall status

Its role is presentation, not re-judgment.

## Front-End Implications

The current trace UI can continue to exist, but the semantic mapping changes.

The front end should render:

- bootstrap plan
- round plan
- executor narrative
- tool execution records
- verifier decision
- accepted result promotion
- final summary

This allows users to inspect where a failure happened without relying on free-form logs alone.

## Refactor Strategy

Recommended implementation order:

1. redesign `DynamicContext`
2. introduce explicit round and overall state objects
3. refactor `Node3` into the sole acceptance gate
4. refactor `Node1` into bootstrap planner plus round planner behavior
5. refactor `Node2` to emit candidate outputs and real execution logs
6. refactor `Node4` to read only accepted truth
7. update front-end trace mapping

Reason:

- verification must become correct before planning sophistication matters
- final composition must become safe before user-facing trust can improve

## Testing Strategy

### 1. Node Contract Tests

Verify the inputs and outputs of each node:

- `Node1` writes `masterPlan/currentRound`
- `Node2` consumes only `currentRound`
- `Node3` alone can write accepted results
- `Node4` only reads accepted truth and goals

### 2. State Transition Tests

Verify loop progression:

- bootstrap to first round
- same-step replan
- next-step advance
- successful completion
- partial completion
- failed completion

### 3. Tool Truth Tests

Verify anti-hallucination behavior:

- no tool record means no acceptance
- failed tool record means no acceptance
- successful tool record plus matching evidence enables acceptance

## Risks

- carrying legacy fields too long will weaken the redesign
- allowing `Node4` to read raw execution summaries will reintroduce hallucinated completion
- failing to explicitly distinguish accepted results from candidate results will preserve the current instability

## Decision Summary

The redesign will:

- preserve the four-node topology
- convert the harness into a plan-and-execute architecture with explicit verification
- make `Node1` the sole planner/dispatcher
- make `Node2` the sole executor
- make `Node3` the sole acceptance gate
- make `Node4` the sole final composer
- move truth from natural language summaries into structured verified state
