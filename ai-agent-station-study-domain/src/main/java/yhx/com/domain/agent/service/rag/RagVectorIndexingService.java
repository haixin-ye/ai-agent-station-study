package yhx.com.domain.agent.service.rag;

import yhx.com.domain.agent.adapter.repository.IVectorIndexRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentVectorIndexEntity;
import yhx.com.domain.agent.model.entity.rag.RagChunkEntity;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class RagVectorIndexingService {

    private final IVectorMemoryRepository vectorMemoryRepository;
    private final IVectorIndexRepository vectorIndexRepository;

    public RagVectorIndexingService(IVectorMemoryRepository vectorMemoryRepository,
                                    IVectorIndexRepository vectorIndexRepository) {
        this.vectorMemoryRepository = vectorMemoryRepository;
        this.vectorIndexRepository = vectorIndexRepository;
    }

    public String indexDocument(RagDocumentEntity document, String indexText) {
        validateDocument(document, indexText);
        return upsert(documentCollection(document),
                documentSourceType(document),
                document.getDocumentId(),
                document.getUserId(),
                document.getSessionId(),
                indexText,
                document.getSummary(),
                document.getContentSha256(),
                documentMetadata(document));
    }

    public String indexChunk(RagChunkEntity chunk, String indexText) {
        validateChunk(chunk, indexText);
        return upsert(chunkCollection(chunk),
                chunkSourceType(chunk),
                chunk.getChunkId(),
                chunk.getUserId(),
                chunk.getSessionId(),
                indexText,
                chunk.getSummary(),
                chunk.getContentSha256(),
                chunkMetadata(chunk));
    }

    private String upsert(VectorCollectionTypeEnumVO collectionType,
                          VectorSourceTypeEnumVO sourceType,
                          String sourceId,
                          String userId,
                          String sessionId,
                          String text,
                          String summary,
                          String contentHash,
                          Map<String, Object> metadata) {
        ensureRepositories();
        LocalDateTime now = LocalDateTime.now();
        String vectorId = vectorMemoryRepository.upsert(VectorIndexRecordVO.builder()
                .collectionType(collectionType)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .userId(userId)
                .sessionId(sessionId)
                .text(text)
                .summary(summary)
                .contentHash(contentHash)
                .metadata(metadata)
                .occurredAt(now)
                .build());
        vectorIndexRepository.saveOrUpdate(AgentVectorIndexEntity.builder()
                .collectionType(collectionType.name())
                .sourceType(sourceType.name())
                .sourceId(sourceId)
                .vectorId(vectorId)
                .userId(userId)
                .sessionId(sessionId)
                .contentHash(contentHash)
                .status("ACTIVE")
                .indexedAt(now)
                .build());
        return vectorId;
    }

    private Map<String, Object> documentMetadata(RagDocumentEntity document) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentId", document.getDocumentId());
        putIfPresent(metadata, "sourceType", document.getSourceType());
        putIfPresent(metadata, "sourceName", document.getSourceName());
        putIfPresent(metadata, "repositoryUrl", document.getRepositoryUrl());
        putIfPresent(metadata, "repositoryName", document.getRepositoryName());
        putIfPresent(metadata, "branchName", document.getBranchName());
        putIfPresent(metadata, "relativePath", document.getRelativePath());
        putIfPresent(metadata, "title", document.getTitle());
        return metadata;
    }

    private VectorCollectionTypeEnumVO documentCollection(RagDocumentEntity document) {
        if (document != null && "GIT_FILE".equals(document.getSourceType())) {
            return VectorCollectionTypeEnumVO.RAG_CODE_FILE_SUMMARY;
        }
        return VectorCollectionTypeEnumVO.RAG_DOCUMENT;
    }

    private VectorSourceTypeEnumVO documentSourceType(RagDocumentEntity document) {
        if (document != null && "GIT_FILE".equals(document.getSourceType())) {
            return VectorSourceTypeEnumVO.RAG_CODE_FILE_SUMMARY;
        }
        return VectorSourceTypeEnumVO.RAG_DOCUMENT;
    }

    private VectorCollectionTypeEnumVO chunkCollection(RagChunkEntity chunk) {
        if (chunk != null && "CODE_CHUNK".equals(chunk.getChunkType())) {
            return VectorCollectionTypeEnumVO.RAG_CODE_CHUNK;
        }
        if (chunk != null && "FILE_CHUNK".equals(chunk.getChunkType())) {
            return VectorCollectionTypeEnumVO.RAG_FILE_CHUNK;
        }
        return VectorCollectionTypeEnumVO.RAG_CHUNK;
    }

    private VectorSourceTypeEnumVO chunkSourceType(RagChunkEntity chunk) {
        if (chunk != null && "CODE_CHUNK".equals(chunk.getChunkType())) {
            return VectorSourceTypeEnumVO.RAG_CODE_CHUNK;
        }
        if (chunk != null && "FILE_CHUNK".equals(chunk.getChunkType())) {
            return VectorSourceTypeEnumVO.RAG_FILE_CHUNK;
        }
        return VectorSourceTypeEnumVO.RAG_CHUNK;
    }

    private Map<String, Object> chunkMetadata(RagChunkEntity chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentId", chunk.getDocumentId());
        metadata.put("chunkId", chunk.getChunkId());
        metadata.put("chunkNo", chunk.getChunkNo());
        putIfPresent(metadata, "chunkType", chunk.getChunkType());
        putIfPresent(metadata, "headingPath", chunk.getHeadingPath());
        return metadata;
    }

    private void validateDocument(RagDocumentEntity document, String indexText) {
        if (document == null) {
            throw new IllegalArgumentException("RAG document is required.");
        }
        if (isBlank(document.getDocumentId())) {
            throw new IllegalArgumentException("documentId is required.");
        }
        if (isBlank(indexText)) {
            throw new IllegalArgumentException("document indexText is required.");
        }
    }

    private void validateChunk(RagChunkEntity chunk, String indexText) {
        if (chunk == null) {
            throw new IllegalArgumentException("RAG chunk is required.");
        }
        if (isBlank(chunk.getChunkId())) {
            throw new IllegalArgumentException("chunkId is required.");
        }
        if (isBlank(chunk.getDocumentId())) {
            throw new IllegalArgumentException("chunk documentId is required.");
        }
        if (isBlank(indexText)) {
            throw new IllegalArgumentException("chunk indexText is required.");
        }
    }

    private void ensureRepositories() {
        if (vectorMemoryRepository == null || vectorIndexRepository == null) {
            throw new IllegalStateException("RAG vector repositories are not configured.");
        }
    }

    private void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (!isBlank(value)) {
            metadata.put(key, value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
