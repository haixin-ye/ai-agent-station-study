package yhx.com.domain.agent.service.evaluation;

import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.model.valobj.invocation.NodeInvocationProfileVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationRunConfigVO;
import yhx.com.domain.agent.service.node.contextplanner.ContextPlannerNodeService;

public class ContextPlannerEvaluationAdapter implements RecallEvaluationPlanner {
    private static final String EVALUATION_AGENT_ID = "RECALL_EVALUATION";

    private final ContextPlannerNodeService contextPlannerNodeService;

    public ContextPlannerEvaluationAdapter(ContextPlannerNodeService contextPlannerNodeService) {
        this.contextPlannerNodeService = contextPlannerNodeService;
    }

    @Override
    public ContextPlannerOutputVO plan(ContextCandidateBundleVO candidates, RecallEvaluationRunConfigVO config) {
        String fallbackRunId = "eval-planner-" + java.util.UUID.randomUUID();
        return plan(candidates, config, fallbackRunId, EVALUATION_AGENT_ID);
    }

    @Override
    public ContextPlannerOutputVO plan(ContextCandidateBundleVO candidates,
                                       RecallEvaluationRunConfigVO config,
                                       String evaluationRunId,
                                       String agentId) {
        if (contextPlannerNodeService == null) {
            throw new IllegalStateException("Context Planner is not configured for evaluation.");
        }
        NodeInvocationProfileVO profile = config == null ? null : NodeInvocationProfileVO.builder()
                .modelCode(config.getPlannerModelCode())
                .temperature(config.getPlannerTemperature())
                .maxOutputTokens(config.getPlannerMaxOutputTokens())
                .maxRepairAttempts(1)
                .build();
        return contextPlannerNodeService.plan(candidates,
                requireIdentity(evaluationRunId, "evaluationRunId"),
                firstNonBlank(agentId, EVALUATION_AGENT_ID),
                profile);
    }

    private String requireIdentity(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required for Context Planner evaluation.");
        }
        return value;
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }
}
