# AutoAgent Phase 10 API SSE Debug Mock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the new AutoAgent Runtime through clean chat APIs, mandatory SSE user-visible event streaming, pending-input APIs, artifact APIs, isolated debug APIs, and frontend mock scenarios.

**Architecture:** Normal frontend APIs read only user-visible messages, guarded final responses, user-visible events, pending input projections, and artifact APIs. Debug data is exposed only through explicit debug endpoints and separate debug SSE. Mock endpoints let frontend development test progress, waiting states, artifacts, final guard repair, and debug views without real LLM, RAG, or MCP calls.

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring MVC, `SseEmitter`, Maven multi-module project, Lombok, JUnit4/Spring Boot Test, DDD package layout under `yhx.com`.

---

## 0. Execution Rules

- Start this phase only after Phase 5 Runtime, Phase 9 final delivery, and repository boundaries compile.
- Do not expose raw prompt, raw model output, raw tool receipt, verifier detail, trace payload, or internal StateView through normal APIs.
- Do not share normal SSE and debug SSE endpoints.
- Debug APIs must be disabled or permission-protected by config.
- Mock mode must not call real LLM, RAG, MCP, or external services.
- Do not commit unless the user explicitly asks.

## 1. Source Of Truth

Use the English canonical spec only:

- `docs/architecture/auto-agent-main-loop-harness-redesign-spec.md`

Primary spec sections:

- Section 2.8: debug data boundary
- Section 3.7: pending input APIs
- Section 7.12: event/trace/audit persistence
- Section 10: frontend API and SSE
- Section 11: logging, trace, audit, observability
- Section 12.4: API and SSE tests
- Section 12.5: frontend mock scenarios
- Section 13.13: Phase 10 implementation tasks

## 2. Phase Boundary

### In Scope

- public chat API
- session message API
- run status and final response API
- normal SSE event stream
- event history API
- pending input read/answer API
- artifact summary/detail/version API
- debug trace/evidence/tool/payload API behind debug config
- separate debug SSE endpoint
- mock scenario API and mock SSE
- API DTOs and response wrappers
- API/SSE tests and mock scenario tests

### Out Of Scope

- frontend UI implementation
- authentication/authorization system beyond config/profile gates
- production-grade reconnect cursor storage beyond basic event seq
- old harness cleanup
- deployment changes

## 3. Controller File Map

Create under:

- `ai-agent-station-study-trigger/src/main/java/yhx/com/trigger/http/`

Required files:

- `AgentChatController.java`
- `AgentRunController.java`
- `AgentEventController.java`
- `AgentPendingInputController.java`
- `AgentArtifactController.java`
- `AgentDebugController.java`
- `AgentMockController.java`

Create DTOs under:

- `ai-agent-station-study-api/src/main/java/yhx/com/api/dto/agent/`

Required files:

- `AgentChatRequestDTO.java`
- `AgentChatResponseDTO.java`
- `AgentMessageDTO.java`
- `AgentRunDTO.java`
- `AgentFinalResponseDTO.java`
- `AgentUserVisibleEventDTO.java`
- `AgentPendingInputDTO.java`
- `AgentPendingOptionDTO.java`
- `AgentUserInputRequestDTO.java`
- `AgentUserInputResponseDTO.java`
- `AgentArtifactSummaryDTO.java`
- `AgentArtifactDetailDTO.java`
- `AgentArtifactVersionDTO.java`
- `AgentDebugTraceDTO.java`
- `AgentDebugPayloadDTO.java`
- `AgentMockScenarioDTO.java`

Create API service facade under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/api/`

Required files:

- `AgentRuntimeFacade.java`
- `AgentQueryFacade.java`
- `AgentDebugFacade.java`
- `AgentMockScenarioService.java`
- `SseEmitterRegistry.java`
- `SseUserEventBridge.java`
- `DebugSseEventBridge.java`

## 4. API Endpoints

### 4.1 Chat API

`POST /agent/chat`

Request:

```json
{
  "sessionId": "sess_001",
  "agentId": "default",
  "userId": "user_001",
  "content": "Generate a 200-word RAG interview answer.",
  "inputType": "TEXT"
}
```

Response:

```json
{
  "runId": "run_001",
  "sessionId": "sess_001",
  "userMessageId": "msg_001",
  "status": "RUNNING"
}
```

Rules:

- Calls `AutoAgentRuntimeService.start`.
- Returns immediately after run start or synchronous first state.
- Does not return trace/debug fields.

### 4.2 Message API

`GET /agent/sessions/{sessionId}/messages`

Rules:

- Returns user-visible messages only.
- Reads `agent_message`, not trace/transcript/payload debug tables.

### 4.3 Run API

`GET /agent/runs/{runId}`

Returns:

- `runId`
- `status`
- `currentPhase`
- `loopIndex`
- `startedAt`
- `completedAt`

`GET /agent/runs/{runId}/final`

Rules:

- Returns guarded `FinalResponse` only.
- If not completed, returns `finalAnswer=null`.
- Does not return guard detail or verifier detail.

### 4.4 Normal SSE API

`GET /agent/runs/{runId}/events/stream`

Rules:

- Uses `SseEmitter`.
- Streams only `AgentUserVisibleEventDTO`.
- Event order follows `seq`.
- Supports optional `Last-Event-ID` or `lastSeq` query later; MVP may replay from latest persisted event list before subscribing.
- Does not include raw payload refs except safe artifact refs and pending input id.

`GET /agent/runs/{runId}/events`

Rules:

- Returns historical user-visible events.
- This is fallback/history API, not primary realtime delivery.

### 4.5 Pending Input API

`GET /agent/runs/{runId}/pending-input`

Rules:

- Returns active pending input projection only.
- Includes `pendingId`, `question`, `inputMode`, `options`, `allowFreeText`, and `pendingType`.
- Does not expose continuation checkpoint payload.
- Does not expose source component internals except optional safe display type if needed.

`POST /agent/runs/{runId}/user-input`

Request:

```json
{
  "pendingId": "pending_001",
  "optionId": "approve",
  "freeText": null
}
```

Rules:

- Calls same-run resume path.
- Rejects free text for `SINGLE_CHOICE`.
- Rejects free text for high-risk `TOOL_APPROVAL`.
- Returns run status after resume.

### 4.6 Artifact API

`GET /agent/sessions/{sessionId}/artifacts`

Returns artifact summaries.

`GET /agent/artifacts/{artifactId}`

Returns artifact detail and content when allowed.

`GET /agent/artifacts/{artifactId}/versions`

Returns artifact version chain.

Rules:

- Summary API does not inline large content.
- Detail API may load content by artifact id and permissions.
- Debug payloads are never returned by artifact APIs.

### 4.7 Debug API

Debug endpoints:

```text
GET /agent/runs/{runId}/debug/traces
GET /agent/runs/{runId}/debug/evidence
GET /agent/runs/{runId}/debug/tool-calls
GET /agent/runs/{runId}/debug/payloads/{payloadId}
GET /agent/runs/{runId}/debug/events/stream
```

Rules:

- Require `auto-agent.debug.debug-api-enabled=true` or local dev profile.
- Debug SSE requires `auto-agent.debug.debug-sse-enabled=true`.
- Debug list endpoints return summaries and `payloadRef` by default.
- Raw payload endpoint applies preview size limits and redaction.
- Normal chat UI must not call debug endpoints.

### 4.8 Mock API

`GET /mock/agent/scenarios`

Returns scenario names and descriptions.

`POST /mock/agent/runs/{scenario}`

Creates a mock run id.

`GET /mock/agent/runs/{scenario}/events/stream`

Streams mock events.

Required scenarios:

- `simple_final`
- `rag_progress`
- `tool_publish_progress`
- `ask_user_confirm`
- `ask_user_choose_artifact`
- `artifact_created`
- `tool_failed`
- `final_guard_repair`
- `context_over_budget`
- `debug_trace`
- `debug_event_stream`

Rules:

- Mock mode does not call Runtime unless explicitly configured to use fake runtime.
- Mock events must match normal SSE DTO shape.
- Debug mock stream must match debug SSE DTO shape.

## 5. SSE Emitter Design

### 5.1 `SseEmitterRegistry`

Required methods:

```java
SseEmitter open(String streamKey, Long timeoutMs);
void send(String streamKey, String eventName, String eventId, Object payload);
void complete(String streamKey);
void completeWithError(String streamKey, Throwable error);
```

Rules:

- Registry stores emitters by run id and stream type.
- Remove emitter on completion, timeout, or error.
- Use separate stream keys for normal and debug:
  - `normal:{runId}`
  - `debug:{runId}`

### 5.2 `SseUserEventBridge`

Responsibilities:

- Subscribe to `RunEventPublisher` or repository event append callback.
- Convert domain `UserVisibleEventVO` to `AgentUserVisibleEventDTO`.
- Send event to `normal:{runId}`.

### 5.3 `DebugSseEventBridge`

Responsibilities:

- Stream developer trace summaries.
- Send only through `debug:{runId}`.
- Must never send debug payload to normal stream.

## 6. DTO Safety Rules

Normal DTOs may include:

- run id
- session id
- message id
- guarded final answer
- safe event summary
- artifact id/title/type/summary
- pending input question/options

Normal DTOs must not include:

- raw prompt
- raw model output
- raw tool receipt
- raw RAG chunk
- developer trace payload
- audit details
- StateView
- StateDelta
- contract validation result
- verifier detail
- guard detail

Debug DTOs may include:

- trace id
- runtime phase
- component name
- action type
- severity
- summary
- payloadRef
- token usage summaries

Debug DTOs should not inline raw payload by default.

## 7. DebugDataPipeline

Create under:

- `ai-agent-station-study-domain/src/main/java/yhx/com/domain/agent/service/debug/`

Required files:

- `DebugDataPipeline.java`
- `DebugPayloadPreviewPolicy.java`
- `DebugAccessPolicy.java`

Rules:

- Runtime and services write trace/audit/payload refs through this path.
- Normal APIs do not depend on `DebugDataPipeline`.
- Raw payload preview is disabled unless config enables it.
- Apply max preview chars from config.

## 8. Required Tests

Create under:

- `ai-agent-station-study-app/src/test/java/yhx/com/test/trigger/agent/`

Required test files:

- `AgentChatApiTest.java`
- `AgentRunApiTest.java`
- `AgentPendingInputApiTest.java`
- `AgentArtifactApiTest.java`
- `AgentSseEventApiTest.java`
- `AgentDebugApiBoundaryTest.java`
- `AgentMockScenarioApiTest.java`

### 8.1 Chat/Run Tests

Required cases:

1. `chat_starts_run_and_returns_run_id`
2. `messages_api_returns_user_visible_messages_only`
3. `run_status_returns_status_and_phase`
4. `final_api_returns_null_when_not_completed`
5. `final_api_returns_guarded_answer_when_completed`

### 8.2 Pending Input Tests

Required cases:

1. `pending_input_returns_question_options_and_input_mode`
2. `single_choice_rejects_free_text_submission`
3. `tool_approval_does_not_allow_free_text`
4. `user_input_submission_resumes_same_run`

### 8.3 SSE Tests

Required cases:

1. `normal_sse_streams_user_visible_events_in_order`
2. `normal_sse_does_not_include_trace_payload`
3. `event_history_returns_user_visible_events`
4. `debug_sse_uses_separate_endpoint`

### 8.4 Debug Boundary Tests

Required cases:

1. `debug_api_disabled_by_default`
2. `debug_trace_endpoint_returns_summary_and_payload_ref_only`
3. `debug_payload_preview_requires_debug_switch`
4. `normal_api_never_returns_debug_payload`

### 8.5 Mock Scenario Tests

Required cases:

1. `mock_scenarios_list_contains_required_scenarios`
2. `mock_simple_final_streams_final_event`
3. `mock_ask_user_confirm_streams_pending_input_event`
4. `mock_debug_event_stream_uses_debug_shape`

## 9. Execution Tasks

### Task 1: Add API DTOs

**Files:**

- Create DTOs listed in Section 3.

- [ ] Implement normal DTOs with safe fields only.
- [ ] Implement debug DTOs with payload refs, not raw payload by default.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-api -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 2: Add Facades And SSE Registry

**Files:**

- Create `AgentRuntimeFacade`, `AgentQueryFacade`, `AgentDebugFacade`, `SseEmitterRegistry`, `SseUserEventBridge`, `DebugSseEventBridge`.

- [ ] Wire facades to domain services/repositories.
- [ ] Keep debug queries separate.
- [ ] Implement emitter cleanup on timeout/error/completion.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-domain -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 3: Add Public Controllers

**Files:**

- `AgentChatController.java`
- `AgentRunController.java`
- `AgentEventController.java`
- `AgentPendingInputController.java`
- `AgentArtifactController.java`

- [ ] Implement endpoints from Sections 4.1-4.6.
- [ ] Use response wrapper style already used by the project.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-trigger -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 4: Add Debug API

**Files:**

- `AgentDebugController.java`
- `DebugDataPipeline.java`
- `DebugPayloadPreviewPolicy.java`
- `DebugAccessPolicy.java`

- [ ] Require debug config/profile.
- [ ] Implement trace/evidence/tool/payload/debug SSE endpoints.
- [ ] Keep debug SSE separate from normal SSE.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-trigger -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 5: Add Mock API

**Files:**

- `AgentMockController.java`
- `AgentMockScenarioService.java`

- [ ] Implement all required mock scenarios.
- [ ] Mock SSE emits DTO-compatible event sequences.
- [ ] Do not call real LLM/RAG/MCP.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-trigger -am -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

### Task 6: Add API/SSE Tests

**Files:**

- Create tests listed in Section 8.

- [ ] Use mock/fake runtime facade where needed.
- [ ] Verify normal/debug boundary.
- [ ] Run:

```powershell
mvn -q -pl ai-agent-station-study-app -am "-Dtest=AgentChatApiTest,AgentRunApiTest,AgentPendingInputApiTest,AgentArtifactApiTest,AgentSseEventApiTest,AgentDebugApiBoundaryTest,AgentMockScenarioApiTest" test
```

Expected result:

```text
BUILD SUCCESS
```

### Task 7: Cross-Spec Consistency Scan

- [ ] Run:

```powershell
rg -n "rawOutput|rawPrompt|toolReceipt|StateView|StateDelta|verifier|guardDetail|tracePayload" ai-agent-station-study-api ai-agent-station-study-trigger
```

Expected:

```text
No normal public DTO/controller exposes raw internal fields. Debug DTO/controller matches are allowed only under debug package/controller names.
```

- [ ] Run:

```powershell
rg -n "/debug/events/stream|/events/stream|SseEmitter" ai-agent-station-study-trigger ai-agent-station-study-domain
```

Expected:

```text
Normal SSE and debug SSE use separate endpoints and stream keys.
```

## 10. Acceptance Checklist

- [ ] Chat API starts a run.
- [ ] Message API returns user-visible messages only.
- [ ] Run API returns status and phase.
- [ ] Final API returns guarded final response only.
- [ ] Normal SSE streams user-visible events in order.
- [ ] Pending input API supports all input modes.
- [ ] `SINGLE_CHOICE` rejects free text.
- [ ] `TOOL_APPROVAL` rejects free text.
- [ ] Artifact summary and detail APIs are separate.
- [ ] Debug API is disabled or protected by default.
- [ ] Debug SSE is separate from normal SSE.
- [ ] Mock scenarios cover required frontend states.
- [ ] Normal DTOs expose no raw internals.
- [ ] Tests pass.

## 11. Worker Split Guidance

If using subagents, split work by non-overlapping file ownership:

- Worker A: API DTOs.
- Worker B: facades and SSE registry/bridges.
- Worker C: public chat/run/event/pending/artifact controllers.
- Worker D: debug API and debug data pipeline.
- Worker E: mock API and scenarios.
- Worker F: API/SSE tests.

The integrator must verify that normal frontend paths cannot read debug traces or raw payloads.

