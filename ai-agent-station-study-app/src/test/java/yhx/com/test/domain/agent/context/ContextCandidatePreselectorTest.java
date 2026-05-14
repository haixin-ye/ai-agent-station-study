package yhx.com.test.domain.agent.context;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.enums.persistence.MessageRoleEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.service.context.ContextCandidatePreselector;
import yhx.com.test.domain.agent.context.support.FakeContextRepositories;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    public void candidate_bundle_does_not_include_full_artifact_body() {
        FakeContextRepositories repos = fixture();

        ContextCandidateBundleVO bundle = preselector(repos).buildCandidates(command(List.of(article("artifact-1", "RAG Article", "payload-artifact"))));

        Assert.assertNull(bundle.getArtifactCandidates().get(0).getStatus());
        Assert.assertFalse(bundle.toString().contains("FULL_ARTIFACT_BODY_SHOULD_NOT_APPEAR"));
    }

    @Test
    public void message_content_ref_loads_summary_not_full_payload() {
        FakeContextRepositories repos = fixture();

        ContextCandidateBundleVO bundle = preselector(repos).buildCandidates(command(List.of()));

        Assert.assertEquals("message preview", bundle.getRecentMessages().get(0).getSummary());
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
                .content("FULL_MESSAGE_BODY_SHOULD_NOT_APPEAR")
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
