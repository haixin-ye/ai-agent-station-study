package yhx.com.test.domain.agent.context;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.CapabilityCandidateVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.context.UserInputVO;
import yhx.com.domain.agent.service.context.ContextBudgetManager;
import yhx.com.domain.agent.service.context.ContextTokenEstimator;

import java.util.List;
import java.util.Map;

public class ContextBudgetManagerTest {

    @Test
    public void shrink_to_fit_removes_capabilities_only_when_the_complete_state_view_is_over_budget() {
        ContextBudgetManager manager = new ContextBudgetManager(new ContextTokenEstimator());
        MainAgentStateViewVO stateView = MainAgentStateViewVO.builder()
                .userInput(UserInputVO.builder().content("read the requested file").build())
                .availableCapabilities(List.of(
                        capability("tool-a"), capability("tool-b"), capability("tool-c")))
                .build();
        TokenBudgetVO budget = TokenBudgetVO.builder().maxStateViewTokens(900).build();

        MainAgentStateViewVO shrunk = manager.shrinkToFit(stateView, budget);

        Assert.assertTrue(shrunk.getAvailableCapabilities().size() < 3);
        Assert.assertFalse(budget.getOverBudget());
        Assert.assertTrue(budget.getSelectedContextTokens() <= budget.getMaxStateViewTokens());
    }

    private CapabilityCandidateVO capability(String code) {
        return CapabilityCandidateVO.builder()
                .capabilityCode(code)
                .capabilityType("TOOL")
                .toolName(code)
                .description("tool description ".repeat(80))
                .inputSchema(Map.of(
                        "type", "object",
                        "description", "schema description ".repeat(60)))
                .requiredArguments(List.of("path"))
                .enabled(true)
                .build();
    }
}
