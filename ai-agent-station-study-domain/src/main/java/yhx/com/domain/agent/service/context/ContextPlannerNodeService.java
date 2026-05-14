package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.model.valobj.enums.contract.AgentComponentCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationCommand;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationResult;
import yhx.com.domain.agent.service.invocation.NodeInvocationPipeline;

public class ContextPlannerNodeService {

    private final NodeInvocationPipeline nodeInvocationPipeline;

    public ContextPlannerNodeService(NodeInvocationPipeline nodeInvocationPipeline) {
        this.nodeInvocationPipeline = nodeInvocationPipeline;
    }

    public ContextPlannerOutputVO plan(Object input) {
        NodeInvocationResult result = nodeInvocationPipeline.invoke(NodeInvocationCommand.builder()
                .componentCode(AgentComponentCodeEnumVO.CONTEXT_PLANNER.name())
                .contractVersion("context-planner-output-v1")
                .promptVersion("v1")
                .inputView(input)
                .maxRepairAttempts(1)
                .build());
        if (result.getTypedOutput() instanceof ContextPlannerOutputVO output) {
            return output;
        }
        return ContextPlannerOutputVO.builder()
                .status("FAILED")
                .reason(result.getFailureMessage())
                .build();
    }
}
