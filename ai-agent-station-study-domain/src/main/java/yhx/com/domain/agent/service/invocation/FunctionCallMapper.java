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
        if (AgentComponentCodeEnumVO.MAIN_AGENT.name().equals(componentCode)
                || AgentComponentCodeEnumVO.FINAL_REPAIR.name().equals(componentCode)) {
            return mapMainAgentFunction(functionCall);
        }
        throw new IllegalArgumentException("Unsupported function-call component: " + componentCode);
    }

    private String mapMainAgentFunction(NodeFunctionCallVO functionCall) {
        Map<String, Object> args = functionCall.getArguments() == null
                ? Map.of()
                : functionCall.getArguments();
        return switch (functionCall.getName()) {
            case "main_final_answer" -> mainAction("FINAL", "finalAnswerCandidate",
                    mapOf("content", requireText(args, "content")));
            case "main_retrieve_rag" -> mainAction("RETRIEVE_RAG", "ragRequest", copyArgs(args));
            case "main_call_tool" -> mainAction("CALL_TOOL", "toolIntent", copyArgs(args));
            case "main_ask_user" -> mainAction("ASK_USER", "askUserRequest", copyArgs(args));
            case "main_plan" -> mainAction("PLAN", "planDraft", copyArgs(args));
            case "main_continue" -> mainAction("CONTINUE", "nextActionHint", copyArgs(args));
            case "main_fail" -> mainAction("FAIL", "failure", copyArgs(args));
            default -> throw new IllegalArgumentException("Unsupported MainAgent function: " + functionCall.getName());
        };
    }

    private String mainAction(String action, String stateDeltaField, Object stateDeltaValue) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("action", action);
        Map<String, Object> stateDelta = new LinkedHashMap<>();
        stateDelta.put(stateDeltaField, stateDeltaValue);
        root.put("stateDelta", stateDelta);
        return JSON.toJSONString(root);
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
