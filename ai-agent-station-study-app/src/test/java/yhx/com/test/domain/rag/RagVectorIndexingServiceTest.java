package yhx.com.test.domain.rag;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IVectorIndexRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentVectorIndexEntity;
import yhx.com.domain.agent.model.entity.rag.RagChunkEntity;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.service.rag.RagVectorIndexingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RagVectorIndexingServiceTest {

    @Test
    public void indexCodeFileSummary_writesCodeFileSummaryVectorAndIndexRecord() {
        FakeVectorMemoryRepository vectorMemoryRepository = new FakeVectorMemoryRepository();
        FakeVectorIndexRepository vectorIndexRepository = new FakeVectorIndexRepository();
        RagVectorIndexingService service = new RagVectorIndexingService(vectorMemoryRepository, vectorIndexRepository);

        service.indexDocument(RagDocumentEntity.builder()
                .documentId("doc-1")
                .userId("user-1")
                .sessionId("session-1")
                .sourceType("GIT_FILE")
                .sourceName("demo/src/App.java")
                .title("App.java")
                .summary("Application entry file")
                .contentSha256("hash-doc")
                .build(), "Application entry file and run method");

        Assert.assertEquals(1, vectorMemoryRepository.records.size());
        VectorIndexRecordVO record = vectorMemoryRepository.records.get(0);
        Assert.assertEquals(VectorCollectionTypeEnumVO.RAG_CODE_FILE_SUMMARY, record.getCollectionType());
        Assert.assertEquals(VectorSourceTypeEnumVO.RAG_CODE_FILE_SUMMARY, record.getSourceType());
        Assert.assertEquals("doc-1", record.getSourceId());
        Assert.assertEquals("user-1", record.getUserId());
        Assert.assertEquals("session-1", record.getSessionId());
        Assert.assertEquals("GIT_FILE", record.getMetadata().get("sourceType"));
        Assert.assertEquals("demo/src/App.java", record.getMetadata().get("sourceName"));
        Assert.assertEquals(1, vectorIndexRepository.indexes.size());
        Assert.assertEquals("doc-1", vectorIndexRepository.indexes.get(0).getSourceId());
        Assert.assertEquals("user-1", vectorIndexRepository.indexes.get(0).getUserId());
    }

    @Test
    public void indexFileChunk_usesFileChunkCollection() {
        FakeVectorMemoryRepository vectorMemoryRepository = new FakeVectorMemoryRepository();
        FakeVectorIndexRepository vectorIndexRepository = new FakeVectorIndexRepository();
        RagVectorIndexingService service = new RagVectorIndexingService(vectorMemoryRepository, vectorIndexRepository);

        service.indexChunk(RagChunkEntity.builder()
                .chunkId("chunk-1")
                .documentId("doc-1")
                .userId("user-1")
                .sessionId("session-1")
                .chunkNo(1)
                .chunkType("FILE_CHUNK")
                .summary("Chunk about context planner")
                .contentSha256("hash-chunk")
                .build(), "Context planner should select RAG chunks only when useful.");

        Assert.assertEquals(1, vectorMemoryRepository.records.size());
        VectorIndexRecordVO record = vectorMemoryRepository.records.get(0);
        Assert.assertEquals(VectorCollectionTypeEnumVO.RAG_FILE_CHUNK, record.getCollectionType());
        Assert.assertEquals(VectorSourceTypeEnumVO.RAG_FILE_CHUNK, record.getSourceType());
        Assert.assertEquals("chunk-1", record.getSourceId());
        Assert.assertEquals("doc-1", record.getMetadata().get("documentId"));
        Assert.assertEquals(1, record.getMetadata().get("chunkNo"));
        Assert.assertEquals(1, vectorIndexRepository.indexes.size());
        Assert.assertEquals("chunk-1", vectorIndexRepository.indexes.get(0).getSourceId());
    }

    @Test
    public void indexCodeChunk_usesCodeChunkCollection() {
        FakeVectorMemoryRepository vectorMemoryRepository = new FakeVectorMemoryRepository();
        FakeVectorIndexRepository vectorIndexRepository = new FakeVectorIndexRepository();
        RagVectorIndexingService service = new RagVectorIndexingService(vectorMemoryRepository, vectorIndexRepository);

        service.indexChunk(RagChunkEntity.builder()
                .chunkId("chunk-code-1")
                .documentId("doc-1")
                .userId("user-1")
                .sessionId("session-1")
                .chunkNo(1)
                .chunkType("CODE_CHUNK")
                .summary("run method")
                .contentSha256("hash-chunk")
                .build(), "public void run() {}");

        VectorIndexRecordVO record = vectorMemoryRepository.records.get(0);
        Assert.assertEquals(VectorCollectionTypeEnumVO.RAG_CODE_CHUNK, record.getCollectionType());
        Assert.assertEquals(VectorSourceTypeEnumVO.RAG_CODE_CHUNK, record.getSourceType());
    }

    private static class FakeVectorMemoryRepository implements IVectorMemoryRepository {
        private final List<VectorIndexRecordVO> records = new ArrayList<>();

        @Override
        public String upsert(VectorIndexRecordVO record) {
            records.add(record);
            return "vector-" + records.size();
        }

        @Override
        public List<VectorRecallHitVO> search(VectorRecallQueryVO query) {
            return List.of();
        }

        @Override
        public void disable(VectorCollectionTypeEnumVO collectionType, String sourceId) {
        }
    }

    private static class FakeVectorIndexRepository implements IVectorIndexRepository {
        private final List<AgentVectorIndexEntity> indexes = new ArrayList<>();

        @Override
        public String saveOrUpdate(AgentVectorIndexEntity index) {
            indexes.add(index);
            return "idx-" + indexes.size();
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
