package yhx.com.domain.agent.service.api;

import yhx.com.domain.agent.model.valobj.mock.AgentMockEventVO;
import yhx.com.domain.agent.model.valobj.mock.AgentMockScenarioVO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AgentMockScenarioService {

    public List<AgentMockScenarioVO> listScenarios() {
        return List.of(
                scenario("simple_final", "Simple final", "A short direct answer flow.", false),
                scenario("rag_progress", "RAG progress", "Knowledge retrieval progress followed by a final response.", false),
                scenario("tool_publish_progress", "Tool publish progress", "Tool approval and publishing progress.", false),
                scenario("ask_user_confirm", "Ask user confirm", "A high-risk confirmation pending input.", false),
                scenario("ask_user_choose_artifact", "Choose artifact", "A context clarification choice for existing artifacts.", false),
                scenario("artifact_created", "Artifact created", "Artifact creation progress and completion.", false),
                scenario("tool_failed", "Tool failed", "A tool failure surfaced as a safe frontend event.", false),
                scenario("final_guard_repair", "Final guard repair", "Final answer repair progress before completion.", false),
                scenario("context_over_budget", "Context over budget", "Context planning detects too much material.", false),
                scenario("debug_trace", "Debug trace", "Debug trace summaries for developer panels.", true),
                scenario("debug_event_stream", "Debug event stream", "Separate debug stream shape.", true)
        );
    }

    public String createMockRunId(String scenario) {
        return "mock-" + scenario + "-" + UUID.randomUUID();
    }

    public List<AgentMockEventVO> buildEvents(String scenario, String runId) {
        return switch (scenario) {
            case "rag_progress" -> List.of(
                    event(runId, 1L, "STATUS_CHANGED", "received", "Request received.", null, null),
                    event(runId, 2L, "STATUS_CHANGED", "rag_retrieving", "Searching knowledge base.", null, null),
                    event(runId, 3L, "FINAL_READY", "completed", "Final response is ready.", null, null)
            );
            case "tool_publish_progress" -> List.of(
                    event(runId, 1L, "ASK_USER", "asking_user", "Confirm publishing this article?", null, "pending-mock-approve"),
                    event(runId, 2L, "STATUS_CHANGED", "tool_calling", "Publishing through configured tool.", null, null),
                    event(runId, 3L, "FINAL_READY", "completed", "Published successfully.", null, null)
            );
            case "ask_user_confirm" -> List.of(
                    event(runId, 1L, "ASK_USER", "asking_user", "Confirm this operation?", null, "pending-mock-confirm")
            );
            case "ask_user_choose_artifact" -> List.of(
                    event(runId, 1L, "ASK_USER", "asking_user", "Which artifact should I use?", null, "pending-mock-artifact")
            );
            case "artifact_created" -> List.of(
                    event(runId, 1L, "STATUS_CHANGED", "composing", "Creating artifact.", null, null),
                    event(runId, 2L, "STATUS_CHANGED", "artifact_created", "Artifact created.", "artifact-mock-001", null)
            );
            case "tool_failed" -> List.of(
                    event(runId, 1L, "STATUS_CHANGED", "tool_calling", "Calling tool.", null, null),
                    event(runId, 2L, "RUN_FAILED", "failed", "Tool execution failed safely.", null, null)
            );
            case "final_guard_repair" -> List.of(
                    event(runId, 1L, "STATUS_CHANGED", "composing", "Composing response.", null, null),
                    event(runId, 2L, "STATUS_CHANGED", "composing", "Repairing final response.", null, null),
                    event(runId, 3L, "FINAL_READY", "completed", "Final response is ready.", null, null)
            );
            case "context_over_budget" -> List.of(
                    event(runId, 1L, "STATUS_CHANGED", "context_over_budget", "Context is too large; asking for a narrower choice.", null, null),
                    event(runId, 2L, "ASK_USER", "asking_user", "Please choose the material range.", null, "pending-mock-context")
            );
            case "debug_trace", "debug_event_stream" -> List.of(
                    event(runId, 1L, "TRACE", "phase_started", "Runtime phase started.", null, null),
                    event(runId, 2L, "TRACE", "node_invocation", "MainAgentNode invoked.", null, null)
            );
            default -> List.of(
                    event(runId, 1L, "STATUS_CHANGED", "received", "Request received.", null, null),
                    event(runId, 2L, "FINAL_READY", "completed", "Final response is ready.", null, null)
            );
        };
    }

    private AgentMockScenarioVO scenario(String scenario, String title, String description, boolean debugScenario) {
        return AgentMockScenarioVO.builder()
                .scenario(scenario)
                .title(title)
                .description(description)
                .debugScenario(debugScenario)
                .build();
    }

    private AgentMockEventVO event(String runId, Long seq, String eventType, String title, String message, String artifactId, String pendingId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("summary", message);
        payload.put("artifactId", artifactId);
        payload.put("pendingInputId", pendingId);
        return AgentMockEventVO.builder()
                .eventId("mock-event-" + seq)
                .runId(runId)
                .seq(seq)
                .eventType(eventType)
                .title(title)
                .message(message)
                .artifactId(artifactId)
                .pendingId(pendingId)
                .safePayload(payload)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

