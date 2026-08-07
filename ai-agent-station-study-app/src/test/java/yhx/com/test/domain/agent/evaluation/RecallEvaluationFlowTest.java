package yhx.com.test.domain.agent.evaluation;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IVectorIndexRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentVectorIndexEntity;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.evaluation.DetailedRecallResultVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallExecutionOptionsVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.service.memory.VectorContextRecallPreselector;
import yhx.com.domain.agent.service.memory.LongTermMemoryService;
import yhx.com.domain.agent.service.memory.MemoryVectorIndexingService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RecallEvaluationFlowTest {

    @Test
    public void detailed_memory_recall_uses_run_parameters_and_exact_dataset_filter() {
        CapturingVectorRepository vectors = new CapturingVectorRepository();
        VectorContextRecallPreselector preselector = new VectorContextRecallPreselector(vectors, null, null, null, null);

        DetailedRecallResultVO result = preselector.recallDetailed(
                ContextPreparationCommand.builder()
                        .userId("eval-user:dataset-1")
                        .sessionId("eval-session:dataset-1")
                        .userInput("慢节奏旅行偏好")
                        .build(),
                RecallExecutionOptionsVO.builder()
                        .topK(17)
                        .minScore(0.61D)
                        .collectionTypes(List.of(VectorCollectionTypeEnumVO.LONG_TERM_MEMORY))
                        .metadataFilters(Map.of("evalDatasetId", "dataset-1"))
                        .build());

        Assert.assertEquals(1, vectors.queries.size());
        VectorRecallQueryVO query = vectors.queries.get(0);
        Assert.assertEquals(Integer.valueOf(17), query.getTopK());
        Assert.assertEquals(Double.valueOf(0.61D), query.getMinScore());
        Assert.assertEquals(List.of(VectorCollectionTypeEnumVO.LONG_TERM_MEMORY), query.getFilter().getCollectionTypes());
        Assert.assertEquals(Map.of("evalDatasetId", "dataset-1"), query.getFilter().getMetadataFilters());
        Assert.assertEquals(1, result.getVectorHits().size());
        Assert.assertNotNull(result.getCandidateBundle());
        Assert.assertTrue(result.getElapsedMs() >= 0L);
    }

    @Test
    public void external_memory_ingestion_persists_payload_source_and_dataset_vector_metadata() {
        FakePayloadRepository payloads = new FakePayloadRepository();
        FakeMemoryRepository memories = new FakeMemoryRepository();
        CapturingVectorRepository vectors = new CapturingVectorRepository();
        MemoryVectorIndexingService indexing = new MemoryVectorIndexingService(vectors, new FakeVectorIndexRepository(), payloads);
        LongTermMemoryService service = new LongTermMemoryService(payloads, memories, indexing);

        AgentMemoryEntity saved = service.ingestExternalMemory(
                AgentMemoryEntity.builder()
                        .memoryId("memory-eval-1")
                        .userId("eval-user:dataset-1")
                        .sessionId("eval-session:dataset-1")
                        .memoryType("LONG_TERM_MEMORY")
                        .summary("用户偏好慢节奏旅行")
                        .score(BigDecimal.ONE)
                        .build(),
                "用户每天最多安排两个主要景点，并保留午休时间。",
                Map.of("evalDatasetId", "dataset-1", "evalExternalId", "memory-travel-pace"));

        Assert.assertEquals("payload-1", saved.getContentRef());
        Assert.assertSame(saved, memories.saved);
        Assert.assertEquals("ACTIVE", saved.getStatus());
        Assert.assertNotNull(vectors.indexed);
        Assert.assertEquals("dataset-1", vectors.indexed.getMetadata().get("evalDatasetId"));
        Assert.assertEquals("memory-travel-pace", vectors.indexed.getMetadata().get("evalExternalId"));
    }

    private static class CapturingVectorRepository implements IVectorMemoryRepository {
        private final List<VectorRecallQueryVO> queries = new ArrayList<>();
        private VectorIndexRecordVO indexed;

        @Override
        public String upsert(VectorIndexRecordVO record) {
            indexed = record;
            return "vector-1";
        }

        @Override
        public List<VectorRecallHitVO> search(VectorRecallQueryVO query) {
            queries.add(query);
            return List.of(VectorRecallHitVO.builder()
                    .collectionType(VectorCollectionTypeEnumVO.LONG_TERM_MEMORY)
                    .sourceType(VectorSourceTypeEnumVO.LONG_TERM_MEMORY)
                    .sourceId("memory-1")
                    .score(0.91D)
                    .build());
        }

        @Override
        public void disable(VectorCollectionTypeEnumVO collectionType, String sourceId) {
        }
    }

    private static class FakePayloadRepository implements IPayloadRepository {
        private AgentPayloadEntity saved;

        @Override
        public String savePayload(AgentPayloadEntity payload) {
            saved = payload;
            payload.setPayloadId("payload-1");
            return payload.getPayloadId();
        }

        @Override
        public Optional<AgentPayloadEntity> findPayload(String payloadId) {
            return Optional.ofNullable(saved);
        }
    }

    private static class FakeMemoryRepository implements IMemoryRepository {
        private AgentMemoryEntity saved;

        @Override
        public List<AgentMemoryEntity> findMemoryCandidates(String userId, String sessionId, String query, int limit) {
            return List.of();
        }

        @Override
        public Optional<AgentMemoryEntity> findMemory(String memoryId) {
            return Optional.ofNullable(saved);
        }

        @Override
        public String saveConversationSummary(AgentConversationSummaryEntity summary) {
            return summary.getSummaryId();
        }

        @Override
        public String saveLongTermMemory(AgentMemoryEntity memory) {
            saved = memory;
            return memory.getMemoryId();
        }

        @Override
        public String recordMemoryEvent(AgentMemoryEventEntity event) {
            return event.getEventId();
        }
    }

    private static class FakeVectorIndexRepository implements IVectorIndexRepository {
        @Override
        public String saveOrUpdate(AgentVectorIndexEntity index) {
            return index.getVectorId();
        }

        @Override
        public Optional<AgentVectorIndexEntity> findBySource(String collectionType, String sourceType, String sourceId) {
            return Optional.empty();
        }

        @Override
        public void markDisabled(String collectionType, String sourceType, String sourceId) {
        }
    }
}
