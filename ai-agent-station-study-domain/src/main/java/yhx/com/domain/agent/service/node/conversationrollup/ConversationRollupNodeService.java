package yhx.com.domain.agent.service.node.conversationrollup;

import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.model.valobj.memory.ConversationRollupInputVO;
import yhx.com.domain.agent.model.valobj.memory.ConversationRollupOutputVO;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;

public class ConversationRollupNodeService {

    private final NodeInvocationPipeline nodeInvocationPipeline;
    private final NodeInvocationProfileVO defaultProfile;

    public ConversationRollupNodeService(NodeInvocationPipeline nodeInvocationPipeline) {
        this(nodeInvocationPipeline, null);
    }

    public ConversationRollupNodeService(NodeInvocationPipeline nodeInvocationPipeline, NodeInvocationProfileVO defaultProfile) {
        this.nodeInvocationPipeline = nodeInvocationPipeline;
        this.defaultProfile = defaultProfile;
    }

    public ConversationRollupOutputVO summarize(ConversationRollupInputVO input, String agentId, NodeInvocationProfileVO profile) {
        if (nodeInvocationPipeline == null) {
            return null;
        }
        NodeInvocationProfileVO effectiveProfile = profile == null ? defaultProfile : profile;
        NodeInvocationResult result = nodeInvocationPipeline.invoke(NodeInvocationCommand.builder()
                .runId(input == null ? null : input.getRunId())
                .agentId(agentId)
                .componentCode(AgentComponentCodeEnumVO.CONVERSATION_ROLLUP.name())
                .contractVersion(firstNonBlank(effectiveProfile == null ? null : effectiveProfile.getContractVersion(), "conversation-rollup-output-v1"))
                .promptVersion(firstNonBlank(effectiveProfile == null ? null : effectiveProfile.getPromptVersion(), "v1"))
                .modelCode(effectiveProfile == null ? null : effectiveProfile.getModelCode())
                .temperature(effectiveProfile == null ? null : effectiveProfile.getTemperature())
                .maxOutputTokens(effectiveProfile == null ? null : effectiveProfile.getMaxOutputTokens())
                .maxRepairAttempts(effectiveProfile == null || effectiveProfile.getMaxRepairAttempts() == null ? 1 : effectiveProfile.getMaxRepairAttempts())
                .inputView(input)
                .build());
        if (result.getTypedOutput() instanceof ConversationRollupOutputVO output) {
            return output;
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
