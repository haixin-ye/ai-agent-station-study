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
                        All human-readable output fields must be written in Simplified Chinese.
                        """),
                layer(PromptLayerTypeEnumVO.TASK_PROCEDURE, "Task Procedure", """
                        Read userInput, finalAnswer, and turnSummary.
                        Extract only facts that the user would reasonably expect the agent to remember later.
                        Use memoryType LONG_TERM_MEMORY for explicit stable user facts, project background, long-running goals, constraints, identity, or ongoing work.
                        Use memoryType USER_PREFERENCE for stable preferences about answer style, language, tooling, workflow, or development habits.
                        Always extract explicit user self-identification, names, nicknames, or preferred forms of address such as "我叫...", "我的名字是...", "我的昵称是...", or "叫我...".
                        Extract explicit memory requests such as "你要记住...", "帮我记住...", "以后记得...", "后续都...", "以后默认...", or "以后写文章/回答时要...".
                        Treat user-stated future/default behavior preferences as durable USER_PREFERENCE when they are not limited to this session.
                        Do not save instructions that are explicitly scoped to this session, this chat, this conversation, this task, this article, or the current answer. Keep those as session/task context rather than long-term memory.
                        Return an empty memories array when the turn contains only public knowledge questions, ordinary Q&A, temporary edits, one-off requests, task instructions, examples, generated content, trivial greetings without durable user information, or weak guesses.
                        For each saved memory, write summary as a clean human-readable fact, and write recallText as a semantic-search-friendly rewrite containing likely future query aliases and references.
                        Write summary, content, reason, recallText, aliases, and descriptive text in Simplified Chinese.
                        """),
                layer(PromptLayerTypeEnumVO.DECISION_POLICY, "Decision Policy", """
                        Prefer precision over recall. A false memory is worse than missing a weak memory.
                        When the user explicitly says to remember something for the future, prefer recall over strictness unless the statement is unsafe, contradictory, or clearly session-scoped.
                        Do not store sensitive personal data unless the user explicitly provided it as stable context for future use.
                        Do not store what the assistant answered as a user memory unless it reveals a stable user preference, project fact, or ongoing goal.
                        If wording includes "本会话", "这个会话", "当前对话", "这次任务", "这篇文章", "这个故事", or similar scope-limiting phrases, do not create long-term memory for that instruction.
                        Score higher when the information is explicit, stable, and reusable.
                        Keep each memory atomic: one fact or preference per item.
                        For explicit user names, nicknames, or preferred forms of address, use memoryType LONG_TERM_MEMORY and write the memory as "用户的称呼或昵称是X。"
                        recallText should improve retrieval without inventing new facts. For example, if the user says their name is Zhang San, include aliases such as name, full name, called, and "my name" in recallText.
                        For Chinese users, recallText should include Chinese retrieval aliases, for example: 姓名、名字、称呼、我叫什么、我的名字.
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
