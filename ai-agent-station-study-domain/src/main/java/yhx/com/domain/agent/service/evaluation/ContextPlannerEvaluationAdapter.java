package yhx.com.domain.agent.service.evaluation;

import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationRunConfigVO;
import yhx.com.domain.agent.service.node.contextplanner.ContextPlannerNodeService;

public class ContextPlannerEvaluationAdapter implements RecallEvaluationPlanner {
    private final ContextPlannerNodeService contextPlannerNodeService;

    public ContextPlannerEvaluationAdapter(ContextPlannerNodeService contextPlannerNodeService) {
        this.contextPlannerNodeService = contextPlannerNodeService;
    }

    @Override
    public ContextPlannerOutputVO plan(ContextCandidateBundleVO candidates, RecallEvaluationRunConfigVO config) {
        if (contextPlannerNodeService == null) {
            throw new IllegalStateException("Context Planner is not configured for evaluation.");
        }
        NodeInvocationProfileVO profile = config == null ? null : NodeInvocationProfileVO.builder()
                .modelCode(config.getPlannerModelCode())
                .temperature(config.getPlannerTemperature())
                .maxOutputTokens(config.getPlannerMaxOutputTokens())
                .maxRepairAttempts(1)
                .build();
        return contextPlannerNodeService.plan(candidates, null, null, profile);
    }
}
