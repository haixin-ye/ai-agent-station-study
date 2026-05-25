package yhx.com.domain.agent.service.contract;

import yhx.com.domain.agent.model.valobj.contract.AgentNodeContract;
import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class ContractRegistry {

    private final Map<AgentComponentCodeEnumVO, AgentNodeContract> contracts;

    public ContractRegistry(Map<AgentComponentCodeEnumVO, AgentNodeContract> contracts) {
        this.contracts = new EnumMap<>(contracts);
    }

    public static ContractRegistry defaultRegistry() {
        Map<AgentComponentCodeEnumVO, AgentNodeContract> map = new EnumMap<>(AgentComponentCodeEnumVO.class);
        register(map, AgentComponentCodeEnumVO.CONTEXT_PLANNER, "ContextPlannerOutputContract", "context-planner-output-v1");
        register(map, AgentComponentCodeEnumVO.MAIN_AGENT, "MainAgentActionContract", "main-agent-action-v1");
        register(map, AgentComponentCodeEnumVO.TOOL_RUNTIME, "ToolInvocationResultContract", "tool-invocation-result-v1");
        register(map, AgentComponentCodeEnumVO.USER_INTERACTION, "UserInteractionContract", "user-interaction-v1");
        register(map, AgentComponentCodeEnumVO.RAG_VERIFIER, "VerificationResultContract", "verification-result-v1");
        register(map, AgentComponentCodeEnumVO.TOOL_VERIFIER, "VerificationResultContract", "verification-result-v1");
        register(map, AgentComponentCodeEnumVO.FINAL_RESPONSE_GUARD, "FinalResponseGuardResultContract", "final-response-guard-result-v1");
        register(map, AgentComponentCodeEnumVO.FINAL_REPAIR, "MainAgentActionContract", "main-agent-action-v1");
        register(map, AgentComponentCodeEnumVO.TURN_SUMMARY, "TurnSummaryOutputContract", "turn-summary-output-v1");
        register(map, AgentComponentCodeEnumVO.MEMORY_EXTRACTOR, "MemoryExtractionOutputContract", "memory-extraction-output-v1");
        register(map, AgentComponentCodeEnumVO.CONVERSATION_ROLLUP, "ConversationRollupOutputContract", "conversation-rollup-output-v1");
        register(map, AgentComponentCodeEnumVO.CONTRACT_REPAIR, "OriginalNodeContract", "original-node-contract");
        return new ContractRegistry(map);
    }

    public Optional<AgentNodeContract> get(AgentComponentCodeEnumVO componentCode) {
        return Optional.ofNullable(contracts.get(componentCode));
    }

    public AgentNodeContract getRequired(AgentComponentCodeEnumVO componentCode) {
        return get(componentCode).orElseThrow(() -> new IllegalArgumentException("Missing contract: " + componentCode));
    }

    private static void register(Map<AgentComponentCodeEnumVO, AgentNodeContract> map,
                                 AgentComponentCodeEnumVO componentCode,
                                 String name,
                                 String version) {
        map.put(componentCode, AgentNodeContract.builder()
                .componentCode(componentCode)
                .name(name)
                .version(version)
                .description(componentCode.name() + " structured output contract")
                .build());
    }
}

