package yhx.com.domain.agent.service.invocation;

import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionSpecVO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NodeFunctionSpecRegistry {

    public static NodeFunctionSpecRegistry defaultRegistry() {
        return new NodeFunctionSpecRegistry();
    }

    public List<NodeFunctionSpecVO> resolve(String componentCode) {
        if (AgentComponentCodeEnumVO.MAIN_AGENT.name().equals(componentCode)) {
            return mainAgentFunctions();
        }
        return List.of();
    }

    private List<NodeFunctionSpecVO> mainAgentFunctions() {
        return List.of(
                spec("main_final_answer", "Return a guarded final answer candidate.",
                        objectSchema(properties(
                                "content", stringProperty("User-facing answer content.")
                        ), required("content"))),
                spec("main_retrieve_rag", "Request Runtime-owned RAG retrieval.",
                        objectSchema(properties(
                                "query", stringProperty("Retrieval query."),
                                "topK", integerProperty("Maximum number of chunks to retrieve.")
                        ), required("query"))),
                spec("main_call_tool", "Declare a Runtime-owned MCP/tool execution action. This function does not execute tools.",
                        objectSchema(properties(
                                "capabilityCode", stringProperty("Runtime capability code."),
                                "toolName", stringProperty("Tool name inside the capability."),
                                "intent", stringProperty("Short reason for the tool call."),
                                "arguments", objectProperty("Tool arguments object.")
                        ), required("capabilityCode", "toolName", "intent", "arguments"))),
                spec("main_ask_user", "Ask the user through Runtime pending input.",
                        objectSchema(properties(
                                "question", stringProperty("Question shown to the user."),
                                "inputMode", stringProperty("Input mode such as SINGLE_CHOICE, SINGLE_CHOICE_OR_FREE_TEXT, FREE_TEXT, or CONFIRM."),
                                "allowFreeText", booleanProperty("Whether free text is allowed."),
                                "options", arrayProperty("Selectable options when the input mode requires them.")
                        ), required("question", "inputMode"))),
                spec("main_plan", "Persist MainAgent plan draft for later loop iterations.",
                        objectSchema(properties(
                                "steps", arrayProperty("Planned steps."),
                                "notes", stringProperty("Concise planning notes.")
                        ), required("steps"))),
                spec("main_continue", "Continue the loop with a next action hint.",
                        objectSchema(properties(
                                "reason", stringProperty("Reason another loop is needed.")
                        ), required("reason"))),
                spec("main_fail", "Return a user-safe failure candidate.",
                        objectSchema(properties(
                                "message", stringProperty("User-safe failure message."),
                                "failureCode", stringProperty("Machine-readable failure code when available.")
                        ), required("message")))
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
