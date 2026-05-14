package yhx.com.infrastructure.adapter.port;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import yhx.com.domain.agent.model.valobj.rag.RagHitVO;
import yhx.com.domain.agent.model.valobj.rag.RagRetrievalCommandVO;
import yhx.com.domain.agent.service.rag.runtime.RagRetrieverPort;

import java.util.List;
import java.util.Map;

@Component
public class SpringAiRagRetrieverAdapter implements RagRetrieverPort {

    @Resource(name = "pgVectorStore")
    private VectorStore vectorStore;

    public SpringAiRagRetrieverAdapter() {
    }

    public SpringAiRagRetrieverAdapter(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<RagHitVO> retrieve(RagRetrievalCommandVO command) {
        validate(command);
        List<Document> documents = vectorStore.similaritySearch(buildSearchRequest(command));
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return mapDocuments(documents);
    }

    SearchRequest buildSearchRequest(RagRetrievalCommandVO command) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(command.getQuery())
                .topK(resolveTopK(command));
        String filterExpression = resolveFilterExpression(command);
        if (!isBlank(filterExpression)) {
            builder.filterExpression(filterExpression);
        }
        return builder.build();
    }

    String resolveFilterExpression(RagRetrievalCommandVO command) {
        Object explicitFilter = command.getRuntimeFilters() == null ? null : command.getRuntimeFilters().get("filterExpression");
        if (explicitFilter != null && !String.valueOf(explicitFilter).isBlank()) {
            return String.valueOf(explicitFilter);
        }
        if (!isBlank(command.getKnowledgeName())) {
            return "knowledge == '" + escapeFilterLiteral(command.getKnowledgeName()) + "'";
        }
        return null;
    }

    private void validate(RagRetrievalCommandVO command) {
        if (command == null) {
            throw new IllegalArgumentException("RagRetrievalCommand is required.");
        }
        if (isBlank(command.getQuery())) {
            throw new IllegalArgumentException("RAG query is required.");
        }
        if (vectorStore == null) {
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

    private String escapeFilterLiteral(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
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
