package cn.bugstack.ai.domain.agent.service.execute.auto.contract;

import cn.bugstack.ai.domain.agent.model.entity.AutoAgentNodeTraceVO;
import cn.bugstack.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for prompt envelopes and node traces.
 */
public final class AutoAgentPromptContractSupport {

    private AutoAgentPromptContractSupport() {
    }

    public static Map<String, Object> wrapPromptPayload(AutoAgentNodeContract contract, Map<String, Object> payload) {
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("contractMeta", contract.meta());
        wrapped.putAll(payload);
        return wrapped;
    }

    public static void recordTrace(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                   AutoAgentNodeContract contract,
                                   String parseMode,
                                   String recoveryLevel,
                                   boolean lowConfidence,
                                   String blockingReason,
                                   List<String> sourceOfTruthUsed) {
        if (dynamicContext == null) {
            return;
        }
        AutoAgentNodeTraceVO trace = AutoAgentNodeTraceVO.builder()
                .nodeId(contract.nodeId())
                .contractVersion(contract.contractVersion())
                .parseMode(parseMode)
                .recoveryLevel(recoveryLevel)
                .lowConfidence(lowConfidence)
                .blockingReason(blockingReason)
                .sourceOfTruthUsed(sourceOfTruthUsed == null ? List.of() : sourceOfTruthUsed)
                .build();
        dynamicContext.getNodeTrace().put(contract.nodeId(), trace);
        dynamicContext.getStructuredState().put(contract.nodeId() + "Trace", trace);
    }
}
