package yhx.com.domain.agent.service.contract;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import yhx.com.domain.agent.model.valobj.enums.ContextLevelEnumVO;
import yhx.com.domain.agent.model.valobj.enums.ContextPlannerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.MainAgentActionTypeEnumVO;

import java.util.Map;

public class ContractValidator {

    private final RawOutputParser rawOutputParser;

    public ContractValidator(RawOutputParser rawOutputParser) {
        this.rawOutputParser = rawOutputParser;
    }

    public static ContractValidator defaultValidator() {
        return new ContractValidator(RawOutputParser.defaultParser());
    }

    public ContractValidationResult validateMainAgentAction(String rawOutput) {
        RawOutputParseResult parseResult = rawOutputParser.parse(rawOutput);
        if (!parseResult.isSuccess()) {
            return ContractValidationResult.failed(parseResult.getErrorCode(), "$", parseResult.getErrorMessage());
        }

        JSONObject root = parseResult.getJsonObject();
        for (String field : root.keySet()) {
            if (StateDeltaScopeRules.isRuntimeOwnedField(field)) {
                return ContractValidationResult.failed("FORBIDDEN_RUNTIME_FIELD", field, "Node output contains Runtime-owned field.");
            }
        }

        String action = root.getString("action");
        if (MainAgentActionTypeEnumVO.ofCode(action).isEmpty()) {
            return ContractValidationResult.failed("INVALID_ACTION", "action", "Unknown MainAgent action.");
        }

        JSONObject stateDelta = root.getJSONObject("stateDelta");
        if (stateDelta == null) {
            return ContractValidationResult.failed("MISSING_STATE_DELTA", "stateDelta", "MainAgent action must include stateDelta.");
        }
        for (String field : stateDelta.keySet()) {
            if (StateDeltaScopeRules.isRuntimeOwnedField(field)) {
                return ContractValidationResult.failed("FORBIDDEN_RUNTIME_FIELD", "stateDelta." + field, "StateDelta contains Runtime-owned field.");
            }
            if (!StateDeltaScopeRules.isAllowed(action, field)) {
                return ContractValidationResult.failed("STATE_DELTA_SCOPE_VIOLATION", "stateDelta." + field, "Field is not allowed for action " + action + ".");
            }
        }

        return ContractValidationResult.passed();
    }

    public ContractValidationResult validateContextPlannerOutput(String rawOutput) {
        RawOutputParseResult parseResult = rawOutputParser.parse(rawOutput);
        if (!parseResult.isSuccess()) {
            return ContractValidationResult.failed(parseResult.getErrorCode(), "$", parseResult.getErrorMessage());
        }

        JSONObject root = parseResult.getJsonObject();
        String status = root.getString("status");
        if (ContextPlannerStatusEnumVO.ofCode(status).isEmpty()) {
            return ContractValidationResult.failed("INVALID_CONTEXT_PLANNER_STATUS", "status", "Unknown ContextPlanner status.");
        }

        ContractValidationResult contextLevelResult = validateContextLevels(root);
        if (!contextLevelResult.isPassed()) {
            return contextLevelResult;
        }

        return ContractValidationResult.passed();
    }

    private ContractValidationResult validateContextLevels(JSONObject root) {
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof JSONArray array) {
                for (int index = 0; index < array.size(); index++) {
                    Object item = array.get(index);
                    if (item instanceof JSONObject itemObject) {
                        ContractValidationResult result = validateContextLevelField(entry.getKey() + "[" + index + "]", itemObject);
                        if (!result.isPassed()) {
                            return result;
                        }
                    }
                }
            }
        }
        return ContractValidationResult.passed();
    }

    private ContractValidationResult validateContextLevelField(String path, JSONObject itemObject) {
        String useLevel = itemObject.getString("useLevel");
        if (useLevel == null) {
            useLevel = itemObject.getString("contextLevel");
        }
        if (useLevel == null) {
            return ContractValidationResult.passed();
        }
        if (ContextLevelEnumVO.ofCode(useLevel).isEmpty()) {
            return ContractValidationResult.failed("INVALID_CONTEXT_LEVEL", path + ".useLevel", "Unknown context level.");
        }
        return ContractValidationResult.passed();
    }
}
