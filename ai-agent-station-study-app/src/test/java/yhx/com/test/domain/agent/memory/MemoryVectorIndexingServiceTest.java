package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IVectorIndexRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentVectorIndexEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.service.memory.MemoryVectorIndexingService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MemoryVectorIndexingServiceTest {

    @Test
    public void conversation_summary_is_indexed_from_payload_content() {
        FakeVectorRepositories repositories = new FakeVectorRepositories();
        repositories.payloads.put("payload-summary", AgentPayloadEntity.builder()
                .payloadId("payload-summary")
                .content("Rolling summary: user iterated an MCP article and asked for a richer version.")
                .build());

        service(repositories).indexConversationSummary(AgentConversationSummaryEntity.builder()
                .summaryId("conversation-summary-1")
                .sessionId("session-1")
                .userId("user-1")
                .summaryRef("payload-summary")
                .messageStartSeq(10L)
                .messageEndSeq(18L)
                .build());

        Assert.assertEquals(1, repositories.vectorRecords.size());
        Assert.assertEquals(VectorCollectionTypeEnumVO.CONVERSATION_SUMMARY, repositories.vectorRecords.get(0).getCollectionType());
        Assert.assertEquals("conversation-summary-1", repositories.vectorRecords.get(0).getSourceId());
        Assert.assertEquals(10L, repositories.vectorRecords.get(0).getMetadata().get("messageStartSeq"));
        Assert.assertEquals(1, repositories.vectorIndexes.size());
        Assert.assertEquals("ACTIVE", repositories.vectorIndexes.get(0).getStatus());
    }

    @Test
    public void long_term_memory_is_indexed_into_long_term_collection() {
        FakeVectorRepositories repositories = new FakeVectorRepositories();

        service(repositories).indexMemory(AgentMemoryEntity.builder()
                .memoryId("memory-1")
                .userId("user-1")
                .sessionId("session-1")
                .memoryType("LONG_TERM_MEMORY")
                .summary("User is building a maintainable AutoAgent memory system.")
                .score(new BigDecimal("0.80"))
                .build());

        Assert.assertEquals(1, repositories.vectorRecords.size());
        Assert.assertEquals(VectorCollectionTypeEnumVO.LONG_TERM_MEMORY, repositories.vectorRecords.get(0).getCollectionType());
        Assert.assertEquals("memory-1", repositories.vectorRecords.get(0).getSourceId());
        Assert.assertEquals("LONG_TERM_MEMORY", repositories.vectorRecords.get(0).getMetadata().get("memoryType"));
    }

    @Test
    public void user_preference_memory_is_indexed_into_preference_collection() {
        FakeVectorRepositories repositories = new FakeVectorRepositories();

        service(repositories).indexMemory(AgentMemoryEntity.builder()
                .memoryId("preference-1")
                .userId("user-1")
                .sessionId("session-1")
                .memoryType("USER_PREFERENCE")
                .summary("User prefers detailed Chinese engineering explanations.")
                .score(new BigDecimal("0.90"))
                .build());

        Assert.assertEquals(1, repositories.vectorRecords.size());
        Assert.assertEquals(VectorCollectionTypeEnumVO.USER_PREFERENCE, repositories.vectorRecords.get(0).getCollectionType());
        Assert.assertEquals("preference-1", repositories.vectorRecords.get(0).getSourceId());
    }

    private MemoryVectorIndexingService service(FakeVectorRepositories repositories) {
        return new MemoryVectorIndexingService(repositories, repositories, repositories);
    }

    private static class FakeVectorRepositories implements IVectorMemoryRepository, IVectorIndexRepository, IPayloadRepository {
        private final Map<String, AgentPayloadEntity> payloads = new LinkedHashMap<>();
        private final List<VectorIndexRecordVO> vectorRecords = new ArrayList<>();
        private final List<AgentVectorIndexEntity> vectorIndexes = new ArrayList<>();

        @Override
        public String upsert(VectorIndexRecordVO record) {
            vectorRecords.add(record);
            return "vector-" + vectorRecords.size();
        }

        @Override
        public List<VectorRecallHitVO> search(VectorRecallQueryVO query) {
            return List.of();
        }

        @Override
        public void disable(VectorCollectionTypeEnumVO collectionType, String sourceId) {
        }

        @Override
        public String saveOrUpdate(AgentVectorIndexEntity index) {
            vectorIndexes.add(index);
            return "index-" + vectorIndexes.size();
        }

        @Override
        public Optional<AgentVectorIndexEntity> findBySource(String collectionType, String sourceType, String sourceId) {
            return vectorIndexes.stream()
                    .filter(index -> collectionType.equals(index.getCollectionType())
                            && sourceType.equals(index.getSourceType())
                            && sourceId.equals(index.getSourceId()))
                    .findFirst();
        }

        @Override
        public void markDisabled(String collectionType, String sourceType, String sourceId) {
        }

        @Override
        public String savePayload(AgentPayloadEntity payload) {
            payloads.put(payload.getPayloadId(), payload);
            return payload.getPayloadId();
        }

        @Override
        public Optional<AgentPayloadEntity> findPayload(String payloadId) {
            return Optional.ofNullable(payloads.get(payloadId));
        }
    }
}
