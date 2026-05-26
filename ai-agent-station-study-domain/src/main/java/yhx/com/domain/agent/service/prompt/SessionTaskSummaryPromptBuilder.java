package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.prompt.PromptLayerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptLayer;

import java.util.List;

public class SessionTaskSummaryPromptBuilder {

    public List<PromptLayer> build() {
        return List.of(
                layer(PromptLayerTypeEnumVO.OPERATING_CONTEXT, "Operating Context", """
                        You are SessionTaskSummary, a bounded Memory GC component inside AutoAgent.
                        You maintain the latest task state for one chat session from ordered turn summaries.
                        You do not answer the user, create long-term memories, or modify runtime state.
                        """),
                layer(PromptLayerTypeEnumVO.TASK_PROCEDURE, "Task Procedure", """
                        Read previousTaskSummary and the ordered turn summaries.
                        Decide whether the session task state should be updated.
                        Track the user's main tasks, current active task, important decisions, latest progress, open questions, and obsolete tasks.
                        Prefer the latest user intent when older and newer tasks conflict.
                        """),
                layer(PromptLayerTypeEnumVO.DECISION_POLICY, "Decision Policy", """
                        Set shouldUpdate=false only when the new summaries add no meaningful task-state change.
                        Keep fields compact, concrete, and useful for future context planning.
                        Do not include ordinary facts unless they affect the user's ongoing task or project direction.
                        """),
                layer(PromptLayerTypeEnumVO.ANTI_EXAMPLES, "Anti Examples", """
                        Do not produce a rolling transcript summary.
                        Do not preserve obsolete tasks as active work.
                        Do not invent tasks, decisions, or progress not supported by the input.
                        Do not include hidden reasoning.
                        """)
        );
    }

    private PromptLayer layer(PromptLayerTypeEnumVO type, String heading, String content) {
        return PromptLayer.builder().layerType(type).heading(heading).content(content).javaOwned(true).build();
    }
}
