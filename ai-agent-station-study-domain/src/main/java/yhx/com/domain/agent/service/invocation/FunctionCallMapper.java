package yhx.com.domain.agent.service.invocation;

import com.alibaba.fastjson.JSON;
import org.springframework.util.StringUtils;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeFunctionCallVO;

import java.util.LinkedHashMap;
import java.util.Map;

public class FunctionCallMapper {

    public static FunctionCallMapper defaultMapper() {
        return new FunctionCallMapper();
    }

    public String mapToRawOutput(String componentCode, NodeFunctionCallVO functionCall) {
        if (functionCall == null) {
            throw new IllegalArgumentException("Node function call is required.");
        }
        if (!StringUtils.hasText(functionCall.getName())) {
            throw new IllegalArgumentException("Node function call name is required.");
        }
        if (AgentComponentCodeEnumVO.MAIN_AGENT.name().equals(componentCode)) {
            return mapMainAgentFunction(functionCall);
        }
        if (AgentComponentCodeEnumVO.FINAL_REPAIR.name().equals(componentCode)) {
            return mapFinalRepairFunction(functionCall);
        }
        throw new IllegalArgumentException("Unsupported function-call component: " + componentCode);
    }

    private String mapMainAgentFunction(NodeFunctionCallVO functionCall) {
        Map<String, Object> args = functionCall.getArguments() == null
                ? Map.of()
                : functionCall.getArguments();
        return switch (functionCall.getName()) {
            case "main_final_answer" -> mainAction("FINAL", taskUpdate(args), "finalAnswerCandidate",
                    finalAnswerCandidate(args));
            case "main_retrieve_rag" -> mainAction("RETRIEVE_RAG", taskUpdate(args), "ragRequest", actionArgs(args));
            case "main_call_tool" -> mainAction("CALL_TOOL", taskUpdate(args), "toolIntent", actionArgs(args));
            case "main_ask_user" -> mainAction("ASK_USER", taskUpdate(args), "askUserRequest", actionArgs(args));
            case "main_delegate_agents" -> mainAction("DELEGATE_AGENTS", taskUpdate(args), "delegateAgentsRequest",
                    requireMap(args, "delegateAgentsRequest"));
            case "main_ready_to_deliver" -> mainAction("READY_TO_DELIVER", taskUpdate(args), "deliveryRequest",
                    mapOf("reason", requireText(args, "reason")));
            case "main_fail" -> mainAction("FAIL", taskUpdate(args), "failure", actionArgs(args));
            default -> throw new IllegalArgumentException("Unsupported MainAgent function: " + functionCall.getName());
        };
    }

    private String mapFinalRepairFunction(NodeFunctionCallVO functionCall) {
        if (!"repair_final_answer".equals(functionCall.getName())) {
            throw new IllegalArgumentException("Unsupported FinalRepair function: " + functionCall.getName());
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("action", "REPAIR_FINAL");
        root.put("stateDelta", Map.of("finalAnswerCandidate", finalAnswerCandidate(functionCall.getArguments())));
        return JSON.toJSONString(root);
    }

    private Map<String, Object> finalAnswerCandidate(Map<String, Object> args) {
        Map<String, Object> candidate = mapOf("content", requireText(args, "content"));
        Object format = args == null ? null : args.get("format");
        if (format != null && StringUtils.hasText(String.valueOf(format))) {
            candidate.put("format", String.valueOf(format));
        }
        return candidate;
    }

    private String mainAction(String action, Map<String, Object> taskUpdate,
                              String stateDeltaField, Object stateDeltaValue) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("taskUpdate", taskUpdate);
        root.put("action", action);
        Map<String, Object> stateDelta = new LinkedHashMap<>();
        stateDelta.put(stateDeltaField, stateDeltaValue);
        root.put("stateDelta", stateDelta);
        return JSON.toJSONString(root);
    }

    private Map<String, Object> taskUpdate(Map<String, Object> args) {
        return requireMap(args, "taskUpdate");
    }

    private Map<String, Object> actionArgs(Map<String, Object> args) {
        Map<String, Object> copy = copyArgs(args);
        copy.remove("taskUpdate");
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMap(Map<String, Object> args, String key) {
        Object value = args == null ? null : args.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Function argument object is required: " + key);
        }
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    private Map<String, Object> copyArgs(Map<String, Object> args) {
        return new LinkedHashMap<>(args);
    }

    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private String requireText(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            throw new IllegalArgumentException("Function argument is required: " + key);
        }
        return String.valueOf(value);
    }
}
