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
        return List.of("You are an AutoAgent bounded-step component.");
    }
}
