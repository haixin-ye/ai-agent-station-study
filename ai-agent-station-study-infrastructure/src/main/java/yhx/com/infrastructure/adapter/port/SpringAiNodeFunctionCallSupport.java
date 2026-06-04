package yhx.com.infrastructure.adapter.port;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.util.StringUtils;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionCallVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionSpecVO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class SpringAiNodeFunctionCallSupport {

    private SpringAiNodeFunctionCallSupport() {
    }

    static List<OpenAiApi.FunctionTool> toOpenAiFunctionTools(List<NodeFunctionSpecVO> specs) {
        if (specs == null || specs.isEmpty()) {
            throw new IllegalArgumentException("FUNCTION_CALL mode requires at least one NodeFunctionSpec.");
        }
        return specs.stream()
                .map(SpringAiNodeFunctionCallSupport::toOpenAiFunctionTool)
                .toList();
    }

    static NodeFunctionCallVO extractRequiredFunctionCall(ChatResponse response) {
        AssistantMessage.ToolCall toolCall = firstToolCall(response);
        if (toolCall == null) {
            throw new IllegalStateException("FUNCTION_CALL_EXPECTED_BUT_MISSING: provider did not return a tool/function call.");
        }
        String argumentsJson = toolCall.arguments();
        return NodeFunctionCallVO.builder()
                .name(toolCall.name())
                .arguments(parseArguments(argumentsJson))
                .rawArgumentsJson(argumentsJson)
                .build();
    }

    static NodeFunctionCallVO extractRequiredFunctionCall(String rawResponseJson) {
        JSONObject message = firstMessage(rawResponseJson);
        JSONArray toolCalls = message == null ? null : message.getJSONArray("tool_calls");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            for (int i = 0; i < toolCalls.size(); i++) {
                JSONObject toolCall = toolCalls.getJSONObject(i);
                NodeFunctionCallVO nodeFunctionCall = toNodeFunctionCall(toolCall);
                if (nodeFunctionCall != null) {
                    return nodeFunctionCall;
                }
            }
        }
        JSONObject functionCall = message == null ? null : message.getJSONObject("function_call");
        if (functionCall != null && StringUtils.hasText(functionCall.getString("name"))) {
            String argumentsJson = functionCall.getString("arguments");
            return NodeFunctionCallVO.builder()
                    .name(functionCall.getString("name"))
                    .arguments(parseArguments(argumentsJson))
                    .rawArgumentsJson(argumentsJson)
                    .build();
        }
        throw new IllegalStateException("FUNCTION_CALL_EXPECTED_BUT_MISSING: provider did not return a tool/function call. preview="
                + normalizedPreview(rawResponseJson));
    }

    static String rawText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    static String rawText(String rawResponseJson) {
        JSONObject message = firstMessage(rawResponseJson);
        return message == null ? null : message.getString("content");
    }

    static List<Map<String, Object>> toOpenAiFunctionToolPayloads(List<NodeFunctionSpecVO> specs) {
        if (specs == null || specs.isEmpty()) {
            throw new IllegalArgumentException("FUNCTION_CALL mode requires at least one NodeFunctionSpec.");
        }
        return specs.stream()
                .map(SpringAiNodeFunctionCallSupport::toOpenAiFunctionToolPayload)
                .toList();
    }

    private static OpenAiApi.FunctionTool toOpenAiFunctionTool(NodeFunctionSpecVO spec) {
        if (spec == null || !StringUtils.hasText(spec.getName())) {
            throw new IllegalArgumentException("NodeFunctionSpec.name is required.");
        }
        Map<String, Object> schema = spec.getParameterSchema() == null
                ? Map.of("type", "object")
                : spec.getParameterSchema();
        OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function(
                spec.getDescription(),
                spec.getName(),
                schema,
                Boolean.TRUE.equals(spec.getStrict()));
        return new OpenAiApi.FunctionTool(function);
    }

    private static Map<String, Object> toOpenAiFunctionToolPayload(NodeFunctionSpecVO spec) {
        if (spec == null || !StringUtils.hasText(spec.getName())) {
            throw new IllegalArgumentException("NodeFunctionSpec.name is required.");
        }
        Map<String, Object> schema = spec.getParameterSchema() == null
                ? Map.of("type", "object")
                : spec.getParameterSchema();
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", spec.getName());
        function.put("description", spec.getDescription());
        function.put("parameters", schema);
        function.put("strict", Boolean.TRUE.equals(spec.getStrict()));
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private static AssistantMessage.ToolCall firstToolCall(ChatResponse response) {
        if (response == null) {
            return null;
        }
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null || !generation.getOutput().hasToolCalls()) {
            return null;
        }
        List<AssistantMessage.ToolCall> toolCalls = generation.getOutput().getToolCalls();
        return toolCalls == null || toolCalls.isEmpty() ? null : toolCalls.get(0);
    }

    private static Map<String, Object> parseArguments(String argumentsJson) {
        if (!StringUtils.hasText(argumentsJson)) {
            return Map.of();
        }
        JSONObject jsonObject = JSON.parseObject(argumentsJson);
        return new LinkedHashMap<>(jsonObject);
    }

    private static NodeFunctionCallVO toNodeFunctionCall(JSONObject toolCall) {
        JSONObject function = toolCall == null ? null : toolCall.getJSONObject("function");
        String name = function == null ? null : function.getString("name");
        String argumentsJson = function == null ? null : function.getString("arguments");
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return NodeFunctionCallVO.builder()
                .name(name)
                .arguments(parseArguments(argumentsJson))
                .rawArgumentsJson(argumentsJson)
                .build();
    }

    private static JSONObject firstMessage(String rawResponseJson) {
        if (!StringUtils.hasText(rawResponseJson)) {
            return null;
        }
        JSONObject root = JSON.parseObject(normalizeRawResponseJson(rawResponseJson));
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        JSONObject firstChoice = choices.getJSONObject(0);
        if (firstChoice == null) {
            return null;
        }
        JSONObject message = firstChoice.getJSONObject("message");
        return message == null ? firstChoice.getJSONObject("delta") : message;
    }

    private static String normalizeRawResponseJson(String rawResponse) {
        String trimmed = rawResponse == null ? "" : rawResponse.strip();
        if (trimmed.startsWith("\uFEFF")) {
            trimmed = trimmed.substring(1).strip();
        }
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        String dataPayload = extractDataPayload(trimmed);
        if (StringUtils.hasText(dataPayload)) {
            return dataPayload;
        }
        throw new IllegalStateException("PROVIDER_RESPONSE_NOT_JSON: response must be JSON or data-prefixed JSON. preview="
                + preview(trimmed));
    }

    private static String extractDataPayload(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            return null;
        }
        String dataBlock = rawResponse.lines()
                .map(String::trim)
                .filter(line -> !line.equals("data: [DONE]") && !line.equals("data:[DONE]"))
                .collect(Collectors.joining("\n"))
                .strip();
        if (dataBlock.startsWith("data:")) {
            String jsonBlock = firstJsonObjectBlock(dataBlock.substring("data:".length()).strip());
            if (StringUtils.hasText(jsonBlock)) {
                return jsonBlock;
            }
        }
        List<String> lines = rawResponse.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .filter(payload -> StringUtils.hasText(payload) && !"[DONE]".equals(payload))
                .collect(Collectors.toList());
        if (lines.isEmpty()) {
            return null;
        }
        String combined = String.join("\n", lines).strip();
        String jsonBlock = firstJsonObjectBlock(combined);
        if (StringUtils.hasText(jsonBlock)) {
            return jsonBlock;
        }
        return lines.stream()
                .filter(payload -> payload.startsWith("{"))
                .findFirst()
                .orElse(null);
    }

    private static String firstJsonObjectBlock(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return value.substring(start, end + 1).strip();
    }

    private static String preview(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 160 ? compact : compact.substring(0, 160);
    }

    private static String normalizedPreview(String rawResponse) {
        try {
            return preview(normalizeRawResponseJson(rawResponse));
        } catch (RuntimeException ignored) {
            return preview(rawResponse);
        }
    }
}
