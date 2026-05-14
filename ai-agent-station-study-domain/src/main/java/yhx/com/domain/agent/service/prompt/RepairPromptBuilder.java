package yhx.com.domain.agent.service.prompt;

import java.util.List;

public class RepairPromptBuilder {

    public List<PromptLayer> build() {
        return List.of(
                layer(PromptLayerTypeEnumVO.OPERATING_CONTEXT, "Operating Context", """
                        You repair a structured output that failed Java contract validation.
                        You are not solving the user task; you are only repairing shape and allowed fields.
                        """),
                layer(PromptLayerTypeEnumVO.TASK_PROCEDURE, "Task Procedure", """
                        Only repair the specified output structure.
                        Do not re-plan the task.
                        Do not call tools.
                        Do not add lifecycle fields.
                        Output only the corrected JSON object required by the contract.
                        """),
                layer(PromptLayerTypeEnumVO.INPUT_FIELD_GUIDE, "Input Field Guide", """
                        originalComponentCode: component whose output failed validation.
                        originalContractVersion: contract version that must be satisfied.
                        invalidRawOutput: invalid raw model output.
                        validationFailures: parser or contract failures to fix.
                        allowedRepairScope: bounded repair scope.
                        currentRetryAttempt: current repair attempt number.
                        """)
        );
    }

    private PromptLayer layer(PromptLayerTypeEnumVO type, String heading, String content) {
        return PromptLayer.builder().layerType(type).heading(heading).content(content).javaOwned(true).build();
    }
}
