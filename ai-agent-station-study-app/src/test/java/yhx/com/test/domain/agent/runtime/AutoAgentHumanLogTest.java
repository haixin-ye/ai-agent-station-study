package yhx.com.test.domain.agent.runtime;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.ConversationViewVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedMemoryVO;
import yhx.com.domain.agent.model.valobj.context.MemoryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.MessageCandidateVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;
import yhx.com.domain.agent.service.observability.AutoAgentHumanLog;

import java.util.List;

public class AutoAgentHumanLogTest {

    @Test
    public void candidate_summary_is_human_readable() {
        ContextCandidateBundleVO candidates = ContextCandidateBundleVO.builder()
                .fixedRecentMessages(List.of(MessageCandidateVO.builder().messageId("m1").build()))
                .sessionSummaries(List.of(SummaryCandidateVO.builder()
                        .summaryId("summary-1")
                        .summary("用户之前说明家乡是西安。")
                        .build()))
                .memoryCandidates(List.of(MemoryCandidateVO.builder()
                        .memoryId("memory-1")
                        .memoryType("LONG_TERM_MEMORY")
                        .summary("用户家乡是西安。")
                        .build()))
                .build();

        String text = AutoAgentHumanLog.candidateSummary(candidates);

        Assert.assertTrue(text.contains("候选收集完成"));
        Assert.assertTrue(text.contains("固定近轮全文=1"));
        Assert.assertTrue(text.contains("会话摘要=1"));
        Assert.assertTrue(text.contains("长期记忆=1"));
        Assert.assertTrue(text.contains("1. LONG_TERM_MEMORY"));
        Assert.assertTrue(text.contains("用户家乡是西安"));
    }

    @Test
    public void planner_selection_summary_shows_selected_ids_and_reasons() {
        String text = AutoAgentHumanLog.plannerSelectionSummary(List.of(ContextSelectionVO.builder()
                .sourceType("SESSION_SUMMARY")
                .sourceId("turn-summary-1")
                .contextLevel(ContextLevelEnumVO.FULL_TEXT)
                .priority(1)
                .confidence(0.82)
                .reason("用户提到刚刚的故事，需要注入该轮摘要。")
                .build()));

        Assert.assertTrue(text.contains("SESSION_SUMMARY:turn-summary-1"));
        Assert.assertTrue(text.contains("注入等级=FULL_TEXT"));
        Assert.assertTrue(text.contains("1. SESSION_SUMMARY"));
        Assert.assertTrue(text.contains("用户提到刚刚的故事"));
    }

    @Test
    public void state_view_summary_shows_injected_context_details() {
        MainAgentStateViewVO stateView = MainAgentStateViewVO.builder()
                .conversation(ConversationViewVO.builder()
                        .recentMessages(List.of(MessageCandidateVO.builder()
                                .messageId("msg-1")
                                .turnId("turn-1")
                                .role("USER")
                                .summary("用户要求介绍故事中的地标。")
                                .build()))
                        .summaries(List.of(SummaryCandidateVO.builder()
                                .summaryId("turn-summary-1")
                                .turnId("turn-1")
                                .summary("故事中出现了西安城墙、钟楼和大雁塔。")
                                .build()))
                        .build())
                .memoryPack(List.of(MaterializedMemoryVO.builder()
                        .memoryId("memory-1")
                        .memoryType("LONG_TERM_MEMORY")
                        .summary("用户家乡是西安。")
                        .build()))
                .build();

        String text = AutoAgentHumanLog.stateViewSummary(stateView);

        Assert.assertTrue(text.contains("MainAgentStateView 已注入上下文"));
        Assert.assertTrue(text.contains("最近对话=1"));
        Assert.assertTrue(text.contains("会话摘要=1"));
        Assert.assertTrue(text.contains("注入摘要明细"));
        Assert.assertTrue(text.contains("1. USER:msg-1"));
        Assert.assertTrue(text.contains("turn-summary-1"));
        Assert.assertTrue(text.contains("用户家乡是西安"));
    }

    @Test
    public void context_planner_result_explains_summary_filter_reason() {
        ContextCandidateBundleVO candidates = ContextCandidateBundleVO.builder()
                .fixedRecentMessages(List.of(MessageCandidateVO.builder()
                        .messageId("msg-1")
                        .turnId("turn-1")
                        .role("USER")
                        .summary("fixed full text")
                        .build()))
                .sessionSummaries(List.of(
                        SummaryCandidateVO.builder()
                                .summaryId("summary-1")
                                .turnId("turn-1")
                                .summary("已经在固定近轮里的摘要。")
                                .build(),
                        SummaryCandidateVO.builder()
                                .summaryId("summary-2")
                                .turnId("turn-2")
                                .summary("未选择的摘要。")
                                .build()))
                .build();
        yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult result =
                yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult.builder()
                        .effectiveSelections(List.of(ContextSelectionVO.builder()
                                .sourceType("SESSION_SUMMARY")
                                .sourceId("summary-1")
                                .contextLevel(ContextLevelEnumVO.SUMMARY_PLUS_SNIPPET)
                                .build()))
                        .stateView(MainAgentStateViewVO.builder()
                                .conversation(ConversationViewVO.builder()
                                        .recentMessages(candidates.getFixedRecentMessages())
                                        .summaries(List.of())
                                        .build())
                                .build())
                        .build();

        String text = AutoAgentHumanLog.plannerMaterializationSummary(candidates, result);

        Assert.assertTrue(text.contains("被固定近轮全文覆盖"));
        Assert.assertTrue(text.contains("summary-2"));
        Assert.assertTrue(text.contains("未选择"));
    }
}
