package yhx.com.domain.agent.service.evaluation;

import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.service.node.contextplanner.ContextPlannerNodeService;

public class ContextPlannerEvaluationAdapter implements RecallEvaluationPlanner {
    private final ContextPlannerNodeService contextPlannerNodeService;

    public ContextPlannerEvaluationAdapter(ContextPlannerNodeService contextPlannerNodeService) {
        this.contextPlannerNodeService = contextPlannerNodeService;
    }

    @Override
    public ContextPlannerOutputVO plan(ContextCandidateBundleVO candidates) {
        if (contextPlannerNodeService == null) {
            throw new IllegalStateException("Context Planner is not configured for evaluation.");
        }
        return contextPlannerNodeService.plan(candidates);
    }
}
