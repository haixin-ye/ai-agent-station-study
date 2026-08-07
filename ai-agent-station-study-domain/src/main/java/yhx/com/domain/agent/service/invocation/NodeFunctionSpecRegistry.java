package yhx.com.domain.agent.service.invocation;

import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionSpecVO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NodeFunctionSpecRegistry {

    public static NodeFunctionSpecRegistry defaultRegistry() {
        return new NodeFunctionSpecRegistry();
    }

    public List<NodeFunctionSpecVO> resolve(String componentCode) {
        if (AgentComponentCodeEnumVO.MAIN_AGENT.name().equals(componentCode)) {
            return mainAgentFunctions();
        }
        if (AgentComponentCodeEnumVO.FINAL_REPAIR.name().equals(componentCode)) {
            return List.of(spec("repair_final_answer", "Return the repaired final answer candidate.",
                    objectSchema(properties(
                                    "content", stringProperty("Repaired user-facing answer content."),
                                    "format", stringProperty("Optional content format nested in finalAnswerCandidate.")),
                            required("content"))));
        }
        return List.of();
    }

    public List<NodeFunctionSpecVO> resolveMainAgent(MainAgentStageEnumVO stage) {
        return filterMainAgent(mainAgentFunctions(), stage);
    }

    public List<NodeFunctionSpecVO> filterMainAgent(List<NodeFunctionSpecVO> candidates, MainAgentStageEnumVO stage) {
        MainAgentStageEnumVO effectiveStage = stage == null ? MainAgentStageEnumVO.PLANNING : stage;
        Set<String> allowedNames = effectiveStage == MainAgentStageEnumVO.DELIVERING
                ? Set.of("main_final_answer", "main_fail")
                : Set.of("main_retrieve_rag", "main_call_tool", "main_ask_user",
                "main_delegate_agents", "main_ready_to_deliver", "main_fail");
        return candidates == null ? List.of() : candidates.stream()
                .filter(spec -> spec != null && allowedNames.contains(spec.getName()))
                .toList();
    }

    private List<NodeFunctionSpecVO> mainAgentFunctions() {
        return List.of(
                spec("main_final_answer", "Return a guarded final answer candidate.",
                        objectSchema(properties(
                                 "taskUpdate", objectProperty("TaskLedger update for this decision."),
                                 "content", stringProperty("User-facing answer content."),
                                 "format", stringProperty("Optional content format nested in finalAnswerCandidate.")
                         ), required("taskUpdate", "content"))),
                spec("main_retrieve_rag", "Request Runtime-owned RAG retrieval.",
                        objectSchema(properties(
                                "taskUpdate", objectProperty("TaskLedger update for this decision."),
                                "query", stringProperty("Retrieval query."),
                                "topK", integerProperty("Maximum number of chunks to retrieve.")
                        ), required("taskUpdate", "query"))),
                spec("main_call_tool", "Declare a Runtime-owned MCP/tool execution action. This function does not execute tools.",
                        objectSchema(properties(
                                "taskUpdate", objectProperty("TaskLedger update for this decision."),
                                "capabilityCode", stringProperty("Runtime capability code."),
                                "toolName", stringProperty("Tool name inside the capability."),
                                "goal", stringProperty("Short reason for the tool call."),
                                "arguments", objectProperty("Tool arguments object.")
                        ), required("taskUpdate", "capabilityCode", "toolName", "goal", "arguments"))),
                spec("main_ask_user", "Ask the user through Runtime pending input.",
                        objectSchema(properties(
                                "taskUpdate", objectProperty("TaskLedger update for this decision."),
                                "question", stringProperty("Question shown to the user."),
                                 "inputMode", stringProperty("Input mode such as SINGLE_CHOICE, SINGLE_CHOICE_OR_FREE_TEXT, FREE_TEXT, or CONFIRM."),
                                 "allowFreeText", booleanProperty("Whether free text is allowed."),
                                 "options", arrayProperty("Selectable options when the input mode requires them.")
                         ), required("taskUpdate", "question", "inputMode", "allowFreeText", "options"))),
                spec("main_delegate_agents", "Delegate bounded tasks through Runtime.",
                        objectSchema(properties(
                                 "taskUpdate", objectProperty("TaskLedger update for this decision."),
                                 "delegateAgentsRequest", delegateAgentsRequestProperty()),
                                 required("taskUpdate", "delegateAgentsRequest"))),
                spec("main_ready_to_deliver", "Request deterministic delivery-readiness validation.",
                        objectSchema(properties(
                                "taskUpdate", objectProperty("TaskLedger update completing every deliverable."),
                                "reason", stringProperty("Why all deliverables are ready.")),
                                required("taskUpdate", "reason"))),
                spec("main_fail", "Return a user-safe failure candidate.",
                        objectSchema(properties(
                                "taskUpdate", objectProperty("TaskLedger update for this decision."),
                                "userMessage", stringProperty("User-safe failure message."),
                                "failureCode", stringProperty("Machine-readable failure code when available.")
                        ), required("taskUpdate", "userMessage")))
        );
    }

    private NodeFunctionSpecVO spec(String name, String description, Map<String, Object> parameterSchema) {
        return NodeFunctionSpecVO.builder()
                .name(name)
                .description(description)
                .parameterSchema(parameterSchema)
                .strict(true)
                .build();
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", "object");
        value.put("properties", properties);
        value.put("required", required);
        value.put("additionalProperties", true);
        return value;
    }

    private Map<String, Object> properties(Object... keyValues) {
        Map<String, Object> value = new LinkedHashMap<>();
        if (keyValues == null) {
            return value;
        }
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            value.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return value;
    }

    private Map<String, Object> stringProperty(String description) {
        return property("string", description);
    }

    private Map<String, Object> integerProperty(String description) {
        return property("integer", description);
    }

    private Map<String, Object> booleanProperty(String description) {
        return property("boolean", description);
    }

    private Map<String, Object> objectProperty(String description) {
        return property("object", description);
    }

    private Map<String, Object> arrayProperty(String description) {
        return property("array", description);
    }

    private Map<String, Object> delegateAgentsRequestProperty() {
        Map<String, Object> taskSchema = objectSchema(properties(
                "taskId", stringProperty("Stable delegated task id."),
                "name", stringProperty("Short delegated worker name."),
                "objective", stringProperty("Atomic delegated objective."),
                "boundary", stringProperty("Optional scope boundary."),
                "requiredOutput", stringProperty("Exact required result shape."),
                "requestedCapabilities", stringArrayProperty("Non-empty Runtime permission codes including COMMIT."),
                "parentContext", objectProperty("Optional bounded parent context.")),
                required("taskId", "name", "objective", "requiredOutput", "requestedCapabilities"));
        Map<String, Object> tasks = arrayProperty("Non-empty delegated task list.");
        tasks.put("items", taskSchema);
        Map<String, Object> request = objectSchema(properties(
                "waitMode", stringProperty("WAIT_ALL"),
                "tasks", tasks), required("waitMode", "tasks"));
        request.put("description", "WAIT_ALL delegation request with capabilities on each tasks[i] item.");
        return request;
    }

    private Map<String, Object> stringArrayProperty(String description) {
        Map<String, Object> value = arrayProperty(description);
        value.put("items", property("string", "Runtime permission code."));
        value.put("minItems", 1);
        return value;
    }

    private Map<String, Object> property(String type, String description) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", type);
        value.put("description", description);
        return value;
    }

    private List<String> required(String... fields) {
        return List.of(fields);
    }
}
