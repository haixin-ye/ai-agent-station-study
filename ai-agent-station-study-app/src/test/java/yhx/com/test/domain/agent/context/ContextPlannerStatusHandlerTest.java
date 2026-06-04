package yhx.com.test.domain.agent.context;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.EvidenceCandidateVO;
import yhx.com.domain.agent.model.valobj.context.MemoryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.service.context.ContextBudgetManager;
import yhx.com.domain.agent.service.context.ContextMaterializer;
import yhx.com.domain.agent.service.context.ContextPlannerStatusHandler;
import yhx.com.domain.agent.service.context.ContextSelectionValidator;
import yhx.com.domain.agent.service.context.ContextTokenEstimator;
import yhx.com.domain.agent.service.context.MainAgentStateViewBuilder;
import yhx.com.domain.agent.service.evidence.EvidencePackBuilder;

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

    @Test
    public void ready_selection_uses_summary_id_as_source_id() {
        ContextCandidateBundleVO candidates = candidatesWithSummary();

        ContextPlannerHandlingResult result = materializingHandler().handle(ContextPlannerOutputVO.builder()
                .status("READY")
                .selectedContext(List.of(Map.of(
                        "sourceType", "SESSION_SUMMARY",
                        "summaryId", "turn-summary-1",
                        "useLevel", "FULL_TEXT",
                        "reason", "用户提到刚刚的故事。")))
                .build(), candidates);

        Assert.assertEquals(ContextPlannerStatusHandler.BUILD_STATE_VIEW, result.getNextStep());
        Assert.assertEquals("turn-summary-1", result.getEffectiveSelections().get(0).getSourceId());
        Assert.assertEquals(1, result.getStateView().getConversation().getSummaries().size());
        Assert.assertEquals("turn-summary-1", result.getStateView().getConversation().getSummaries().get(0).getSummaryId());
    }

    @Test
    public void refresh_without_planner_reuses_previous_context_selections() {
        ContextCandidateBundleVO candidates = ContextCandidateBundleVO.builder()
                .recentMessages(List.of())
                .fixedRecentMessages(List.of())
                .sessionSummaries(List.of())
                .artifactCandidates(List.of())
                .memoryCandidates(List.of(MemoryCandidateVO.builder()
                        .memoryId("memory-1")
                        .memoryType("LONG_TERM_MEMORY")
                        .summary("用户的称呼或昵称是小美。")
                        .build()))
                .evidenceCandidates(List.of())
                .availableCapabilities(List.of())
                .tokenBudget(TokenBudgetVO.builder().maxStateViewTokens(6000).maxArtifactInlineChars(1000).build())
                .build();

        ContextPlannerHandlingResult result = materializingHandler().refreshWithoutPlanner(candidates, List.of(ContextSelectionVO.builder()
                .sourceType("MEMORY")
                .sourceId("memory-1")
                .contextLevel(ContextLevelEnumVO.FULL_TEXT)
                .build()));

        Assert.assertEquals(1, result.getStateView().getMemoryPack().size());
        Assert.assertEquals("memory-1", result.getStateView().getMemoryPack().get(0).getMemoryId());
    }

    @Test
    public void refresh_without_planner_always_includes_runtime_evidence() {
        ContextCandidateBundleVO candidates = ContextCandidateBundleVO.builder()
                .recentMessages(List.of())
                .fixedRecentMessages(List.of())
                .sessionSummaries(List.of())
                .artifactCandidates(List.of())
                .memoryCandidates(List.of(MemoryCandidateVO.builder()
                        .memoryId("memory-1")
                        .memoryType("LONG_TERM_MEMORY")
                        .summary("User prefers concise answers.")
                        .build()))
                .evidenceCandidates(List.of(EvidenceCandidateVO.builder()
                        .evidenceId("evidence-tool-1")
                        .evidenceType("TOOL")
                        .sourceRef("tool-call-1")
                        .summary("Tool action succeeded: C:/Users/hp/Desktop")
                        .build()))
                .availableCapabilities(List.of())
                .tokenBudget(TokenBudgetVO.builder().maxStateViewTokens(6000).maxArtifactInlineChars(1000).build())
                .build();

        ContextPlannerHandlingResult result = materializingHandler().refreshWithoutPlanner(candidates, List.of(ContextSelectionVO.builder()
                .sourceType("MEMORY")
                .sourceId("memory-1")
                .contextLevel(ContextLevelEnumVO.FULL_TEXT)
                .build()));

        Assert.assertEquals(1, result.getStateView().getMemoryPack().size());
        Assert.assertEquals(1, result.getStateView().getEvidencePack().size());
        Assert.assertEquals("evidence-tool-1", result.getStateView().getEvidencePack().get(0).getEvidenceId());
        Assert.assertTrue(result.getStateView().getEvidencePack().get(0).getSummary().contains("Desktop"));
    }

    private ContextPlannerStatusHandler handler() {
        return new ContextPlannerStatusHandler(null, new MainAgentStateViewBuilder());
    }

    private ContextPlannerStatusHandler materializingHandler() {
        ContextTokenEstimator estimator = new ContextTokenEstimator();
        ContextMaterializer materializer = new ContextMaterializer(
                new ContextSelectionValidator(),
                null,
                new EvidencePackBuilder(),
                new ContextBudgetManager(estimator),
                new MainAgentStateViewBuilder());
        return new ContextPlannerStatusHandler(materializer, new MainAgentStateViewBuilder());
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

    private ContextCandidateBundleVO candidatesWithSummary() {
        return ContextCandidateBundleVO.builder()
                .recentMessages(List.of())
                .fixedRecentMessages(List.of())
                .sessionSummaries(List.of(SummaryCandidateVO.builder()
                        .summaryId("turn-summary-1")
                        .turnId("turn-1")
                        .summary("用户刚刚要求写一个发生在西安城墙和大雁塔附近的故事。")
                        .build()))
                .artifactCandidates(List.of())
                .memoryCandidates(List.of())
                .evidenceCandidates(List.of())
                .availableCapabilities(List.of())
                .tokenBudget(TokenBudgetVO.builder().maxStateViewTokens(6000).maxArtifactInlineChars(1000).build())
                .build();
    }
}
