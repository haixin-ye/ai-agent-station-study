package yhx.com.test.domain.agent.context;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.MessageRoleEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.service.context.ContextCandidatePreselector;
import yhx.com.test.domain.agent.context.support.FakeContextRepositories;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ContextCandidatePreselectorTest {

    @Test
    public void build_candidates_includes_recent_messages_artifacts_memories_evidence() {
        FakeContextRepositories repos = fixture();

        ContextCandidateBundleVO bundle = preselector(repos).buildCandidates(command(List.of(article("artifact-1", "RAG Article", "payload-artifact"))));

        Assert.assertEquals(1, bundle.getRecentMessages().size());
        Assert.assertEquals(1, bundle.getArtifactCandidates().size());
        Assert.assertEquals(1, bundle.getMemoryCandidates().size());
        Assert.assertEquals(1, bundle.getEvidenceCandidates().size());
    }

    @Test
    public void artifact_candidates_are_ranked_by_alias_title_and_recency() {
        FakeContextRepositories repos = fixture();

        ContextCandidateBundleVO bundle = preselector(repos).buildCandidates(command(List.of(
                article("artifact-1", "Other", "payload-artifact"),
                article("artifact-2", "RAG Article", "payload-artifact"))));

        Assert.assertEquals("artifact-2", bundle.getArtifactCandidates().get(0).getArtifactId());
    }

    @Test
    public void artifact_candidates_can_load_from_repository() {
        FakeContextRepositories repos = fixture();
        repos.artifacts.put("artifact-1", article("artifact-1", "RAG Article", "payload-artifact"));

        ContextCandidateBundleVO bundle = new ContextCandidatePreselector(repos, repos, repos, repos, repos)
                .buildCandidates(command(List.of()));

        Assert.assertEquals("artifact-1", bundle.getArtifactCandidates().get(0).getArtifactId());
    }

    @Test
    public void candidate_bundle_does_not_include_full_artifact_body() {
        FakeContextRepositories repos = fixture();

        ContextCandidateBundleVO bundle = preselector(repos).buildCandidates(command(List.of(article("artifact-1", "RAG Article", "payload-artifact"))));

        Assert.assertNull(bundle.getArtifactCandidates().get(0).getStatus());
        Assert.assertFalse(bundle.toString().contains("FULL_ARTIFACT_BODY_SHOULD_NOT_APPEAR"));
    }

    @Test
    public void message_content_ref_loads_bounded_visible_content_instead_of_short_preview() {
        FakeContextRepositories repos = fixture();

        ContextCandidateBundleVO bundle = preselector(repos).buildCandidates(command(List.of()));

        Assert.assertEquals("FULL_MESSAGE_BODY_SHOULD_NOW_BE_VISIBLE_TO_CONTEXT", bundle.getRecentMessages().get(0).getSummary());
    }

    @Test
    public void message_context_is_truncated_when_payload_is_long() {
        FakeContextRepositories repos = fixture();
        String longContent = "A".repeat(1700);
        repos.payloads.get("payload-message").setContent(longContent);

        ContextCandidateBundleVO bundle = preselector(repos).buildCandidates(command(List.of()));

        String summary = bundle.getRecentMessages().get(0).getSummary();
        Assert.assertEquals(1615, summary.length());
        Assert.assertTrue(summary.endsWith("... [truncated]"));
    }

    @Test
    public void runtime_user_clarifications_are_preserved_as_candidates() {
        FakeContextRepositories repos = fixture();
        UserClarificationVO clarification = UserClarificationVO.builder()
                .pendingId("pending-1")
                .question("Which MCP?")
                .answerType("OPTION")
                .selectedOptionId("mcp-software")
                .value(Map.of("topic", "mcp software"))
                .build();

        ContextPreparationCommand command = command(List.of());
        command.setRuntimeFacts(Map.of("userClarifications", List.of(clarification)));

        ContextCandidateBundleVO bundle = preselector(repos).buildCandidates(command);

        Assert.assertEquals(1, bundle.getUserClarifications().size());
        Assert.assertEquals("mcp-software", bundle.getUserClarifications().get(0).getSelectedOptionId());
    }

    @Test
    public void turn_window_uses_latest_six_full_turns_and_previous_six_summaries() {
        FakeContextRepositories repos = fixture();
        repos.messages.clear();
        repos.payloads.clear();
        for (int i = 1; i <= 13; i++) {
            String turnId = "turn-" + i;
            repos.turns.add(AgentTurnEntity.builder()
                    .turnId(turnId)
                    .sessionId("session-1")
                    .runId("run-" + i)
                    .turnNo((long) i)
                    .userMessageId("msg-user-" + i)
                    .assistantMessageId("msg-assistant-" + i)
                    .userPayloadRef("payload-user-" + i)
                    .assistantPayloadRef("payload-assistant-" + i)
                    .status("COMPLETED")
                    .completedAt(LocalDateTime.now().plusMinutes(i))
                    .build());
            repos.payloads.put("payload-user-" + i, AgentPayloadEntity.builder().payloadId("payload-user-" + i).content("user " + i).build());
            repos.payloads.put("payload-assistant-" + i, AgentPayloadEntity.builder().payloadId("payload-assistant-" + i).content("assistant " + i).build());
            repos.turnSummaries.add(AgentTurnSummaryEntity.builder()
                    .summaryId("summary-" + i)
                    .turnId(turnId)
                    .sessionId("session-1")
                    .summaryRef("payload-summary-" + i)
                    .status("ACTIVE")
                    .build());
            repos.payloads.put("payload-summary-" + i, AgentPayloadEntity.builder().payloadId("payload-summary-" + i).content("summary " + i).build());
        }

        ContextCandidateBundleVO bundle = new ContextCandidatePreselector(repos, repos, repos, repos, repos, repos, repos)
                .buildCandidates(command(List.of()));

        Assert.assertEquals(12, bundle.getRecentMessages().size());
        Assert.assertEquals(6, bundle.getSessionSummaries().size());
        Assert.assertEquals("msg-user-8", bundle.getRecentMessages().get(0).getMessageId());
        Assert.assertEquals("msg-assistant-13", bundle.getRecentMessages().get(11).getMessageId());
        Assert.assertEquals("summary-2", bundle.getSessionSummaries().get(0).getSummaryId());
        Assert.assertEquals("summary 7", bundle.getSessionSummaries().get(5).getSummary());
    }

    @Test
    public void recalled_turn_summary_exposes_artifact_reference_without_inlining_artifact_body_to_planner() {
        FakeContextRepositories repos = fixture();
        repos.messages.clear();
        repos.payloads.clear();
        repos.artifacts.put("artifact-rag-1", article("artifact-rag-1", "RAG Article", "payload-artifact-rag"));
        repos.payloads.put("payload-artifact-rag", AgentPayloadEntity.builder()
                .payloadId("payload-artifact-rag")
                .payloadType(PayloadTypeEnumVO.ARTIFACT_CONTENT)
                .content("FULL_RAG_ARTICLE_BODY_SHOULD_NOT_APPEAR_IN_CANDIDATES")
                .preview("rag artifact preview")
                .build());
        repos.turnSummaries.add(AgentTurnSummaryEntity.builder()
                .summaryId("summary-rag-old")
                .turnId("turn-rag-old")
                .sessionId("session-1")
                .summaryRef("payload-summary-rag-old")
                .artifactRefsJson("[\"artifact-rag-1\"]")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now().minusDays(3))
                .build());
        repos.payloads.put("payload-summary-rag-old", AgentPayloadEntity.builder()
                .payloadId("payload-summary-rag-old")
                .content("User created a RAG article draft and asked to keep it for later editing.")
                .build());

        ContextCandidateBundleVO bundle = new ContextCandidatePreselector(repos, repos, repos, repos, repos, repos, repos)
                .buildCandidates(command(List.of()));

        Assert.assertTrue(bundle.getSessionSummaries().stream()
                .anyMatch(summary -> "summary-rag-old".equals(summary.getSummaryId())
                        && summary.getArtifactRefs().contains("artifact-rag-1")));
        Assert.assertTrue(bundle.getArtifactCandidates().stream()
                .anyMatch(artifact -> "artifact-rag-1".equals(artifact.getArtifactId())));
        Assert.assertFalse(bundle.toString().contains("FULL_RAG_ARTICLE_BODY_SHOULD_NOT_APPEAR_IN_CANDIDATES"));
    }

    private ContextCandidatePreselector preselector(FakeContextRepositories repos) {
        return new ContextCandidatePreselector(repos, repos, repos, repos);
    }

    private ContextPreparationCommand command(List<AgentArtifactEntity> artifacts) {
        return ContextPreparationCommand.builder()
                .runId("run-1")
                .sessionId("session-1")
                .userId("user-1")
                .agentId("agent-1")
                .userMessageId("msg-current")
                .userInput("publish RAG Article")
                .artifactSeeds(artifacts)
                .recentMessageLimit(5)
                .artifactCandidateLimit(5)
                .memoryCandidateLimit(5)
                .evidenceCandidateLimit(5)
                .build();
    }

    private AgentArtifactEntity article(String artifactId, String title, String contentRef) {
        return AgentArtifactEntity.builder()
                .artifactId(artifactId)
                .sessionId("session-1")
                .artifactType("ARTICLE")
                .title(title)
                .summary("article summary")
                .contentRef(contentRef)
                .version(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private FakeContextRepositories fixture() {
        FakeContextRepositories repos = new FakeContextRepositories();
        repos.payloads.put("payload-message", AgentPayloadEntity.builder()
                .payloadId("payload-message")
                .payloadType(PayloadTypeEnumVO.TEXT)
                .content("FULL_MESSAGE_BODY_SHOULD_NOW_BE_VISIBLE_TO_CONTEXT")
                .preview("message preview")
                .build());
        repos.payloads.put("payload-artifact", AgentPayloadEntity.builder()
                .payloadId("payload-artifact")
                .payloadType(PayloadTypeEnumVO.ARTIFACT_CONTENT)
                .content("FULL_ARTIFACT_BODY_SHOULD_NOT_APPEAR")
                .preview("artifact preview")
                .build());
        repos.messages.add(AgentMessageEntity.builder()
                .messageId("msg-1")
                .sessionId("session-1")
                .role(MessageRoleEnumVO.USER)
                .contentRef("payload-message")
                .visibleToUser(true)
                .createdAt(LocalDateTime.now())
                .build());
        repos.memories.add(AgentMemoryEntity.builder()
                .memoryId("memory-1")
                .summary("RAG topic preference")
                .score(BigDecimal.ONE)
                .build());
        repos.evidence.add(AgentEvidenceEntity.builder()
                .evidenceId("evidence-1")
                .runId("run-1")
                .evidenceType("RAG")
                .summary("RAG evidence summary")
                .build());
        return repos;
    }
}
