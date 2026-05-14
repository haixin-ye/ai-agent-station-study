package yhx.com.domain.agent.service.prompt;

import java.util.Arrays;
import java.util.Optional;

public enum PromptLayerTypeEnumVO {
    ROLE_PROMPT("ROLE_PROMPT", "Role and business prompt loaded from editable prompt content."),
    STABLE_BEHAVIOR_RULES("STABLE_BEHAVIOR_RULES", "Java-owned stable behavior rules."),
    RUNTIME_BOUNDARY_RULES("RUNTIME_BOUNDARY_RULES", "Java-owned Runtime boundary rules."),
    UNTRUSTED_CONTENT_RULES("UNTRUSTED_CONTENT_RULES", "Java-owned untrusted content rules."),
    OPERATING_CONTEXT("OPERATING_CONTEXT", "Component operating context."),
    INPUT_FIELD_GUIDE("INPUT_FIELD_GUIDE", "Input field meaning guide."),
    TASK_PROCEDURE("TASK_PROCEDURE", "Component task procedure."),
    DECISION_POLICY("DECISION_POLICY", "Component decision policy."),
    RISK_AND_PERMISSION_POLICY("RISK_AND_PERMISSION_POLICY", "Risk and permission policy."),
    OUTPUT_CONTRACT("OUTPUT_CONTRACT", "Java-owned structured output contract."),
    FEW_SHOT_EXAMPLES("FEW_SHOT_EXAMPLES", "Valid examples."),
    ANTI_EXAMPLES("ANTI_EXAMPLES", "Invalid examples."),
    CURRENT_STATE_VIEW("CURRENT_STATE_VIEW", "Current input view for this invocation."),
    OUTPUT_ONLY_INSTRUCTION("OUTPUT_ONLY_INSTRUCTION", "Final output-only instruction.");

    private final String code;
    private final String info;

    PromptLayerTypeEnumVO(String code, String info) {
        this.code = code;
        this.info = info;
    }

    public String code() {
        return code;
    }

    public String info() {
        return info;
    }

    public static Optional<PromptLayerTypeEnumVO> ofCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
}
