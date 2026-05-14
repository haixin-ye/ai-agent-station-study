package yhx.com.test.domain.agent.context;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.service.context.ContextPlannerStatusHandler;
import yhx.com.domain.agent.service.context.MainAgentStateViewBuilder;

import java.util.List;
import java.util.Map;

public class ContextPlannerStatusHandlerTest {

    @Test
    public void no_relevant_context_builds_minimal_state_view() {
        ContextPlannerHandlingResult result = handler().handle(ContextPlannerOutputVO.builder().status("NO_RELEVANT_CONTEXT").build(), candidates());

        Assert.assertEquals(ContextPlannerStatusHandler.BUILD_MINIMAL_STATE_VIEW, result.getNextStep());
        Assert.assertNotNull(result.getStateView());
    }

    @Test
    public void needs_user_clarification_returns_ask_user_result() {
        ContextPlannerHandlingResult result = handler().handle(ContextPlannerOutputVO.builder()
                .status("NEEDS_USER_CLARIFICATION")
                .clarificationRequest(Map.of("question", "Which article?", "inputMode", "SINGLE_CHOICE_OR_FREE_TEXT", "options", List.of()))
                .build(), candidates());

        Assert.assertEquals(ContextPlannerStatusHandler.ASK_USER, result.getNextStep());
        Assert.assertEquals("Which article?", result.getAskUserRequest().getQuestion());
    }

    @Test
    public void context_over_budget_returns_compress_or_ask() {
        ContextPlannerHandlingResult result = handler().handle(ContextPlannerOutputVO.builder().status("CONTEXT_OVER_BUDGET").build(), candidates());

        Assert.assertEquals(ContextPlannerStatusHandler.COMPRESS_OR_ASK, result.getNextStep());
    }

    private ContextPlannerStatusHandler handler() {
        return new ContextPlannerStatusHandler(null, new MainAgentStateViewBuilder());
    }

    private ContextCandidateBundleVO candidates() {
        return ContextCandidateBundleVO.builder()
                .recentMessages(List.of())
                .sessionSummaries(List.of())
                .artifactCandidates(List.of())
                .memoryCandidates(List.of())
                .evidenceCandidates(List.of())
                .availableCapabilities(List.of())
                .tokenBudget(TokenBudgetVO.builder().maxStateViewTokens(6000).build())
                .build();
    }
}
