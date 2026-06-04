package yhx.com.infrastructure.adapter.port;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.vectorstore.VectorStore;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.model.valobj.rag.RagRetrievalCommandVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpringAiRagRetrieverAdapterTest {

    @Test
    public void knowledge_name_does_not_create_filter_expression() {
        SpringAiRagRetrieverAdapter adapter = new SpringAiRagRetrieverAdapter((VectorStore) null);

        String filterExpression = adapter.resolveFilterExpression(RagRetrievalCommandVO.builder()
                .knowledgeName("article-prompt-words")
                .runtimeFilters(Map.of("filterExpression", "knowledge == 'custom'"))
                .build());

        Assert.assertNull(filterExpression);
    }

    @Test
    public void explicit_filter_expression_is_ignored_for_global_rag_search() {
        SpringAiRagRetrieverAdapter adapter = new SpringAiRagRetrieverAdapter((VectorStore) null);

        String filterExpression = adapter.resolveFilterExpression(RagRetrievalCommandVO.builder()
                .knowledgeName("article-prompt-words")
                .build());

        Assert.assertNull(filterExpression);
    }

    @Test
    public void blank_knowledge_name_does_not_create_filter_expression() {
        SpringAiRagRetrieverAdapter adapter = new SpringAiRagRetrieverAdapter((VectorStore) null);

        String filterExpression = adapter.resolveFilterExpression(RagRetrievalCommandVO.builder()
                .knowledgeName(" ")
                .build());

        Assert.assertNull(filterExpression);
    }

    @Test
    public void retrieve_searchesDedicatedRagVectorCollections() {
        FakeVectorMemoryRepository repository = new FakeVectorMemoryRepository();
        repository.hits = List.of(VectorRecallHitVO.builder()
                .collectionType(VectorCollectionTypeEnumVO.RAG_CHUNK)
                .sourceType(VectorSourceTypeEnumVO.RAG_CHUNK)
                .sourceId("chunk-1")
                .summary("RAG design summary")
                .snippet("RAG chunk text")
                .score(0.88D)
                .metadata(Map.of("sourceName", "rag-design.txt", "documentId", "doc-1"))
                .build());
        SpringAiRagRetrieverAdapter adapter = new SpringAiRagRetrieverAdapter(repository);

        var hits = adapter.retrieve(RagRetrievalCommandVO.builder()
                .query("RAG 设计")
                .topK(3)
                .build());

        Assert.assertEquals(1, hits.size());
        Assert.assertEquals("RAG_CHUNK", hits.get(0).getSourceType());
        Assert.assertEquals("rag-design.txt", hits.get(0).getTitle());
        Assert.assertEquals("RAG chunk text", hits.get(0).getChunkText());
        Assert.assertEquals(List.of(VectorCollectionTypeEnumVO.RAG_DOCUMENT, VectorCollectionTypeEnumVO.RAG_CHUNK),
                repository.queries.get(0).getFilter().getCollectionTypes());
    }

    private static class FakeVectorMemoryRepository implements IVectorMemoryRepository {
        private List<VectorRecallHitVO> hits = List.of();
        private final List<VectorRecallQueryVO> queries = new ArrayList<>();

        @Override
        public String upsert(yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO record) {
            return "vector-1";
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
