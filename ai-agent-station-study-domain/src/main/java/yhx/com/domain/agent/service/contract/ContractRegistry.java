package yhx.com.domain.agent.service.contract;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class ContractRegistry {

    private final Map<AgentComponentCode, AgentNodeContract> contracts;

    public ContractRegistry(Map<AgentComponentCode, AgentNodeContract> contracts) {
        this.contracts = new EnumMap<>(contracts);
    }

    public static ContractRegistry defaultRegistry() {
        Map<AgentComponentCode, AgentNodeContract> map = new EnumMap<>(AgentComponentCode.class);
        register(map, AgentComponentCode.CONTEXT_PLANNER, "ContextPlannerOutputContract", "context-planner-output-v1");
        register(map, AgentComponentCode.MAIN_AGENT, "MainAgentActionContract", "main-agent-action-v1");
        register(map, AgentComponentCode.TOOL_RUNTIME, "ToolInvocationResultContract", "tool-invocation-result-v1");
        register(map, AgentComponentCode.USER_INTERACTION, "UserInteractionContract", "user-interaction-v1");
        register(map, AgentComponentCode.RAG_VERIFIER, "VerificationResultContract", "verification-result-v1");
        register(map, AgentComponentCode.TOOL_VERIFIER, "VerificationResultContract", "verification-result-v1");
        register(map, AgentComponentCode.FINAL_RESPONSE_GUARD, "FinalResponseGuardResultContract", "final-response-guard-result-v1");
        register(map, AgentComponentCode.FINAL_REPAIR, "MainAgentActionContract", "main-agent-action-v1");
        register(map, AgentComponentCode.CONTRACT_REPAIR, "OriginalNodeContract", "original-node-contract");
        return new ContractRegistry(map);
    }

    public Optional<AgentNodeContract> get(AgentComponentCode componentCode) {
        return Optional.ofNullable(contracts.get(componentCode));
    }

    public AgentNodeContract getRequired(AgentComponentCode componentCode) {
        return get(componentCode).orElseThrow(() -> new IllegalArgumentException("Missing contract: " + componentCode));
    }

    private static void register(Map<AgentComponentCode, AgentNodeContract> map,
                                 AgentComponentCode componentCode,
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
