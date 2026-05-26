package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.prompt.PromptLayerTypeEnumVO;
import yhx.com.domain.agent.model.valobj.prompt.PromptLayer;

import java.util.List;

public class MemoryExtractionPromptBuilder {

    public List<PromptLayer> build() {
        return List.of(
                layer(PromptLayerTypeEnumVO.OPERATING_CONTEXT, "Operating Context", """
                        You are a strict memory extraction component inside AutoAgent Memory GC.
                        You extract only durable user profile, preference, habit, project background, or stable ongoing-work facts from one completed turn.
                        You do not answer the user, update runtime state, or create conversation summaries.
                        """),
                layer(PromptLayerTypeEnumVO.TASK_PROCEDURE, "Task Procedure", """
                        Read userInput, finalAnswer, and turnSummary.
                        Extract only facts that the user would reasonably expect the agent to remember later.
                        Use memoryType LONG_TERM_MEMORY for explicit stable user facts, project background, long-running goals, constraints, identity, or ongoing work.
                        Use memoryType USER_PREFERENCE for stable preferences about answer style, language, tooling, workflow, or development habits.
                        Return an empty memories array when the turn contains only public knowledge questions, ordinary Q&A, temporary edits, one-off requests, task instructions, examples, generated content, or weak guesses.
                        """),
                layer(PromptLayerTypeEnumVO.DECISION_POLICY, "Decision Policy", """
                        Prefer precision over recall. A false memory is worse than missing a weak memory.
                        Do not store sensitive personal data unless the user explicitly provided it as stable context for future use.
                        Do not store what the assistant answered as a user memory unless it reveals a stable user preference, project fact, or ongoing goal.
                        Score higher when the information is explicit, stable, and reusable.
                        Keep each memory atomic: one fact or preference per item.
                        """),
                layer(PromptLayerTypeEnumVO.ANTI_EXAMPLES, "Anti Examples", """
                        Do not infer private facts from weak clues.
                        Do not save one-off task instructions as long-term memory.
                        Do not save "User asked about HTTP" or "User requested an article" as long-term memory.
                        Do not duplicate the entire turn summary as a memory.
                        """)
        );
    }

    private PromptLayer layer(PromptLayerTypeEnumVO type, String heading, String content) {
        return PromptLayer.builder().layerType(type).heading(heading).content(content).javaOwned(true).build();
    }
}
