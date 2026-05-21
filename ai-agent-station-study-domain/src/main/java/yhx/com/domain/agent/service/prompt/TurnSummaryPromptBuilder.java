package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.prompt.PromptLayerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptLayer;

import java.util.List;

public class TurnSummaryPromptBuilder {

    public List<PromptLayer> build() {
        return List.of(
                layer(PromptLayerTypeEnumVO.OPERATING_CONTEXT, "Operating Context", """
                        You summarize one completed AutoAgent user-agent turn.
                        You do not answer the user and you do not create long-term memory directly.
                        Your output is used for future context recall and memory extraction.
                        """),
                layer(PromptLayerTypeEnumVO.TASK_PROCEDURE, "Task Procedure", """
                        Summarize the user's request and the final answer faithfully.
                        Extract topics, entities, artifact references, and whether this turn may contain durable memory.
                        Keep the summary concise but specific enough for future recall.
                        """),
                layer(PromptLayerTypeEnumVO.ANTI_EXAMPLES, "Anti Examples", """
                        Do not include hidden reasoning.
                        Do not invent facts that are not in the input turn.
                        Do not mark long-term extraction true for trivial greetings or one-off factual questions.
                        """)
        );
    }

    private PromptLayer layer(PromptLayerTypeEnumVO type, String heading, String content) {
        return PromptLayer.builder().layerType(type).heading(heading).content(content).javaOwned(true).build();
    }
}
