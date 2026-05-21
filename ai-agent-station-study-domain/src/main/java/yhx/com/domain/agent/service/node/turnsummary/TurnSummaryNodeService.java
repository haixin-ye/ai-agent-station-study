package yhx.com.domain.agent.service.node.turnsummary;

import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.model.valobj.memory.TurnSummaryInputVO;
import yhx.com.domain.agent.model.valobj.memory.TurnSummaryOutputVO;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;

public class TurnSummaryNodeService {

    private final NodeInvocationPipeline nodeInvocationPipeline;

    public TurnSummaryNodeService(NodeInvocationPipeline nodeInvocationPipeline) {
        this.nodeInvocationPipeline = nodeInvocationPipeline;
    }

    public TurnSummaryOutputVO summarize(TurnSummaryInputVO input, String agentId, NodeInvocationProfileVO profile) {
        if (nodeInvocationPipeline == null) {
            return null;
        }
        NodeInvocationResult result = nodeInvocationPipeline.invoke(NodeInvocationCommand.builder()
                .runId(input == null ? null : input.getRunId())
                .agentId(agentId)
                .componentCode(AgentComponentCodeEnumVO.TURN_SUMMARY.name())
                .contractVersion(firstNonBlank(profile == null ? null : profile.getContractVersion(), "turn-summary-output-v1"))
                .promptVersion(firstNonBlank(profile == null ? null : profile.getPromptVersion(), "v1"))
                .modelCode(profile == null ? null : profile.getModelCode())
                .temperature(profile == null ? null : profile.getTemperature())
                .maxOutputTokens(profile == null ? null : profile.getMaxOutputTokens())
                .maxRepairAttempts(profile == null || profile.getMaxRepairAttempts() == null ? 1 : profile.getMaxRepairAttempts())
                .inputView(input)
                .build());
        if (result.getTypedOutput() instanceof TurnSummaryOutputVO output) {
            return output;
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
