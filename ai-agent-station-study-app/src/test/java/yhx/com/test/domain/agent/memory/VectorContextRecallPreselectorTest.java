package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.enums.memory.ContextCandidateSourceChannelEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.service.memory.NoopVectorMemoryRepository;
import yhx.com.domain.agent.service.memory.VectorContextRecallPreselector;
import yhx.com.test.domain.agent.context.support.FakeContextRepositories;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VectorContextRecallPreselectorTest {

    @Test
    public void noop_vector_repository_returns_no_hits() {
        NoopVectorMemoryRepository repository = new NoopVectorMemoryRepository();

        Assert.assertTrue(repository.search(VectorRecallQueryVO.builder().queryText("mcp").build()).isEmpty());
        Assert.assertNull(repository.upsert(null));
    }

    @Test
    public void vector_hits_are_resolved_back_to_mysql_candidates_before_planner() {
        FakeContextRepositories repos = fixture();
        FakeVectorMemoryRepository vector = new FakeVectorMemoryRepository(List.of(
                hit(VectorCollectionTypeEnumVO.TURN_SUMMARY, VectorSourceTypeEnumVO.TURN_SUMMARY, "summary-1", 0.91),
                hit(VectorCollectionTypeEnumVO.LONG_TERM_MEMORY, VectorSourceTypeEnumVO.LONG_TERM_MEMORY, "memory-1", 0.77)));

        ContextCandidateBundleVO bundle = new VectorContextRecallPreselector(vector, repos, repos, repos, repos)
                .recall(ContextPreparationCommand.builder()
                        .userId("user-1")
                        .sessionId("session-1")
                        .userInput("把上次那篇 MCP 文章改得适合初学者")
                        .build());

        Assert.assertEquals(1, vector.queries.size());
        Assert.assertEquals("把上次那篇 MCP 文章改得适合初学者", vector.queries.get(0).getQueryText());
        Assert.assertEquals(1, bundle.getSessionSummaries().size());
        Assert.assertEquals("summary payload about MCP article", bundle.getSessionSummaries().get(0).getSummary());
        Assert.assertEquals(ContextCandidateSourceChannelEnumVO.VECTOR_SEMANTIC.name(), bundle.getSessionSummaries().get(0).getSourceChannel());
        Assert.assertTrue(bundle.getArtifactCandidates().isEmpty());
        Assert.assertEquals(1, bundle.getMemoryCandidates().size());
        Assert.assertEquals("memory-1", bundle.getMemoryCandidates().get(0).getMemoryId());
        Assert.assertFalse(vector.queries.get(0).getFilter().getCollectionTypes().contains(VectorCollectionTypeEnumVO.ARTIFACT_SUMMARY));
        Assert.assertFalse(vector.queries.get(0).getFilter().getCollectionTypes().contains(VectorCollectionTypeEnumVO.ARTIFACT_CHUNK));
    }

    @Test
    public void unresolved_vector_hits_are_dropped() {
        FakeContextRepositories repos = fixture();
        FakeVectorMemoryRepository vector = new FakeVectorMemoryRepository(List.of(
                hit(VectorCollectionTypeEnumVO.TURN_SUMMARY, VectorSourceTypeEnumVO.TURN_SUMMARY, "missing-summary", 0.91),
                hit(VectorCollectionTypeEnumVO.ARTIFACT_SUMMARY, VectorSourceTypeEnumVO.ARTIFACT_SUMMARY, "missing-artifact", 0.88),
                hit(VectorCollectionTypeEnumVO.USER_PREFERENCE, VectorSourceTypeEnumVO.USER_PREFERENCE, "missing-memory", 0.77)));

        ContextCandidateBundleVO bundle = new VectorContextRecallPreselector(vector, repos, repos, repos, repos)
                .recall(ContextPreparationCommand.builder()
                        .userId("user-1")
                        .sessionId("session-1")
                        .userInput("mcp")
                        .build());

        Assert.assertTrue(bundle.getSessionSummaries().isEmpty());
        Assert.assertTrue(bundle.getArtifactCandidates().isEmpty());
        Assert.assertTrue(bundle.getMemoryCandidates().isEmpty());
    }

    @Test
    public void artifact_chunk_hits_are_ignored_by_memory_recall() {
        FakeContextRepositories repos = fixture();
        FakeVectorMemoryRepository vector = new FakeVectorMemoryRepository(List.of(VectorRecallHitVO.builder()
                .collectionType(VectorCollectionTypeEnumVO.ARTIFACT_CHUNK)
                .sourceType(VectorSourceTypeEnumVO.ARTIFACT_CHUNK)
                .sourceId("artifact-1:chunk:002")
                .score(0.93)
                .snippet("MCP tools and resources detailed section")
                .metadata(Map.of("artifactId", "artifact-1", "chunkNo", 2))
                .build()));

        ContextCandidateBundleVO bundle = new VectorContextRecallPreselector(vector, repos, repos, repos, repos)
                .recall(ContextPreparationCommand.builder()
                        .userId("user-1")
                        .sessionId("session-1")
                        .userInput("MCP tools resources 那段")
                        .build());

        Assert.assertTrue(bundle.getArtifactCandidates().isEmpty());
    }

    private FakeContextRepositories fixture() {
        FakeContextRepositories repos = new FakeContextRepositories();
        repos.turnSummaries.add(AgentTurnSummaryEntity.builder()
                .summaryId("summary-1")
                .turnId("turn-1")
                .sessionId("session-1")
                .summaryRef("payload-summary-1")
                .artifactRefsJson("[\"artifact-1\"]")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build());
        repos.payloads.put("payload-summary-1", AgentPayloadEntity.builder()
                .payloadId("payload-summary-1")
                .content("summary payload about MCP article")
                .build());
        repos.artifacts.put("artifact-1", AgentArtifactEntity.builder()
                .artifactId("artifact-1")
                .sessionId("session-1")
                .artifactType("ARTICLE")
                .title("MCP knowledge article")
                .summary("artifact summary")
                .contentRef("payload-artifact-1")
                .version(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        repos.memories.add(AgentMemoryEntity.builder()
                .memoryId("memory-1")
                .userId("user-1")
                .memoryType("LONG_TERM_MEMORY")
                .summary("User is building AutoAgent memory recall.")
                .score(BigDecimal.ONE)
                .build());
        return repos;
    }

    private VectorRecallHitVO hit(VectorCollectionTypeEnumVO collectionType,
                                  VectorSourceTypeEnumVO sourceType,
                                  String sourceId,
                                  double score) {
        return VectorRecallHitVO.builder()
                .collectionType(collectionType)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .score(score)
                .summary("vector summary")
                .build();
    }

    private static class FakeVectorMemoryRepository implements IVectorMemoryRepository {

        private final List<VectorRecallHitVO> hits;
        private final List<VectorRecallQueryVO> queries = new ArrayList<>();

        private FakeVectorMemoryRepository(List<VectorRecallHitVO> hits) {
            this.hits = hits;
        }

        @Override
        public String upsert(VectorIndexRecordVO record) {
            return record == null ? null : record.getVectorId();
        }

        @Override
        public List<VectorRecallHitVO> search(VectorRecallQueryVO query) {
            queries.add(query);
            return hits;
        }

        @Override
        public void disable(VectorCollectionTypeEnumVO collectionType, String sourceId) {
        }
    }
}
