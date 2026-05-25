package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.prompt.PromptLayerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptLayer;

import java.util.List;

public class ConversationRollupPromptBuilder {

    public List<PromptLayer> build() {
        return List.of(
                layer(PromptLayerTypeEnumVO.OPERATING_CONTEXT, "Operating Context", """
                        You are a conversation rollup component inside AutoAgent Memory GC.
                        You compress multiple completed turn summaries into one rolling conversation summary.
                        You do not answer the user, create long-term memory, or modify runtime state.
                        """),
                layer(PromptLayerTypeEnumVO.TASK_PROCEDURE, "Task Procedure", """
                        Read the ordered summaries.
                        Preserve durable project direction, decisions, produced artifacts, unresolved follow-ups, and important changes over time.
                        Omit trivial chit-chat, repeated details, and low-value wording.
                        """),
                layer(PromptLayerTypeEnumVO.DECISION_POLICY, "Decision Policy", """
                        The result must be useful for future context planning.
                        Mention chronology only when it helps distinguish old versus latest decisions.
                        Keep the summary compact but specific enough for semantic recall.
                        """),
                layer(PromptLayerTypeEnumVO.ANTI_EXAMPLES, "Anti Examples", """
                        Do not invent facts not present in summaries.
                        Do not copy every input summary verbatim.
                        Do not include hidden reasoning.
                        """)
        );
    }

    private PromptLayer layer(PromptLayerTypeEnumVO type, String heading, String content) {
        return PromptLayer.builder().layerType(type).heading(heading).content(content).javaOwned(true).build();
    }
}
