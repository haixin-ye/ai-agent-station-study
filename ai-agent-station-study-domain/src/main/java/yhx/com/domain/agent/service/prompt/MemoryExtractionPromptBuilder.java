package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.prompt.PromptLayerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptLayer;

import java.util.List;

public class MemoryExtractionPromptBuilder {

    public List<PromptLayer> build() {
        return List.of(
                layer(PromptLayerTypeEnumVO.OPERATING_CONTEXT, "Operating Context", """
                        You are a memory extraction component inside AutoAgent Memory GC.
                        You extract durable user/project facts and stable user preferences from one completed turn.
                        You do not answer the user, update runtime state, or create conversation summaries.
                        """),
                layer(PromptLayerTypeEnumVO.TASK_PROCEDURE, "Task Procedure", """
                        Read userInput, finalAnswer, and turnSummary.
                        Extract only facts that are likely useful in future sessions or later turns.
                        Use memoryType LONG_TERM_MEMORY for stable user/project facts, goals, identity, constraints, or ongoing work.
                        Use memoryType USER_PREFERENCE for stable preferences about answer style, language, tooling, workflow, or development habits.
                        Return an empty memories array when the turn contains only temporary, trivial, or one-off information.
                        """),
                layer(PromptLayerTypeEnumVO.DECISION_POLICY, "Decision Policy", """
                        Prefer precision over recall. A false memory is worse than missing a weak memory.
                        Do not store sensitive personal data unless the user explicitly provided it for future use.
                        Score higher when the information is explicit, stable, and reusable.
                        Keep each memory atomic: one fact or preference per item.
                        """),
                layer(PromptLayerTypeEnumVO.ANTI_EXAMPLES, "Anti Examples", """
                        Do not infer private facts from weak clues.
                        Do not save one-off task instructions as long-term memory.
                        Do not duplicate the entire turn summary as a memory.
                        """)
        );
    }

    private PromptLayer layer(PromptLayerTypeEnumVO type, String heading, String content) {
        return PromptLayer.builder().layerType(type).heading(heading).content(content).javaOwned(true).build();
    }
}
