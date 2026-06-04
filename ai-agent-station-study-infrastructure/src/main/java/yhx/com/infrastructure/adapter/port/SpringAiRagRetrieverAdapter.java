package yhx.com.infrastructure.adapter.port;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallFilterVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.model.valobj.rag.RagHitVO;
import yhx.com.domain.agent.model.valobj.rag.RagRetrievalCommandVO;
import yhx.com.domain.agent.service.rag.runtime.RagRetrieverPort;

import java.util.List;
import java.util.Map;

@Component
public class SpringAiRagRetrieverAdapter implements RagRetrieverPort {

    @Resource(name = "pgVectorStore")
    private VectorStore vectorStore;

    @Resource
    private IVectorMemoryRepository vectorMemoryRepository;

    public SpringAiRagRetrieverAdapter() {
    }

    public SpringAiRagRetrieverAdapter(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public SpringAiRagRetrieverAdapter(IVectorMemoryRepository vectorMemoryRepository) {
        this.vectorMemoryRepository = vectorMemoryRepository;
    }

    @Override
    public List<RagHitVO> retrieve(RagRetrievalCommandVO command) {
        validate(command);
        if (vectorMemoryRepository != null) {
            return mapVectorHits(vectorMemoryRepository.search(VectorRecallQueryVO.builder()
                    .queryText(command.getQuery())
                    .topK(resolveTopK(command))
                    .filter(VectorRecallFilterVO.builder()
                            .collectionTypes(List.of(VectorCollectionTypeEnumVO.RAG_DOCUMENT, VectorCollectionTypeEnumVO.RAG_CHUNK))
                            .build())
                    .build()));
        }
        List<Document> documents = vectorStore.similaritySearch(buildSearchRequest(command));
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return mapDocuments(documents);
    }

    SearchRequest buildSearchRequest(RagRetrievalCommandVO command) {
        return SearchRequest.builder()
                .query(command.getQuery())
                .topK(resolveTopK(command))
                .build();
    }

    String resolveFilterExpression(RagRetrievalCommandVO command) {
        return null;
    }

    private void validate(RagRetrievalCommandVO command) {
        if (command == null) {
            throw new IllegalArgumentException("RagRetrievalCommand is required.");
        }
        if (isBlank(command.getQuery())) {
            throw new IllegalArgumentException("RAG query is required.");
        }
        if (vectorMemoryRepository == null && vectorStore == null) {
            throw new IllegalStateException("PgVectorStore is not configured for RAG retrieval.");
        }
    }

    private int resolveTopK(RagRetrievalCommandVO command) {
        Integer topK = command.getTopK();
        return topK == null || topK <= 0 ? 5 : topK;
    }

    private List<RagHitVO> mapDocuments(List<Document> documents) {
        return java.util.stream.IntStream.range(0, documents.size())
                .mapToObj(index -> mapDocument(documents.get(index), index + 1))
                .toList();
    }

    private List<RagHitVO> mapVectorHits(List<VectorRecallHitVO> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        return java.util.stream.IntStream.range(0, hits.size())
                .mapToObj(index -> mapVectorHit(hits.get(index), index + 1))
                .toList();
    }

    private RagHitVO mapVectorHit(VectorRecallHitVO hit, int rankNo) {
        Map<String, Object> metadata = hit.getMetadata();
        String title = firstNonBlank(metadataValue(metadata, "sourceName"),
                metadataValue(metadata, "relativePath"),
                metadataValue(metadata, "repositoryName"),
                hit.getSummary(),
                "RAG Document " + rankNo);
        return RagHitVO.builder()
                .sourceType(hit.getSourceType() == null ? "RAG" : hit.getSourceType().name())
                .sourceId(hit.getSourceId())
                .title(title)
                .chunkText(firstNonBlank(hit.getSnippet(), hit.getSummary()))
                .score(hit.getScore())
                .rankNo(rankNo)
                .metadata(metadata)
                .build();
    }

    private RagHitVO mapDocument(Document document, int rankNo) {
        Map<String, Object> metadata = document.getMetadata();
        String title = firstNonBlank(metadataValue(metadata, "title"),
                metadataValue(metadata, "file_name"),
                metadataValue(metadata, "filename"),
                metadataValue(metadata, "source"),
                metadataValue(metadata, "knowledge"),
                "RAG Document " + rankNo);
        String sourceId = firstNonBlank(metadataValue(metadata, "source"),
                metadataValue(metadata, "id"),
                metadataValue(metadata, "knowledge"),
                title);
        return RagHitVO.builder()
                .sourceType("PG_VECTOR")
                .sourceId(sourceId)
                .title(title)
                .chunkText(document.getText())
                .score(score(metadata))
                .rankNo(rankNo)
                .metadata(metadata)
                .build();
    }

    private Double score(Map<String, Object> metadata) {
        String[] keys = {"score", "similarity", "distance"};
        for (String key : keys) {
            Object value = metadata == null ? null : metadata.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value != null) {
                try {
                    return Double.parseDouble(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                    // Keep scanning known score keys.
                }
            }
        }
        return null;
    }

    private String metadataValue(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
