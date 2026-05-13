package yhx.com.domain.agent.model.valobj.enums.runtime;

import java.util.Arrays;
import java.util.Optional;

public enum RuntimePhaseEnumVO {
    CREATED("CREATED", "Initial run record and user message creation."),
    PREPARING_CONTEXT("PREPARING_CONTEXT", "Runtime loads candidates and current state."),
    PLANNING_CONTEXT("PLANNING_CONTEXT", "Runtime invokes ContextPlannerNode when needed."),
    BUILDING_STATE_VIEW("BUILDING_STATE_VIEW", "Runtime builds MainAgentStateView."),
    CALLING_MAIN_NODE("CALLING_MAIN_NODE", "Runtime invokes MainAgentNode."),
    VALIDATING_ACTION("VALIDATING_ACTION", "Runtime parses and validates MainAgentAction."),
    HANDLING_ACTION("HANDLING_ACTION", "Runtime routes the action to the correct handler."),
    EXECUTING_RAG("EXECUTING_RAG", "Runtime executes RAG retrieval."),
    PREPARING_TOOL("PREPARING_TOOL", "Runtime validates tool intent and permission."),
    INVOKING_TOOL_RUNTIME("INVOKING_TOOL_RUNTIME", "Runtime invokes ToolRuntime."),
    VERIFYING_TOOL("VERIFYING_TOOL", "Runtime verifies real tool receipt."),
    VERIFYING_RAG("VERIFYING_RAG", "Runtime verifies RAG grounding when required."),
    VERIFYING_FINAL("VERIFYING_FINAL", "Runtime runs final response guard pipeline."),
    REPAIRING_CONTRACT("REPAIRING_CONTRACT", "Runtime performs bounded contract repair."),
    REPAIRING_FINAL("REPAIRING_FINAL", "Runtime performs bounded final answer repair."),
    WAITING_USER("WAITING_USER", "Runtime stores pending input and pauses execution."),
    RESOLVING_USER_ANSWER("RESOLVING_USER_ANSWER", "Runtime normalizes submitted user answer."),
    COMPLETED("COMPLETED", "Runtime persists final response and assistant message."),
    FAILED("FAILED", "Runtime persists failure and user-safe error."),
    CANCELLED("CANCELLED", "Runtime persists cancellation.");

    private final String code;
    private final String info;

    RuntimePhaseEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<RuntimePhaseEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}

