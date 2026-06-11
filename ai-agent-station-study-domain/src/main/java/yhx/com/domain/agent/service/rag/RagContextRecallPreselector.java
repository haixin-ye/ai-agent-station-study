package yhx.com.domain.agent.service.rag;

import yhx.com.domain.agent.adapter.repository.IRagAssetRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.rag.RagChunkEntity;
import yhx.com.domain.agent.model.entity.rag.RagCodeFileEntity;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.RagCodeCandidateMetaVO;
import yhx.com.domain.agent.model.valobj.context.RagCandidateVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallFilterVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RagContextRecallPreselector {

    private final IVectorMemoryRepository vectorMemoryRepository;
    private final IRagAssetRepository ragAssetRepository;
    private final Integer topK;
    private final Double minScore;

    public RagContextRecallPreselector(IVectorMemoryRepository vectorMemoryRepository,
                                       IRagAssetRepository ragAssetRepository,
                                       Integer topK,
                                       Double minScore) {
        this.vectorMemoryRepository = vectorMemoryRepository;
        this.ragAssetRepository = ragAssetRepository;
        this.topK = topK == null ? 8 : topK;
        this.minScore = minScore == null ? 0.2 : minScore;
    }

    public RagContextRecallPreselector(IVectorMemoryRepository vectorMemoryRepository,
                                       IRagAssetRepository ragAssetRepository,
                                       Integer topK,
                                       Double minScore,
                                       Duration ignoredTimeout) {
        this(vectorMemoryRepository, ragAssetRepository, topK, minScore);
    }

    public List<RagCandidateVO> recall(ContextPreparationCommand command) {
        if (vectorMemoryRepository == null || ragAssetRepository == null || command == null || isBlank(command.getUserInput())) {
            return List.of();
        }
        VectorRecallQueryVO query = VectorRecallQueryVO.builder()
                .queryText(command.getUserInput())
                .topK(topK)
                .minScore(minScore)
                .filter(VectorRecallFilterVO.builder()
                        .userId(command.getUserId())
                        .sessionId(command.getSessionId())
                        .collectionTypes(List.of(
                                VectorCollectionTypeEnumVO.RAG_FILE_CHUNK,
                                VectorCollectionTypeEnumVO.RAG_CODE_FILE_SUMMARY,
                                VectorCollectionTypeEnumVO.RAG_CODE_CHUNK))
                        .build())
                .build();
        List<VectorRecallHitVO> hits = new ArrayList<>();
        List<VectorRecallHitVO> vectorHits = vectorMemoryRepository.search(query);
        if (vectorHits != null) {
            hits.addAll(vectorHits);
        }
        List<VectorRecallHitVO> lexicalHits = vectorMemoryRepository.lexicalSearch(query);
        if (lexicalHits != null) {
            hits.addAll(lexicalHits);
        }
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        Map<String, RagCandidateVO> merged = new LinkedHashMap<>();
        for (VectorRecallHitVO hit : hits) {
            RagCandidateVO candidate = toCandidate(hit);
            if (candidate == null || candidate.getCandidateId() == null) {
                continue;
            }
            merged.merge(candidate.getCandidateId(), candidate, this::higherScore);
        }
        return new ArrayList<>(merged.values());
    }

    private RagCandidateVO toCandidate(VectorRecallHitVO hit) {
        if (hit == null || hit.getCollectionType() == null || isBlank(hit.getSourceId())) {
            return null;
        }
        if (hit.getCollectionType() == VectorCollectionTypeEnumVO.RAG_CODE_FILE_SUMMARY
                || hit.getCollectionType() == VectorCollectionTypeEnumVO.RAG_DOCUMENT) {
            return ragAssetRepository.findDocument(hit.getSourceId())
                    .filter(this::active)
                    .map(document -> documentCandidate(document, hit))
                    .orElse(null);
        }
        if (hit.getCollectionType() == VectorCollectionTypeEnumVO.RAG_FILE_CHUNK
                || hit.getCollectionType() == VectorCollectionTypeEnumVO.RAG_CODE_CHUNK
                || hit.getCollectionType() == VectorCollectionTypeEnumVO.RAG_CHUNK) {
            return ragAssetRepository.findChunk(hit.getSourceId())
                    .filter(this::active)
                    .map(chunk -> chunkCandidate(chunk, hit))
                    .orElse(null);
        }
        return null;
    }

    private RagCandidateVO documentCandidate(RagDocumentEntity document, VectorRecallHitVO hit) {
        return RagCandidateVO.builder()
                .candidateId(document.getDocumentId())
                .sourceType(documentSourceType(document, hit))
                .documentId(document.getDocumentId())
                .sourceName(document.getSourceName())
                .title(document.getTitle())
                .summary(firstNonBlank(document.getSummary(), hit.getSummary()))
                .snippet(hit.getSnippet())
                .contentRef(document.getContentRef())
                .chunkCount(document.getChunkCount())
                .codeMeta(codeMeta(document))
                .injectMode("RAG_CODE_FILE_SUMMARY".equals(documentSourceType(document, hit)) ? "SUMMARY_ONLY" : "SUMMARY_ONLY")
                .sourceScore(hit.getScore())
                .sourceChannel("vector-rag")
                .reasons(List.of("semantic match from RAG document vector"))
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    private RagCandidateVO chunkCandidate(RagChunkEntity chunk, VectorRecallHitVO hit) {
        return RagCandidateVO.builder()
                .candidateId(chunk.getChunkId())
                .sourceType(chunkSourceType(chunk, hit))
                .documentId(chunk.getDocumentId())
                .chunkId(chunk.getChunkId())
                .summary(firstNonBlank(chunk.getSummary(), hit.getSummary()))
                .snippet(hit.getSnippet())
                .contentRef(chunk.getContentRef())
                .retrievalTextRef(chunk.getRetrievalTextRef())
                .chunkNo(chunk.getChunkNo())
                .codeMeta(codeMeta(chunk.getDocumentId()))
                .injectMode("CHUNK_TEXT")
                .sourceScore(hit.getScore())
                .sourceChannel("vector-rag")
                .reasons(List.of("semantic match from RAG chunk vector"))
                .createdAt(chunk.getCreatedAt())
                .updatedAt(chunk.getUpdatedAt())
                .build();
    }

    private RagCandidateVO higherScore(RagCandidateVO existing, RagCandidateVO incoming) {
        double left = existing.getSourceScore() == null ? 0.0 : existing.getSourceScore();
        double right = incoming.getSourceScore() == null ? 0.0 : incoming.getSourceScore();
        return right > left ? incoming : existing;
    }

    private String documentSourceType(RagDocumentEntity document, VectorRecallHitVO hit) {
        if (hit != null && hit.getCollectionType() == VectorCollectionTypeEnumVO.RAG_CODE_FILE_SUMMARY) {
            return "RAG_CODE_FILE_SUMMARY";
        }
        if (document != null && "GIT_FILE".equals(document.getSourceType())) {
            return "RAG_CODE_FILE_SUMMARY";
        }
        return "RAG_DOCUMENT";
    }

    private String chunkSourceType(RagChunkEntity chunk, VectorRecallHitVO hit) {
        if (hit != null && hit.getCollectionType() == VectorCollectionTypeEnumVO.RAG_CODE_CHUNK) {
            return "RAG_CODE_CHUNK";
        }
        if (hit != null && hit.getCollectionType() == VectorCollectionTypeEnumVO.RAG_FILE_CHUNK) {
            return "RAG_FILE_CHUNK";
        }
        if (chunk != null && "CODE_CHUNK".equals(chunk.getChunkType())) {
            return "RAG_CODE_CHUNK";
        }
        if (chunk != null && "FILE_CHUNK".equals(chunk.getChunkType())) {
            return "RAG_FILE_CHUNK";
        }
        return "RAG_CHUNK";
    }

    private RagCodeCandidateMetaVO codeMeta(RagDocumentEntity document) {
        if (document == null) {
            return null;
        }
        return codeMeta(document.getDocumentId());
    }

    private RagCodeCandidateMetaVO codeMeta(String documentId) {
        if (isBlank(documentId)) {
            return null;
        }
        List<RagCodeFileEntity> codeFiles = ragAssetRepository.findCodeFilesByDocumentId(documentId);
        if (codeFiles == null || codeFiles.isEmpty()) {
            return null;
        }
        RagCodeFileEntity codeFile = codeFiles.get(0);
        return RagCodeCandidateMetaVO.builder()
                .repositoryUrl(codeFile.getRepositoryUrl())
                .branchName(codeFile.getBranchName())
                .relativePath(codeFile.getRelativePath())
                .language(codeFile.getLanguage())
                .build();
    }

    private boolean active(RagDocumentEntity document) {
        return document != null && !"DELETED".equalsIgnoreCase(document.getStatus()) && !"FAILED".equalsIgnoreCase(document.getStatus());
    }

    private boolean active(RagChunkEntity chunk) {
        return chunk != null && !"DELETED".equalsIgnoreCase(chunk.getStatus()) && !"FAILED".equalsIgnoreCase(chunk.getStatus());
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
