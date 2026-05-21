package yhx.com.test.domain.agent.context;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextMaterializationCommand;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MessageCandidateVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.service.artifact.ArtifactPayloadLoader;
import yhx.com.domain.agent.service.context.ContextBudgetManager;
import yhx.com.domain.agent.service.context.ContextCandidatePreselector;
import yhx.com.domain.agent.service.context.ContextMaterializer;
import yhx.com.domain.agent.service.context.ContextSelectionValidator;
import yhx.com.domain.agent.service.context.ContextTokenEstimator;
import yhx.com.domain.agent.service.context.MainAgentStateViewBuilder;
import yhx.com.domain.agent.service.evidence.EvidencePackBuilder;
import yhx.com.test.domain.agent.context.support.FakeContextRepositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ContextMaterializationTest {

    @Test
    public void metadata_only_artifact_does_not_load_body() {
        MainAgentStateViewVO stateView = materialize(ContextLevelEnumVO.METADATA_ONLY, 1000);

        Assert.assertNull(stateView.getArtifactContent().get(0).getContent());
        Assert.assertTrue(stateView.getArtifactContent().get(0).getChunks().isEmpty());
    }

    @Test
    public void full_text_artifact_loads_payload_within_budget() {
        MainAgentStateViewVO stateView = materialize(ContextLevelEnumVO.FULL_TEXT, 1000);

        Assert.assertTrue(stateView.getArtifactContent().get(0).getContent().contains("RAG article body"));
    }

    @Test
    public void oversized_full_text_downgrades_to_chunked_context() {
        MainAgentStateViewVO stateView = materialize(ContextLevelEnumVO.FULL_TEXT, 20);

        Assert.assertEquals(ContextLevelEnumVO.CHUNKED_CONTEXT, stateView.getArtifactContent().get(0).getContextLevel());
        Assert.assertFalse(stateView.getArtifactContent().get(0).getChunks().isEmpty());
    }

    @Test
    public void state_view_includes_user_clarifications() {
        FakeContextRepositories repos = fixture();
        ContextPreparationCommand command = ContextPreparationCommand.builder()
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .agentId("agent-1")
                .userMessageId("msg-current")
                .userInput("write about mcp")
                .runtimeFacts(Map.of("userClarifications", List.of(UserClarificationVO.builder()
                        .question("Which MCP?")
                        .answerType("OPTION")
                        .selectedOptionId("mcp-software")
                        .value(Map.of("topic", "mcp software"))
                        .build())))
                .build();
        ContextCandidateBundleVO candidates = new ContextCandidatePreselector(repos, repos, repos, repos).buildCandidates(command);

        MainAgentStateViewVO stateView = new MainAgentStateViewBuilder().build(yhx.com.domain.agent.model.valobj.context.MainAgentStateViewBuildCommand.builder()
                .candidates(candidates)
                .artifactContent(List.of())
                .memoryPack(List.of())
                .evidencePack(List.of())
                .tokenBudget(candidates.getTokenBudget())
                .build());

        Assert.assertEquals("mcp-software", stateView.getUserClarifications().get(0).getSelectedOptionId());
    }

    @Test
    public void materializer_keeps_fixed_recent_messages_and_only_selected_summaries() {
        ContextTokenEstimator estimator = new ContextTokenEstimator();
        ContextCandidateBundleVO candidates = ContextCandidateBundleVO.builder()
                .fixedRecentMessages(List.of(MessageCandidateVO.builder()
                        .messageId("msg-fixed-1")
                        .role("USER")
                        .summary("fixed recent turn")
                        .build()))
                .recentMessages(List.of())
                .sessionSummaries(List.of(
                        SummaryCandidateVO.builder().summaryId("summary-1").summary("not selected").build(),
                        SummaryCandidateVO.builder().summaryId("summary-2").summary("selected older summary").build()))
                .artifactCandidates(List.of())
                .memoryCandidates(List.of())
                .evidenceCandidates(List.of())
                .tokenBudget(TokenBudgetVO.builder().maxStateViewTokens(6000).maxArtifactInlineChars(1000).build())
                .build();
        ContextMaterializer materializer = new ContextMaterializer(
                new ContextSelectionValidator(),
                null,
                new EvidencePackBuilder(),
                new ContextBudgetManager(estimator),
                new MainAgentStateViewBuilder());

        MainAgentStateViewVO stateView = materializer.materialize(ContextMaterializationCommand.builder()
                .candidates(candidates)
                .forcedSelections(List.of(ContextSelectionVO.builder()
                        .sourceType("TURN_SUMMARY")
                        .sourceId("summary-2")
                        .contextLevel(ContextLevelEnumVO.SUMMARY_ONLY)
                        .build()))
                .tokenBudget(candidates.getTokenBudget())
                .build());

        Assert.assertEquals("msg-fixed-1", stateView.getConversation().getRecentMessages().get(0).getMessageId());
        Assert.assertEquals(1, stateView.getConversation().getSummaries().size());
        Assert.assertEquals("summary-2", stateView.getConversation().getSummaries().get(0).getSummaryId());
    }

    private MainAgentStateViewVO materialize(ContextLevelEnumVO level, int maxInlineChars) {
        FakeContextRepositories repos = fixture();
        ContextCandidateBundleVO candidates = new ContextCandidatePreselector(repos, repos, repos, repos)
                .buildCandidates(ContextPreparationCommand.builder()
                        .runId("run-1")
                        .sessionId("session-1")
                        .userId("user-1")
                        .agentId("agent-1")
                        .userMessageId("msg-current")
                        .userInput("rewrite RAG article")
                        .artifactSeeds(List.of(article()))
                        .tokenBudget(TokenBudgetVO.builder().maxStateViewTokens(6000).maxArtifactInlineChars(maxInlineChars).build())
                        .build());
        ContextTokenEstimator estimator = new ContextTokenEstimator();
        ContextMaterializer materializer = new ContextMaterializer(
                new ContextSelectionValidator(),
                new ArtifactPayloadLoader(repos, estimator),
                new EvidencePackBuilder(),
                new ContextBudgetManager(estimator),
                new MainAgentStateViewBuilder());
        return materializer.materialize(ContextMaterializationCommand.builder()
                .candidates(candidates)
                .forcedSelections(List.of(ContextSelectionVO.builder()
                        .sourceType("ARTIFACT")
                        .sourceId("artifact-1")
                        .contextLevel(level)
                        .build()))
                .tokenBudget(candidates.getTokenBudget())
                .build());
    }

    private AgentArtifactEntity article() {
        return AgentArtifactEntity.builder()
                .artifactId("artifact-1")
                .artifactType("ARTICLE")
                .title("RAG Article")
                .summary("RAG summary")
                .contentRef("payload-artifact")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private FakeContextRepositories fixture() {
        FakeContextRepositories repos = new FakeContextRepositories();
        repos.payloads.put("payload-artifact", AgentPayloadEntity.builder()
                .payloadId("payload-artifact")
                .payloadType(PayloadTypeEnumVO.ARTIFACT_CONTENT)
                .content("RAG article body with enough text for context materialization test.")
                .preview("RAG article preview")
                .build());
        return repos;
    }
}
