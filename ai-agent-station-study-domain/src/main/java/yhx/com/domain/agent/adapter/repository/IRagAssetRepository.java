package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.rag.RagChunkEntity;
import yhx.com.domain.agent.model.entity.rag.RagCodeFileEntity;
import yhx.com.domain.agent.model.entity.rag.RagCodeSymbolEntity;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;

import java.util.List;
import java.util.Optional;

public interface IRagAssetRepository {

    void saveDocument(RagDocumentEntity document);

    void updateDocument(RagDocumentEntity document);

    Optional<RagDocumentEntity> findDocument(String documentId);

    List<RagDocumentEntity> findDocumentsByIds(List<String> documentIds);

    void saveChunk(RagChunkEntity chunk);

    List<RagChunkEntity> findChunksByDocumentId(String documentId);

    Optional<RagChunkEntity> findChunk(String chunkId);

    List<RagChunkEntity> findChunksByIds(List<String> chunkIds);

    void saveCodeFile(RagCodeFileEntity codeFile);

    List<RagCodeFileEntity> findCodeFilesByDocumentId(String documentId);

    void saveCodeSymbol(RagCodeSymbolEntity symbol);

    List<RagCodeSymbolEntity> findCodeSymbolsByDocumentId(String documentId);
}
