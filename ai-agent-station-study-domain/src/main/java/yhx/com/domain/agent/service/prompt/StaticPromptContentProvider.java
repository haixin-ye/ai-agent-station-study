package yhx.com.domain.agent.service.prompt;

import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;

import java.util.List;

public class StaticPromptContentProvider implements PromptContentProvider {

    @Override
    public List<String> loadRolePrompts(String agentId, String componentCode, String promptVersion) {
        if (AgentComponentCodeEnumVO.CONTEXT_PLANNER.name().equals(componentCode)) {
            return List.of("You are ContextPlannerNode. You select context references for the next MainAgentNode call.");
        }
        if (AgentComponentCodeEnumVO.MAIN_AGENT.name().equals(componentCode)) {
            return List.of("You are MainAgentNode. You choose one structured next action for this loop iteration.");
        }
        if (AgentComponentCodeEnumVO.RAG_VERIFIER.name().equals(componentCode)) {
            return List.of("You are RagVerifier. You verify whether final answer content is grounded in retrieved RAG evidence.");
        }
        if (AgentComponentCodeEnumVO.FINAL_REPAIR.name().equals(componentCode)) {
            return List.of("You repair final answer action JSON without changing task intent.");
        }
        if (AgentComponentCodeEnumVO.CONTRACT_REPAIR.name().equals(componentCode)) {
            return List.of("You repair invalid structured JSON so it matches the specified contract.");
        }
        if (AgentComponentCodeEnumVO.TURN_SUMMARY.name().equals(componentCode)) {
            return List.of("You are TurnSummaryNode. You summarize one completed user-agent turn for future memory recall.");
        }
        if (AgentComponentCodeEnumVO.MEMORY_EXTRACTOR.name().equals(componentCode)) {
            return List.of("You are MemoryExtractor. You extract durable long-term memories and user preferences from completed turns.");
        }
        if (AgentComponentCodeEnumVO.SESSION_TASK_SUMMARY.name().equals(componentCode)) {
            return List.of("You are SessionTaskSummary. You maintain the latest task state for one chat session.");
        }
        if (AgentComponentCodeEnumVO.CONVERSATION_ROLLUP.name().equals(componentCode)) {
            return List.of("You are ConversationRollup. You compress multiple turn summaries into one rolling session summary.");
        }
        return List.of("You are an AutoAgent bounded-step component.");
    }
}
