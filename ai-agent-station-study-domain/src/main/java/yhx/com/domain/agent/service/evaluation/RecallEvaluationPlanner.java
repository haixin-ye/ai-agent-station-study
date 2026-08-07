package yhx.com.domain.agent.service.evaluation;

import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;

@FunctionalInterface
public interface RecallEvaluationPlanner {
    ContextPlannerOutputVO plan(ContextCandidateBundleVO candidates);
}
