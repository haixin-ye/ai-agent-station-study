package yhx.com.domain.agent.model.valobj.enums;

import java.util.Arrays;
import java.util.Optional;

public enum ContextPlannerStatusEnumVO {
    READY("READY", "Selected context can be materialized."),
    NO_RELEVANT_CONTEXT("NO_RELEVANT_CONTEXT", "No useful context is available."),
    NEEDS_USER_CLARIFICATION("NEEDS_USER_CLARIFICATION", "User clarification is required."),
    CONTEXT_OVER_BUDGET("CONTEXT_OVER_BUDGET", "Selected context exceeds budget."),
    FAILED("FAILED", "Context planning failed.");

    private final String code;
    private final String info;

    ContextPlannerStatusEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<ContextPlannerStatusEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
