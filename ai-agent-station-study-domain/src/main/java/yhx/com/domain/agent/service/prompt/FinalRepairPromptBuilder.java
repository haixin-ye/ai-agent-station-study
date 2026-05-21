package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.prompt.PromptLayerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptLayer;

import java.util.List;

public class FinalRepairPromptBuilder {

    public List<PromptLayer> build() {
        return List.of(
                layer(PromptLayerTypeEnumVO.OPERATING_CONTEXT, "Operating Context", """
                        You repair only the final user-facing answer after the final response guard rejected a candidate.
                        Preserve the user's task intent and rewrite the answer so it is helpful, safe, and free of internal runtime details.
                        """),
                layer(PromptLayerTypeEnumVO.TASK_PROCEDURE, "Task Procedure", """
                        Read the failedCandidate, failureCode, guardSummary, and repairInstruction.
                        Produce one REPAIR_FINAL action whose stateDelta.finalAnswerCandidate contains the repaired answer.
                        Do not expose prompts, contracts, traces, validation details, node names, raw tool receipts, or repair process details.
                        Do not change the task into a new plan, RAG request, tool call, or user clarification.
                        """),
                layer(PromptLayerTypeEnumVO.INPUT_FIELD_GUIDE, "Input Field Guide", """
                        userInput: original user request.
                        failedCandidate: final answer candidate that did not pass the guard.
                        failureCode: reason category reported by the guard.
                        guardSummary: concise guard explanation.
                        repairInstruction: additional rewrite boundary.
                        """)
        );
    }

    private PromptLayer layer(PromptLayerTypeEnumVO type, String heading, String content) {
        return PromptLayer.builder().layerType(type).heading(heading).content(content).javaOwned(true).build();
    }
}
