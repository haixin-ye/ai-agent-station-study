package yhx.com.domain.agent.service.contract;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentDelegationWaitModeEnumVO;
import yhx.com.domain.agent.model.valobj.contract.ContractValidationResult;
import yhx.com.domain.agent.model.valobj.contract.RawOutputParseResult;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextPlannerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.SubAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.SubAgentCommitStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.agent.AgentProfileTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.FinalRepairActionTypeEnumVO;

import java.util.List;
import java.util.Map;
import java.util.Set;

import yhx.com.domain.agent.service.agent.AgentProfileRegistry;

public class ContractValidator {

    private static final Set<String> MAIN_AGENT_V2_ACTIONS = Set.of(
            "RETRIEVE_RAG", "CALL_TOOL", "ASK_USER", "READY_TO_DELIVER", "DELEGATE_AGENTS", "FINAL", "FAIL");
    private static final Map<String, String> MAIN_AGENT_V2_PAYLOAD_FIELDS = Map.of(
            "RETRIEVE_RAG", "ragRequest",
            "CALL_TOOL", "toolIntent",
            "ASK_USER", "askUserRequest",
            "READY_TO_DELIVER", "deliveryRequest",
            "DELEGATE_AGENTS", "delegateAgentsRequest",
            "FINAL", "finalAnswerCandidate",
            "FAIL", "failure");
    private static final Set<String> GENERIC_SUB_AGENT_CAPABILITY_CODES = Set.copyOf(
            AgentProfileRegistry.defaultRegistry()
                    .requireProfile(AgentProfileTypeEnumVO.GENERIC_SUB_AGENT)
                    .getMaximumCapabilityCodes());

    private final RawOutputParser rawOutputParser;

    public ContractValidator(RawOutputParser rawOutputParser) {
        this.rawOutputParser = rawOutputParser;
    }

    public static ContractValidator defaultValidator() {
        return new ContractValidator(RawOutputParser.defaultParser());
    }

    public ContractValidationResult validateComponentOutput(String componentCode, String rawOutput) {
        AgentComponentCodeEnumVO component;
        try {
            component = AgentComponentCodeEnumVO.valueOf(componentCode);
        } catch (RuntimeException exception) {
            return ContractValidationResult.failed("UNKNOWN_COMPONENT", "componentCode",
                    "Unknown component contract: " + componentCode);
        }
        return switch (component) {
            case MAIN_AGENT -> validateMainAgentAction(rawOutput);
            case GENERIC_SUB_AGENT -> validateSubAgentAction(rawOutput);
            case FINAL_REPAIR -> validateFinalRepairAction(rawOutput);
            case CONTEXT_PLANNER -> validateContextPlannerOutput(rawOutput);
            case RAG_VERIFIER, TOOL_VERIFIER -> validateVerificationResult(rawOutput);
            case FINAL_RESPONSE_GUARD -> validateFinalResponseGuardResult(rawOutput);
            case TURN_SUMMARY -> validateTurnSummaryOutput(rawOutput);
            case MEMORY_EXTRACTOR -> validateMemoryExtractionOutput(rawOutput);
            case SESSION_TASK_SUMMARY -> validateSessionTaskSummaryOutput(rawOutput);
            case MEMORY_GOVERNANCE -> validateMemoryGovernanceOutput(rawOutput);
            case CONVERSATION_ROLLUP -> validateConversationRollupOutput(rawOutput);
            case RAG_ASSET_ANALYZER -> validateRagAssetAnalysisOutput(rawOutput);
            default -> ContractValidationResult.passed();
        };
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
        if (!MAIN_AGENT_V2_ACTIONS.contains(action)) {
            return ContractValidationResult.failed("INVALID_ACTION", "action", "Action is not part of MainAgent v2.");
        }

        ContractValidationResult taskUpdateResult = validateTaskUpdate(root.getJSONObject("taskUpdate"));
        if (!taskUpdateResult.isPassed()) {
            return taskUpdateResult;
        }
        if (MainAgentActionTypeEnumVO.READY_TO_DELIVER.code().equals(action)) {
            ContractValidationResult readinessResult = validateReadyToDeliverTaskUpdate(root.getJSONObject("taskUpdate"));
            if (!readinessResult.isPassed()) {
                return readinessResult;
            }
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

        ContractValidationResult payloadResult = validateMainAgentPayload(action, stateDelta);
        if (!payloadResult.isPassed()) {
            return payloadResult;
        }

        if (MainAgentActionTypeEnumVO.DELEGATE_AGENTS.code().equals(action)) {
            ContractValidationResult delegateResult = validateDelegateAgentsRequest(stateDelta.getJSONObject("delegateAgentsRequest"));
            if (!delegateResult.isPassed()) {
                return delegateResult;
            }
        }

        return ContractValidationResult.passed();
    }

    private ContractValidationResult validateDelegateAgentsRequest(JSONObject request) {
        if (request == null) {
            return ContractValidationResult.failed("MISSING_DELEGATE_REQUEST", "stateDelta.delegateAgentsRequest",
                    "DELEGATE_AGENTS requires delegateAgentsRequest.");
        }
        String waitMode = request.getString("waitMode");
        if (AgentDelegationWaitModeEnumVO.ofCode(waitMode).isEmpty()) {
            return ContractValidationResult.failed("INVALID_DELEGATE_WAIT_MODE", "stateDelta.delegateAgentsRequest.waitMode",
                    "Only WAIT_ALL is supported in the first delegation implementation.");
        }
        JSONArray tasks = request.getJSONArray("tasks");
        if (tasks == null || tasks.isEmpty()) {
            return ContractValidationResult.failed("MISSING_DELEGATE_TASKS", "stateDelta.delegateAgentsRequest.tasks",
                    "DELEGATE_AGENTS requires at least one task.");
        }
        for (int index = 0; index < tasks.size(); index++) {
            Object item = tasks.get(index);
            if (!(item instanceof JSONObject task)) {
                return ContractValidationResult.failed("INVALID_DELEGATE_TASK", "stateDelta.delegateAgentsRequest.tasks[" + index + "]",
                        "Delegated task must be a JSON object.");
            }
            if (isBlank(task.getString("taskId"))) {
                return ContractValidationResult.failed("MISSING_DELEGATE_TASK_ID", "stateDelta.delegateAgentsRequest.tasks[" + index + "].taskId",
                        "Delegated task requires taskId.");
            }
            if (isBlank(task.getString("name"))) {
                return ContractValidationResult.failed("MISSING_DELEGATE_TASK_NAME", "stateDelta.delegateAgentsRequest.tasks[" + index + "].name",
                        "Delegated task requires name.");
            }
            if (isBlank(task.getString("objective"))) {
                return ContractValidationResult.failed("MISSING_DELEGATE_TASK_OBJECTIVE", "stateDelta.delegateAgentsRequest.tasks[" + index + "].objective",
                        "Delegated task requires objective.");
            }
            if (isBlank(task.getString("requiredOutput"))) {
                return ContractValidationResult.failed("MISSING_DELEGATE_TASK_REQUIRED_OUTPUT", "stateDelta.delegateAgentsRequest.tasks[" + index + "].requiredOutput",
                        "Delegated task requires requiredOutput.");
            }
            JSONArray requestedCapabilities = task.getJSONArray("requestedCapabilities");
            if (requestedCapabilities == null || requestedCapabilities.isEmpty()) {
                return ContractValidationResult.failed("MISSING_DELEGATE_TASK_REQUESTED_CAPABILITIES", "stateDelta.delegateAgentsRequest.tasks[" + index + "].requestedCapabilities",
                        "Delegated task requires requestedCapabilities.");
            }
            boolean hasCommitCapability = false;
            for (int capabilityIndex = 0; capabilityIndex < requestedCapabilities.size(); capabilityIndex++) {
                Object capability = requestedCapabilities.get(capabilityIndex);
                if (!(capability instanceof String value) || value.isBlank()) {
                    return ContractValidationResult.failed("INVALID_DELEGATE_TASK_REQUESTED_CAPABILITY",
                            "stateDelta.delegateAgentsRequest.tasks[" + index + "].requestedCapabilities[" + capabilityIndex + "]",
                            "Delegated task requestedCapabilities must contain non-empty strings.");
                }
                if (!GENERIC_SUB_AGENT_CAPABILITY_CODES.contains(value)) {
                    return ContractValidationResult.failed("UNSUPPORTED_DELEGATE_TASK_REQUESTED_CAPABILITY",
                            "stateDelta.delegateAgentsRequest.tasks[" + index + "].requestedCapabilities[" + capabilityIndex + "]",
                            "Delegated task requestedCapabilities must use one of "
                                    + GENERIC_SUB_AGENT_CAPABILITY_CODES + ".");
                }
                hasCommitCapability = hasCommitCapability || "COMMIT".equals(value);
            }
            if (!hasCommitCapability) {
                return ContractValidationResult.failed("MISSING_DELEGATE_TASK_COMMIT_CAPABILITY",
                        "stateDelta.delegateAgentsRequest.tasks[" + index + "].requestedCapabilities",
                        "Delegated task requestedCapabilities must include COMMIT so the child can return its result.");
            }
        }
        return ContractValidationResult.passed();
    }

    public ContractValidationResult validateSubAgentAction(String rawOutput) {
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
        if (SubAgentActionTypeEnumVO.ofCode(action).isEmpty()) {
            return ContractValidationResult.failed("INVALID_SUB_AGENT_ACTION", "action", "Unknown SubAgent action.");
        }
        return switch (action) {
            case "COMMIT" -> validateSubAgentCommit(root.getJSONObject("commit"));
            case "CALL_TOOL" -> validateSubAgentToolInput(root.getJSONObject("actionInput"));
            case "RETRIEVE_RAG" -> requiredText(root.getJSONObject("actionInput"), "query", "actionInput.query");
            case "ASK_USER" -> validateSubAgentAskUserInput(root.getJSONObject("actionInput"));
            case "CONTINUE" -> requiredText(root.getJSONObject("actionInput"), "reason", "actionInput.reason");
            case "FAIL" -> validateSubAgentFailure(root.getJSONObject("actionInput"));
            default -> ContractValidationResult.passed();
        };
    }

    private ContractValidationResult validateSubAgentToolInput(JSONObject input) {
        if (input == null) {
            return ContractValidationResult.failed("MISSING_ACTION_INPUT", "actionInput",
                    "SubAgent CALL_TOOL requires actionInput.");
        }
        for (String field : List.of("capabilityCode", "toolName", "goal")) {
            ContractValidationResult result = requiredText(input, field, "actionInput." + field);
            if (!result.isPassed()) {
                return result;
            }
        }
        return input.getJSONObject("arguments") == null
                ? ContractValidationResult.failed("MISSING_TOOL_ARGUMENTS", "actionInput.arguments",
                "SubAgent CALL_TOOL requires an arguments object.")
                : ContractValidationResult.passed();
    }

    private ContractValidationResult validateSubAgentAskUserInput(JSONObject input) {
        if (input == null || input.getJSONObject("askUserRequest") == null) {
            return ContractValidationResult.failed("MISSING_ASK_USER_REQUEST", "actionInput.askUserRequest",
                    "SubAgent ASK_USER requires actionInput.askUserRequest.");
        }
        return validateAskUserRequest(input.getJSONObject("askUserRequest"), "actionInput.askUserRequest", false);
    }

    private ContractValidationResult validateSubAgentFailure(JSONObject input) {
        return input != null && hasAnyText(input, "message", "reason")
                ? ContractValidationResult.passed()
                : ContractValidationResult.failed("MISSING_FAILURE_MESSAGE", "actionInput",
                "SubAgent FAIL requires actionInput.message or actionInput.reason.");
    }

    private ContractValidationResult validateSubAgentCommit(JSONObject commit) {
        if (commit == null) {
            return ContractValidationResult.failed("MISSING_COMMIT", "commit", "SubAgent COMMIT requires commit payload.");
        }
        String taskId = commit.getString("taskId");
        if (taskId == null || taskId.isBlank()) {
            return ContractValidationResult.failed("MISSING_COMMIT_TASK_ID", "commit.taskId", "SubAgent COMMIT requires taskId.");
        }
        String status = commit.getString("status");
        if (status == null || status.isBlank()) {
            return ContractValidationResult.failed("MISSING_COMMIT_STATUS", "commit.status", "SubAgent COMMIT requires status.");
        }
        if (SubAgentCommitStatusEnumVO.ofCode(status).isEmpty()) {
            return ContractValidationResult.failed("INVALID_COMMIT_STATUS", "commit.status", "Unknown SubAgent commit status.");
        }
        String result = commit.getString("result");
        if (result == null || result.isBlank()) {
            return ContractValidationResult.failed("MISSING_COMMIT_RESULT", "commit.result", "SubAgent COMMIT requires result.");
        }
        return ContractValidationResult.passed();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ContractValidationResult validateTaskUpdate(JSONObject taskUpdate) {
        if (taskUpdate == null) {
            return ContractValidationResult.failed("MISSING_TASK_UPDATE", "taskUpdate", "MainAgent v2 requires taskUpdate.");
        }
        Set<String> validStepStatuses = Set.of("PENDING", "IN_PROGRESS", "COMPLETED", "FAILED", "BLOCKED", "CANCELLED");
        Set<String> validDeliverableStatuses = Set.of("PENDING", "IN_PROGRESS", "READY", "COMPLETED", "BLOCKED", "CANCELLED");
        ContractValidationResult stepResult = validateStatusArray(taskUpdate, "stepUpdates", "stepId", validStepStatuses);
        if (!stepResult.isPassed()) return stepResult;
        return validateStatusArray(taskUpdate, "deliverableUpdates", "deliverableId", validDeliverableStatuses);
    }

    private ContractValidationResult validateReadyToDeliverTaskUpdate(JSONObject taskUpdate) {
        if (taskUpdate == null) {
            return ContractValidationResult.failed("MISSING_TASK_UPDATE", "taskUpdate", "MainAgent v2 requires taskUpdate.");
        }
        JSONArray deliverables = taskUpdate.getJSONArray("deliverableUpdates");
        if (deliverables == null) {
            return ContractValidationResult.passed();
        }
        Set<String> readyStatuses = Set.of("READY", "COMPLETED", "CANCELLED");
        for (int index = 0; index < deliverables.size(); index++) {
            JSONObject deliverable = deliverables.getJSONObject(index);
            String status = deliverable == null ? null : deliverable.getString("status");
            if (!isBlank(status) && !readyStatuses.contains(status)) {
                return ContractValidationResult.failed(
                        "READY_TO_DELIVER_HAS_INCOMPLETE_DELIVERABLE",
                        "taskUpdate.deliverableUpdates[" + index + "].status",
                        "READY_TO_DELIVER cannot mark a deliverable as " + status + ".");
            }
        }
        return ContractValidationResult.passed();
    }

    private ContractValidationResult validateStatusArray(JSONObject update, String field, String idField, Set<String> validStatuses) {
        JSONArray values = update.getJSONArray(field);
        if (values == null) return ContractValidationResult.passed();
        for (int index = 0; index < values.size(); index++) {
            JSONObject value = values.getJSONObject(index);
            if (value == null || isBlank(value.getString(idField))) {
                return ContractValidationResult.failed("MISSING_TASK_ITEM_ID", "taskUpdate." + field + "[" + index + "]." + idField,
                        idField + " is required.");
            }
            String status = value.getString("status");
            if (!isBlank(status) && !validStatuses.contains(status)) {
                return ContractValidationResult.failed("INVALID_TASK_STATUS", "taskUpdate." + field + "[" + index + "].status",
                        "Status must be one of " + validStatuses + ".");
            }
        }
        return ContractValidationResult.passed();
    }

    private ContractValidationResult validateMainAgentPayload(String action, JSONObject stateDelta) {
        String field = MAIN_AGENT_V2_PAYLOAD_FIELDS.get(action);
        JSONObject payload = field == null ? null : stateDelta.getJSONObject(field);
        if (payload == null) {
            return ContractValidationResult.failed("MISSING_ACTION_PAYLOAD", "stateDelta." + field,
                    action + " requires stateDelta." + field + ".");
        }
        return switch (action) {
            case "RETRIEVE_RAG" -> requiredText(payload, "query", "stateDelta.ragRequest.query");
            case "CALL_TOOL" -> validateToolIntent(payload);
            case "ASK_USER" -> validateAskUserRequest(payload, "stateDelta.askUserRequest", true);
            case "READY_TO_DELIVER" -> requiredText(payload, "reason", "stateDelta.deliveryRequest.reason");
            case "FINAL" -> hasAnyText(payload, "content", "contentRef")
                    ? ContractValidationResult.passed()
                    : ContractValidationResult.failed("MISSING_FINAL_CONTENT", "stateDelta.finalAnswerCandidate",
                    "FINAL requires content or contentRef.");
            case "FAIL" -> hasAnyText(payload, "userMessage", "message")
                    ? ContractValidationResult.passed()
                    : ContractValidationResult.failed("MISSING_FAILURE_MESSAGE", "stateDelta.failure",
                    "FAIL requires userMessage or message.");
            default -> ContractValidationResult.passed();
        };
    }

    private ContractValidationResult validateToolIntent(JSONObject intent) {
        for (String field : List.of("capabilityCode", "toolName", "goal")) {
            ContractValidationResult result = requiredText(intent, field, "stateDelta.toolIntent." + field);
            if (!result.isPassed()) {
                return result;
            }
        }
        if (intent.getJSONObject("arguments") == null) {
            return ContractValidationResult.failed("MISSING_TOOL_ARGUMENTS", "stateDelta.toolIntent.arguments",
                    "CALL_TOOL requires an arguments object.");
        }
        return ContractValidationResult.passed();
    }

    private ContractValidationResult requiredText(JSONObject object, String field, String path) {
        return object == null || isBlank(object.getString(field))
                ? ContractValidationResult.failed("MISSING_REQUIRED_FIELD", path, path + " is required.")
                : ContractValidationResult.passed();
    }

    private boolean hasAnyText(JSONObject object, String... fields) {
        for (String field : fields) {
            if (!isBlank(object.getString(field))) {
                return true;
            }
        }
        return false;
    }

    public ContractValidationResult validateFinalRepairAction(String rawOutput) {
        RawOutputParseResult parseResult = rawOutputParser.parse(rawOutput);
        if (!parseResult.isSuccess()) {
            return ContractValidationResult.failed(parseResult.getErrorCode(), "$", parseResult.getErrorMessage());
        }
        JSONObject root = parseResult.getJsonObject();
        if (!FinalRepairActionTypeEnumVO.REPAIR_FINAL.code().equals(root.getString("action"))) {
            return ContractValidationResult.failed("INVALID_FINAL_REPAIR_ACTION", "action",
                    "FinalRepair must return REPAIR_FINAL.");
        }
        JSONObject stateDelta = root.getJSONObject("stateDelta");
        JSONObject candidate = stateDelta == null ? null : stateDelta.getJSONObject("finalAnswerCandidate");
        if (candidate == null) {
            return ContractValidationResult.failed("MISSING_FINAL_ANSWER_CANDIDATE",
                    "stateDelta.finalAnswerCandidate", "FinalRepair requires finalAnswerCandidate.");
        }
        return hasAnyText(candidate, "content", "contentRef")
                ? ContractValidationResult.passed()
                : ContractValidationResult.failed("MISSING_FINAL_CONTENT", "stateDelta.finalAnswerCandidate",
                "FinalRepair requires content or contentRef.");
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

        JSONArray selectedContext = root.getJSONArray("selectedContext");
        if (selectedContext == null) {
            return ContractValidationResult.failed("MISSING_SELECTED_CONTEXT", "selectedContext",
                    "ContextPlanner requires selectedContext array.");
        }
        if (ContextPlannerStatusEnumVO.READY.code().equals(status) && selectedContext.isEmpty()) {
            return ContractValidationResult.failed("EMPTY_READY_CONTEXT", "selectedContext",
                    "READY requires at least one selected context item.");
        }
        if ((ContextPlannerStatusEnumVO.NO_RELEVANT_CONTEXT.code().equals(status)
                || ContextPlannerStatusEnumVO.NEEDS_USER_CLARIFICATION.code().equals(status))
                && !selectedContext.isEmpty()) {
            return ContractValidationResult.failed("UNEXPECTED_SELECTED_CONTEXT", "selectedContext",
                    status + " requires an empty selectedContext array.");
        }
        if (ContextPlannerStatusEnumVO.NEEDS_USER_CLARIFICATION.code().equals(status)) {
            ContractValidationResult clarification = validateAskUserRequest(root.getJSONObject("clarificationRequest"),
                    "clarificationRequest", false);
            if (!clarification.isPassed()) {
                return clarification;
            }
        }
        if ((ContextPlannerStatusEnumVO.CONTEXT_OVER_BUDGET.code().equals(status)
                || ContextPlannerStatusEnumVO.FAILED.code().equals(status))) {
            ContractValidationResult reason = requiredText(root, "reason", "reason");
            if (!reason.isPassed()) {
                return reason;
            }
        }

        ContractValidationResult contextLevelResult = validateContextSelections(selectedContext);
        if (!contextLevelResult.isPassed()) {
            return contextLevelResult;
        }

        return ContractValidationResult.passed();
    }

    public ContractValidationResult validateVerificationResult(String rawOutput) {
        RawOutputParseResult parseResult = rawOutputParser.parse(rawOutput);
        if (!parseResult.isSuccess()) {
            return ContractValidationResult.failed(parseResult.getErrorCode(), "$", parseResult.getErrorMessage());
        }
        JSONObject root = parseResult.getJsonObject();
        String status = root.getString("status");
        if (!"PASSED".equals(status) && !"FAILED".equals(status) && !"SKIPPED".equals(status)) {
            return ContractValidationResult.failed("INVALID_VERIFICATION_STATUS", "status", "Verification status must be PASSED, FAILED, or SKIPPED.");
        }
        if (!root.containsKey("failureCode")) {
            return ContractValidationResult.failed("MISSING_FAILURE_CODE_FIELD", "failureCode",
                    "Verification output requires failureCode, using null when there is no failure.");
        }
        if ("FAILED".equals(status)) {
            String failureCode = root.getString("failureCode");
            if (failureCode == null || failureCode.isBlank()) {
                return ContractValidationResult.failed("MISSING_FAILURE_CODE", "failureCode", "Failed verification requires failureCode.");
            }
        } else if (root.get("failureCode") != null) {
            return ContractValidationResult.failed("UNEXPECTED_FAILURE_CODE", "failureCode",
                    "Passed or skipped verification requires failureCode=null.");
        }
        return requiredText(root, "detail", "detail");
    }

    public ContractValidationResult validateFinalResponseGuardResult(String rawOutput) {
        RawOutputParseResult parseResult = rawOutputParser.parse(rawOutput);
        if (!parseResult.isSuccess()) {
            return ContractValidationResult.failed(parseResult.getErrorCode(), "$", parseResult.getErrorMessage());
        }
        JSONObject root = parseResult.getJsonObject();
        String status = root.getString("status");
        if (!"PASSED".equals(status) && !"FAILED".equals(status)) {
            return ContractValidationResult.failed("INVALID_FINAL_GUARD_STATUS", "status",
                    "Final response guard status must be PASSED or FAILED.");
        }
        if (!root.containsKey("failureCode")) {
            return ContractValidationResult.failed("MISSING_FAILURE_CODE_FIELD", "failureCode",
                    "Final response guard requires failureCode, using null when passed.");
        }
        ContractValidationResult detail = requiredText(root, "detail", "detail");
        if (!detail.isPassed()) {
            return detail;
        }
        if ("PASSED".equals(status)) {
            if (root.get("failureCode") != null) {
                return ContractValidationResult.failed("UNEXPECTED_FAILURE_CODE", "failureCode",
                        "Passed final response guard requires failureCode=null.");
            }
            return requiredText(root, "finalContent", "finalContent");
        }
        return requiredText(root, "failureCode", "failureCode");
    }

    public ContractValidationResult validateTurnSummaryOutput(String rawOutput) {
        JSONObject root = parsedObject(rawOutput);
        if (root == null) {
            return parseFailure(rawOutput);
        }
        for (String field : List.of("summary", "intent")) {
            ContractValidationResult result = requiredText(root, field, field);
            if (!result.isPassed()) return result;
        }
        for (String field : List.of("topics", "artifactRefs")) {
            ContractValidationResult result = validateStringArray(root, field, field);
            if (!result.isPassed()) return result;
        }
        JSONArray entities = root.getJSONArray("entities");
        if (entities == null) {
            return ContractValidationResult.failed("MISSING_ARRAY_FIELD", "entities", "entities must be an array.");
        }
        for (int index = 0; index < entities.size(); index++) {
            if (!(entities.get(index) instanceof JSONObject)) {
                return ContractValidationResult.failed("INVALID_ARRAY_ITEM", "entities[" + index + "]",
                        "entities must contain objects.");
            }
        }
        ContractValidationResult score = validateScore(root, "importanceScore", "importanceScore");
        if (!score.isPassed()) return score;
        return root.get("requiresLongTermExtraction") instanceof Boolean
                ? ContractValidationResult.passed()
                : ContractValidationResult.failed("INVALID_BOOLEAN_FIELD", "requiresLongTermExtraction",
                "requiresLongTermExtraction must be boolean.");
    }

    public ContractValidationResult validateMemoryExtractionOutput(String rawOutput) {
        JSONObject root = parsedObject(rawOutput);
        if (root == null) return parseFailure(rawOutput);
        JSONArray memories = root.getJSONArray("memories");
        if (memories == null) {
            return ContractValidationResult.failed("MISSING_ARRAY_FIELD", "memories", "memories must be an array.");
        }
        for (int index = 0; index < memories.size(); index++) {
            JSONObject memory = memories.getJSONObject(index);
            String path = "memories[" + index + "]";
            if (memory == null) {
                return ContractValidationResult.failed("INVALID_MEMORY_ITEM", path, "Memory item must be an object.");
            }
            String memoryType = memory.getString("memoryType");
            if (!Set.of("LONG_TERM_MEMORY", "USER_PREFERENCE").contains(memoryType)) {
                return ContractValidationResult.failed("INVALID_MEMORY_TYPE", path + ".memoryType",
                        "memoryType must be LONG_TERM_MEMORY or USER_PREFERENCE.");
            }
            for (String field : List.of("summary", "content", "recallText", "reason")) {
                ContractValidationResult result = requiredText(memory, field, path + "." + field);
                if (!result.isPassed()) return result;
            }
            ContractValidationResult score = validateScore(memory, "score", path + ".score");
            if (!score.isPassed()) return score;
        }
        return ContractValidationResult.passed();
    }

    public ContractValidationResult validateSessionTaskSummaryOutput(String rawOutput) {
        JSONObject root = parsedObject(rawOutput);
        if (root == null) return parseFailure(rawOutput);
        if (!(root.get("shouldUpdate") instanceof Boolean)) {
            return ContractValidationResult.failed("INVALID_BOOLEAN_FIELD", "shouldUpdate", "shouldUpdate must be boolean.");
        }
        for (String field : List.of("mainTasks", "importantDecisions", "latestProgress", "openQuestions", "obsoleteTasks")) {
            ContractValidationResult result = validateStringArray(root, field, field);
            if (!result.isPassed()) return result;
        }
        if (!root.containsKey("currentTask") || (root.get("currentTask") != null && !(root.get("currentTask") instanceof String))) {
            return ContractValidationResult.failed("INVALID_CURRENT_TASK", "currentTask", "currentTask must be a string or null.");
        }
        return ContractValidationResult.passed();
    }

    public ContractValidationResult validateMemoryGovernanceOutput(String rawOutput) {
        JSONObject root = parsedObject(rawOutput);
        if (root == null) return parseFailure(rawOutput);
        JSONArray actions = root.getJSONArray("actions");
        if (actions == null) {
            return ContractValidationResult.failed("MISSING_ARRAY_FIELD", "actions", "actions must be an array.");
        }
        for (int index = 0; index < actions.size(); index++) {
            JSONObject action = actions.getJSONObject(index);
            String path = "actions[" + index + "]";
            if (action == null) {
                return ContractValidationResult.failed("INVALID_GOVERNANCE_ACTION", path, "Governance action must be an object.");
            }
            String actionCode = action.getString("action");
            if (!Set.of("KEEP", "DISABLE", "SUPERSEDE", "NOOP").contains(actionCode)) {
                return ContractValidationResult.failed("INVALID_GOVERNANCE_ACTION", path + ".action",
                        "action must be KEEP, DISABLE, SUPERSEDE, or NOOP.");
            }
            for (String field : List.of("memoryId", "reason")) {
                ContractValidationResult result = requiredText(action, field, path + "." + field);
                if (!result.isPassed()) return result;
            }
            if ("SUPERSEDE".equals(actionCode)) {
                ContractValidationResult target = requiredText(action, "targetMemoryId", path + ".targetMemoryId");
                if (!target.isPassed()) return target;
            }
        }
        return ContractValidationResult.passed();
    }

    public ContractValidationResult validateConversationRollupOutput(String rawOutput) {
        JSONObject root = parsedObject(rawOutput);
        return root == null ? parseFailure(rawOutput) : requiredText(root, "summary", "summary");
    }

    public ContractValidationResult validateRagAssetAnalysisOutput(String rawOutput) {
        JSONObject root = parsedObject(rawOutput);
        if (root == null) return parseFailure(rawOutput);
        for (String field : List.of("title", "summary", "retrievalText", "language")) {
            ContractValidationResult result = requiredText(root, field, field);
            if (!result.isPassed()) return result;
        }
        return validateStringArray(root, "keySymbols", "keySymbols");
    }

    private ContractValidationResult validateContextSelections(JSONArray selections) {
        for (int index = 0; index < selections.size(); index++) {
            JSONObject selection = selections.getJSONObject(index);
            String path = "selectedContext[" + index + "]";
            if (selection == null) {
                return ContractValidationResult.failed("INVALID_CONTEXT_SELECTION", path,
                        "Selected context item must be an object.");
            }
            for (String field : List.of("sourceType", "sourceId", "useLevel", "reason")) {
                ContractValidationResult result = requiredText(selection, field, path + "." + field);
                if (!result.isPassed()) return result;
            }
            String sourceType = selection.getString("sourceType");
            String useLevel = selection.getString("useLevel");
            Set<String> allowedLevels = allowedContextLevels(sourceType);
            if (allowedLevels.isEmpty()) {
                return ContractValidationResult.failed("INVALID_CONTEXT_SOURCE_TYPE", path + ".sourceType",
                        "Unknown context source type.");
            }
            if (ContextLevelEnumVO.ofCode(useLevel).isEmpty() || !allowedLevels.contains(useLevel)) {
                return ContractValidationResult.failed("INVALID_CONTEXT_LEVEL", path + ".useLevel",
                        "Context level " + useLevel + " is not valid for " + sourceType + ".");
            }
        }
        return ContractValidationResult.passed();
    }

    private Set<String> allowedContextLevels(String sourceType) {
        if ("SESSION_SUMMARY".equals(sourceType)) {
            return Set.of("SUMMARY_ONLY", "SUMMARY_PLUS_SNIPPET", "FULL_TEXT");
        }
        if ("MEMORY".equals(sourceType) || "ARTIFACT".equals(sourceType)) {
            return Set.of("METADATA_ONLY", "SUMMARY_ONLY", "SUMMARY_PLUS_SNIPPET", "FULL_TEXT");
        }
        if ("EVIDENCE".equals(sourceType)) {
            return Set.of("SUMMARY_ONLY", "SUMMARY_PLUS_SNIPPET", "FULL_TEXT");
        }
        if ("RAG_FILE_CHUNK".equals(sourceType) || "RAG_CODE_CHUNK".equals(sourceType)) {
            return Set.of("CHUNKED_CONTEXT");
        }
        if ("RAG_CODE_FILE_SUMMARY".equals(sourceType)) {
            return Set.of("SUMMARY_ONLY", "FULL_TEXT");
        }
        if ("ARTIFACT_CHUNK".equals(sourceType)) {
            return Set.of("SUMMARY_PLUS_SNIPPET", "CHUNKED_CONTEXT");
        }
        return Set.of();
    }

    private ContractValidationResult validateAskUserRequest(JSONObject request, String path, boolean allowConfirm) {
        if (request == null) {
            return ContractValidationResult.failed("MISSING_ASK_USER_REQUEST", path, "Ask-user request is required.");
        }
        ContractValidationResult question = requiredText(request, "question", path + ".question");
        if (!question.isPassed()) return question;
        String inputMode = request.getString("inputMode");
        Set<String> modes = allowConfirm
                ? Set.of("SINGLE_CHOICE", "SINGLE_CHOICE_OR_FREE_TEXT", "FREE_TEXT", "CONFIRM")
                : Set.of("SINGLE_CHOICE", "SINGLE_CHOICE_OR_FREE_TEXT", "FREE_TEXT");
        if (!modes.contains(inputMode)) {
            return ContractValidationResult.failed("INVALID_INPUT_MODE", path + ".inputMode",
                    "Unsupported ask-user input mode.");
        }
        if (!(request.get("allowFreeText") instanceof Boolean allowFreeText)) {
            return ContractValidationResult.failed("INVALID_ALLOW_FREE_TEXT", path + ".allowFreeText",
                    "allowFreeText must be boolean.");
        }
        JSONArray options = request.getJSONArray("options");
        if (options == null) {
            return ContractValidationResult.failed("MISSING_OPTIONS", path + ".options", "options must be an array.");
        }
        boolean freeTextOnly = "FREE_TEXT".equals(inputMode);
        boolean freeTextWithOptions = "SINGLE_CHOICE_OR_FREE_TEXT".equals(inputMode);
        if (freeTextOnly && (!allowFreeText || !options.isEmpty())) {
            return ContractValidationResult.failed("INVALID_FREE_TEXT_CONFIGURATION", path,
                    "FREE_TEXT requires allowFreeText=true and an empty options array.");
        }
        if (freeTextWithOptions && (!allowFreeText || options.isEmpty())) {
            return ContractValidationResult.failed("INVALID_CHOICE_OR_TEXT_CONFIGURATION", path,
                    "SINGLE_CHOICE_OR_FREE_TEXT requires allowFreeText=true and non-empty options.");
        }
        if (("SINGLE_CHOICE".equals(inputMode) || "CONFIRM".equals(inputMode))
                && (allowFreeText || options.isEmpty())) {
            return ContractValidationResult.failed("INVALID_CHOICE_CONFIGURATION", path,
                    inputMode + " requires allowFreeText=false and non-empty options.");
        }
        return ContractValidationResult.passed();
    }

    private ContractValidationResult validateStringArray(JSONObject object, String field, String path) {
        JSONArray values = object == null ? null : object.getJSONArray(field);
        if (values == null) {
            return ContractValidationResult.failed("MISSING_ARRAY_FIELD", path, path + " must be an array.");
        }
        for (int index = 0; index < values.size(); index++) {
            if (!(values.get(index) instanceof String)) {
                return ContractValidationResult.failed("INVALID_ARRAY_ITEM", path + "[" + index + "]",
                        path + " must contain strings.");
            }
        }
        return ContractValidationResult.passed();
    }

    private ContractValidationResult validateScore(JSONObject object, String field, String path) {
        Object value = object == null ? null : object.get(field);
        if (!(value instanceof Number number) || number.doubleValue() < 0.0 || number.doubleValue() > 1.0) {
            return ContractValidationResult.failed("INVALID_SCORE", path, path + " must be a number from 0.0 to 1.0.");
        }
        return ContractValidationResult.passed();
    }

    private JSONObject parsedObject(String rawOutput) {
        RawOutputParseResult result = rawOutputParser.parse(rawOutput);
        return result.isSuccess() ? result.getJsonObject() : null;
    }

    private ContractValidationResult parseFailure(String rawOutput) {
        RawOutputParseResult result = rawOutputParser.parse(rawOutput);
        return ContractValidationResult.failed(result.getErrorCode(), "$", result.getErrorMessage());
    }
}

