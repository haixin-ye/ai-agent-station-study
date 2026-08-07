package yhx.com.domain.agent.service.node.contextplanner;

import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;

public class ContextPlannerNodeService {

    private final NodeInvocationPipeline nodeInvocationPipeline;

    public ContextPlannerNodeService(NodeInvocationPipeline nodeInvocationPipeline) {
        this.nodeInvocationPipeline = nodeInvocationPipeline;
    }

    public ContextPlannerOutputVO plan(Object input) {
        return plan(input, null, null, null);
    }

    public ContextPlannerOutputVO plan(Object input, String runId, String agentId, NodeInvocationProfileVO profile) {
        return plan(input, runId, agentId, profile, null);
    }

    public ContextPlannerOutputVO plan(Object input, String runId, String agentId,
                                       NodeInvocationProfileVO profile, Integer loopIndex) {
        NodeInvocationResult result = nodeInvocationPipeline.invoke(NodeInvocationCommand.builder()
                .runId(runId)
                .agentId(agentId)
                .componentCode(AgentComponentCodeEnumVO.CONTEXT_PLANNER.name())
                .contractVersion(firstNonBlank(profile == null ? null : profile.getContractVersion(), "context-planner-output-v1"))
                .promptVersion(firstNonBlank(profile == null ? null : profile.getPromptVersion(), "v1"))
                .modelCode(profile == null ? null : profile.getModelCode())
                .temperature(profile == null ? null : profile.getTemperature())
                .maxOutputTokens(profile == null ? null : profile.getMaxOutputTokens())
                .inputView(input)
                .maxRepairAttempts(profile == null || profile.getMaxRepairAttempts() == null ? 1 : profile.getMaxRepairAttempts())
                .invocationMetadata(loopIndex == null ? null : java.util.Map.of("loopIndex", loopIndex,
                        "nodeStage", "CONTEXT_PLANNER"))
                .build());
        if (result.getTypedOutput() instanceof ContextPlannerOutputVO output) {
            return output;
        }
        return ContextPlannerOutputVO.builder()
                .status("FAILED")
                .reason(result.getFailureMessage())
                .build();
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
