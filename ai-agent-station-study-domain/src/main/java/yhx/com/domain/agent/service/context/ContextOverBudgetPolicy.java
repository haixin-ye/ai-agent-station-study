package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.model.valobj.context.FailureVO;

public class ContextOverBudgetPolicy {

    public FailureVO overBudgetFailure() {
        return FailureVO.builder()
                .failureCode("CONTEXT_OVER_BUDGET")
                .message("Selected context is too large to safely send to MainAgentNode.")
                .build();
    }
}
