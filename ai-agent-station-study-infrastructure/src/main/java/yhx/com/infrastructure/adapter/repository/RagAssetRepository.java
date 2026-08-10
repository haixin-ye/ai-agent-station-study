package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IRagAssetRepository;
import yhx.com.domain.agent.model.entity.rag.RagChunkEntity;
import yhx.com.domain.agent.model.entity.rag.RagCodeFileEntity;
import yhx.com.domain.agent.model.entity.rag.RagCodeSymbolEntity;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;
import yhx.com.infrastructure.dao.IAgentRagChunkDao;
import yhx.com.infrastructure.dao.IAgentRagCodeFileDao;
import yhx.com.infrastructure.dao.IAgentRagDocumentDao;
import yhx.com.infrastructure.dao.po.AgentRagChunkPO;
import yhx.com.infrastructure.dao.po.AgentRagCodeFilePO;
import yhx.com.infrastructure.dao.po.AgentRagDocumentPO;

import java.util.List;
import java.util.Optional;

@Repository
public class RagAssetRepository implements IRagAssetRepository {

    @Resource
    private IAgentRagDocumentDao agentRagDocumentDao;

    @Resource
    private IAgentRagChunkDao agentRagChunkDao;

    @Resource
    private IAgentRagCodeFileDao agentRagCodeFileDao;

    @Override
    public void saveDocument(RagDocumentEntity document) {
        agentRagDocumentDao.insert(toPO(document));
    }

    @Override
    public void updateDocument(RagDocumentEntity document) {
        agentRagDocumentDao.updateByDocumentId(toPO(document));
    }

    @Override
    public Optional<RagDocumentEntity> findDocument(String documentId) {
        return Optional.ofNullable(agentRagDocumentDao.queryByDocumentId(documentId)).map(this::toEntity);
    }

    @Override
    public Optional<RagDocumentEntity> findLatestDocument(String userId, String sessionId, String sourceName) {
        return Optional.ofNullable(agentRagDocumentDao.queryLatestByScopeAndSource(userId, sessionId, sourceName))
                .map(this::toEntity);
    }

    @Override
    public List<RagDocumentEntity> findDocumentsByIds(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        return agentRagDocumentDao.queryByDocumentIds(documentIds).stream().map(this::toEntity).toList();
    }

    @Override
    public void saveChunk(RagChunkEntity chunk) {
        agentRagChunkDao.insert(toPO(chunk));
    }

    @Override
    public void updateChunkStatus(String chunkId, String status) {
        agentRagChunkDao.updateStatus(chunkId, status);
    }

    @Override
    public List<RagChunkEntity> findChunksByDocumentId(String documentId) {
        return agentRagChunkDao.queryByDocumentId(documentId).stream().map(this::toEntity).toList();
    }

    @Override
    public Optional<RagChunkEntity> findChunk(String chunkId) {
        return Optional.ofNullable(agentRagChunkDao.queryByChunkId(chunkId)).map(this::toEntity);
    }

    @Override
    public List<RagChunkEntity> findChunksByIds(List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        return agentRagChunkDao.queryByChunkIds(chunkIds).stream().map(this::toEntity).toList();
    }

    @Override
    public void saveCodeFile(RagCodeFileEntity codeFile) {
        agentRagCodeFileDao.insert(toPO(codeFile));
    }

    @Override
    public List<RagCodeFileEntity> findCodeFilesByDocumentId(String documentId) {
        return agentRagCodeFileDao.queryByDocumentId(documentId).stream().map(this::toEntity).toList();
    }

    @Override
    public void saveCodeSymbol(RagCodeSymbolEntity symbol) {
        throw new UnsupportedOperationException("RAG code symbol persistence is not implemented in this MVP.");
    }

    @Override
    public List<RagCodeSymbolEntity> findCodeSymbolsByDocumentId(String documentId) {
        return List.of();
    }

    private AgentRagDocumentPO toPO(RagDocumentEntity entity) {
        return AgentRagDocumentPO.builder()
                .documentId(entity.getDocumentId())
                .userId(entity.getUserId())
                .sessionId(entity.getSessionId())
                .sourceType(entity.getSourceType())
                .sourceName(entity.getSourceName())
                .repositoryUrl(entity.getRepositoryUrl())
                .repositoryName(entity.getRepositoryName())
                .branchName(entity.getBranchName())
                .relativePath(entity.getRelativePath())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .contentRef(entity.getContentRef())
                .summaryRef(entity.getSummaryRef())
                .contentSha256(entity.getContentSha256())
                .status(entity.getStatus())
                .chunkCount(entity.getChunkCount())
                .failureMessage(entity.getFailureMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private RagDocumentEntity toEntity(AgentRagDocumentPO po) {
        return RagDocumentEntity.builder()
                .documentId(po.getDocumentId())
                .userId(po.getUserId())
                .sessionId(po.getSessionId())
                .sourceType(po.getSourceType())
                .sourceName(po.getSourceName())
                .repositoryUrl(po.getRepositoryUrl())
                .repositoryName(po.getRepositoryName())
                .branchName(po.getBranchName())
                .relativePath(po.getRelativePath())
                .title(po.getTitle())
                .summary(po.getSummary())
                .contentRef(po.getContentRef())
                .summaryRef(po.getSummaryRef())
                .contentSha256(po.getContentSha256())
                .status(po.getStatus())
                .chunkCount(po.getChunkCount())
                .failureMessage(po.getFailureMessage())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private AgentRagChunkPO toPO(RagChunkEntity entity) {
        return AgentRagChunkPO.builder()
                .chunkId(entity.getChunkId())
                .documentId(entity.getDocumentId())
                .userId(entity.getUserId())
                .sessionId(entity.getSessionId())
                .chunkNo(entity.getChunkNo())
                .chunkType(entity.getChunkType())
                .headingPath(entity.getHeadingPath())
                .summary(entity.getSummary())
                .contentRef(entity.getContentRef())
                .retrievalTextRef(entity.getRetrievalTextRef())
                .contentSha256(entity.getContentSha256())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private RagChunkEntity toEntity(AgentRagChunkPO po) {
        return RagChunkEntity.builder()
                .chunkId(po.getChunkId())
                .documentId(po.getDocumentId())
                .userId(po.getUserId())
                .sessionId(po.getSessionId())
                .chunkNo(po.getChunkNo())
                .chunkType(po.getChunkType())
                .headingPath(po.getHeadingPath())
                .summary(po.getSummary())
                .contentRef(po.getContentRef())
                .retrievalTextRef(po.getRetrievalTextRef())
                .contentSha256(po.getContentSha256())
                .status(po.getStatus())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private AgentRagCodeFilePO toPO(RagCodeFileEntity entity) {
        return AgentRagCodeFilePO.builder()
                .codeFileId(entity.getCodeFileId())
                .documentId(entity.getDocumentId())
                .repositoryUrl(entity.getRepositoryUrl())
                .branchName(entity.getBranchName())
                .relativePath(entity.getRelativePath())
                .language(entity.getLanguage())
                .fileSummary(entity.getFileSummary())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private RagCodeFileEntity toEntity(AgentRagCodeFilePO po) {
        return RagCodeFileEntity.builder()
                .codeFileId(po.getCodeFileId())
                .documentId(po.getDocumentId())
                .repositoryUrl(po.getRepositoryUrl())
                .branchName(po.getBranchName())
                .relativePath(po.getRelativePath())
                .language(po.getLanguage())
                .fileSummary(po.getFileSummary())
                .status(po.getStatus())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
