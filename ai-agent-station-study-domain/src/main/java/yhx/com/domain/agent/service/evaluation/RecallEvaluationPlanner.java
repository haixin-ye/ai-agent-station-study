package yhx.com.domain.agent.service.evaluation;

import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationRunConfigVO;

@FunctionalInterface
public interface RecallEvaluationPlanner {
    ContextPlannerOutputVO plan(ContextCandidateBundleVO candidates, RecallEvaluationRunConfigVO config);

    default ContextPlannerOutputVO plan(ContextCandidateBundleVO candidates,
                                        RecallEvaluationRunConfigVO config,
                                        String evaluationRunId,
                                        String agentId) {
        return plan(candidates, config);
    }
}
