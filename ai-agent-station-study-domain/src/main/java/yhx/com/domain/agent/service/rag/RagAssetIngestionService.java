package yhx.com.domain.agent.service.rag;

import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRagAssetRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.rag.RagChunkEntity;
import yhx.com.domain.agent.model.entity.rag.RagCodeFileEntity;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;
import yhx.com.domain.agent.model.entity.rag.RagFileIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagFilePayloadEntity;
import yhx.com.domain.agent.model.entity.rag.RagGitFileEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.rag.RagAssetAnalysisResultVO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public class RagAssetIngestionService {

    private final IRagAssetRepository ragAssetRepository;
    private final IPayloadRepository payloadRepository;
    private final RagParagraphChunker chunker;
    private final RagVectorIndexingService vectorIndexingService;
    private final RagAssetAnalyzer analyzer;

    public RagAssetIngestionService(IRagAssetRepository ragAssetRepository,
                                    IPayloadRepository payloadRepository,
                                    RagParagraphChunker chunker,
                                    RagVectorIndexingService vectorIndexingService) {
        this(ragAssetRepository, payloadRepository, chunker, vectorIndexingService, new DeterministicRagAssetAnalyzer());
    }

    public RagAssetIngestionService(IRagAssetRepository ragAssetRepository,
                                    IPayloadRepository payloadRepository,
                                    RagParagraphChunker chunker,
                                    RagVectorIndexingService vectorIndexingService,
                                    RagAssetAnalyzer analyzer) {
        this.ragAssetRepository = ragAssetRepository;
        this.payloadRepository = payloadRepository;
        this.chunker = chunker == null ? new RagParagraphChunker() : chunker;
        this.vectorIndexingService = vectorIndexingService;
        this.analyzer = analyzer == null ? new DeterministicRagAssetAnalyzer() : analyzer;
    }

    public List<RagDocumentEntity> ingestFiles(RagFileIngestCommandEntity command) {
        validate(command);
        return command.getFiles().stream()
                .filter(file -> file != null && file.getContent() != null && file.getContent().length > 0)
                .map(file -> ingestFile(command.getUserId(), command.getSessionId(), "FILE", null, null, null, null,
                        firstNonBlank(file.getFileName(), "uploaded-file"),
                        new String(file.getContent(), StandardCharsets.UTF_8),
                        null,
                        command.getIndexingMetadata()))
                .toList();
    }

    public List<RagDocumentEntity> ingestGitFiles(String userId,
                                                  String sessionId,
                                                  String repositoryUrl,
                                                  String branchName,
                                                  List<RagGitFileEntity> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .filter(file -> file != null && file.getContent() != null && !file.getContent().isBlank())
                .map(file -> ingestFile(userId,
                        sessionId,
                        "GIT_FILE",
                        repositoryUrl,
                        file.getRepositoryName(),
                        branchName,
                        file.getRelativePath(),
                        firstNonBlank(file.getRepositoryName(), "repository") + "/" + file.getRelativePath(),
                        file.getContent(),
                        file,
                        null))
                .toList();
    }

    private RagDocumentEntity ingestFile(String userId,
                                         String sessionId,
                                         String sourceType,
                                         String repositoryUrl,
                                         String repositoryName,
                                         String branchName,
                                         String relativePath,
                                         String sourceName,
                                         String text,
                                         RagGitFileEntity gitFile,
                                         java.util.Map<String, Object> indexingMetadata) {
        validateText(sourceName, text);
        LocalDateTime now = LocalDateTime.now();
        String documentId = "rag-doc-" + UUID.randomUUID();
        String contentHash = sha256(text);
        List<String> chunks = chunker.split(text);
        RagAssetAnalysisResultVO documentAnalysis = analyzeDocument(sourceName, sourceType, text);
        String contentRef = savePayload(PayloadTypeEnumVO.RAG_CHUNK, text);
        RagDocumentEntity document = RagDocumentEntity.builder()
                .documentId(documentId)
                .userId(userId)
                .sessionId(sessionId)
                .sourceType(sourceType)
                .sourceName(sourceName)
                .repositoryUrl(repositoryUrl)
                .repositoryName(repositoryName)
                .branchName(branchName)
                .relativePath(relativePath)
                .title(firstNonBlank(documentAnalysis.getTitle(), sourceName))
                .summary(firstNonBlank(documentAnalysis.getSummary(), summarize(text)))
                .contentRef(contentRef)
                .contentSha256(contentHash)
                .status("INGESTING")
                .chunkCount(chunks.size())
                .createdAt(now)
                .updatedAt(now)
                .build();
        ragAssetRepository.saveDocument(document);
        if ("GIT_FILE".equals(sourceType)) {
            vectorIndexingService.indexDocument(document,
                    firstNonBlank(documentAnalysis.getRetrievalText(), documentIndexText(document, text)), indexingMetadata);
        }
        if (gitFile != null) {
            ragAssetRepository.saveCodeFile(RagCodeFileEntity.builder()
                    .codeFileId("rag-code-file-" + UUID.randomUUID())
                    .documentId(documentId)
                    .repositoryUrl(repositoryUrl)
                    .branchName(branchName)
                    .relativePath(relativePath)
                    .language(firstNonBlank(gitFile.getLanguage(), documentAnalysis.getLanguage()))
                    .fileSummary(document.getSummary())
                    .status("ACTIVE")
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }
        for (int index = 0; index < chunks.size(); index++) {
            String chunkText = chunks.get(index);
            RagAssetAnalysisResultVO chunkAnalysis = analyzeChunk(sourceName, sourceType, chunkText);
            String chunkRetrievalText = firstNonBlank(chunkAnalysis.getRetrievalText(), retrievalText(document, chunkText));
            RagChunkEntity chunk = RagChunkEntity.builder()
                    .chunkId(documentId + "-chunk-" + (index + 1))
                    .documentId(documentId)
                    .userId(userId)
                    .sessionId(sessionId)
                    .chunkNo(index + 1)
                    .chunkType("GIT_FILE".equals(sourceType) ? "CODE_CHUNK" : "FILE_CHUNK")
                    .summary(firstNonBlank(chunkAnalysis.getSummary(), summarize(chunkText)))
                    .contentRef(savePayload(PayloadTypeEnumVO.RAG_CHUNK, chunkText))
                    .retrievalTextRef(savePayload(PayloadTypeEnumVO.TEXT, chunkRetrievalText))
                    .contentSha256(sha256(chunkText))
                    .status("ACTIVE")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            ragAssetRepository.saveChunk(chunk);
            vectorIndexingService.indexChunk(chunk, chunkRetrievalText, indexingMetadata);
        }
        document.setStatus("READY");
        document.setUpdatedAt(LocalDateTime.now());
        ragAssetRepository.updateDocument(document);
        return document;
    }

    private RagAssetAnalysisResultVO analyzeDocument(String sourceName, String sourceType, String text) {
        try {
            RagAssetAnalysisResultVO result = analyzer.analyzeDocument(sourceName, sourceType, text);
            return result == null ? fallbackAnalysis(sourceName, sourceType, text) : result;
        } catch (Exception ignored) {
            return fallbackAnalysis(sourceName, sourceType, text);
        }
    }

    private RagAssetAnalysisResultVO analyzeChunk(String sourceName, String sourceType, String text) {
        try {
            RagAssetAnalysisResultVO result = analyzer.analyzeChunk(sourceName, sourceType, text);
            return result == null ? fallbackAnalysis(sourceName, sourceType, text) : result;
        } catch (Exception ignored) {
            return fallbackAnalysis(sourceName, sourceType, text);
        }
    }

    private RagAssetAnalysisResultVO fallbackAnalysis(String sourceName, String sourceType, String text) {
        return new DeterministicRagAssetAnalyzer().analyzeDocument(sourceName, sourceType, text);
    }

    private String savePayload(PayloadTypeEnumVO type, String content) {
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(type)
                .content(content)
                .build());
    }

    private String documentIndexText(RagDocumentEntity document, String text) {
        return "title: " + document.getTitle()
                + "\nsource: " + document.getSourceName()
                + "\nsummary: " + document.getSummary()
                + "\ncontent:\n" + preview(text, 4000);
    }

    private String retrievalText(RagDocumentEntity document, String chunkText) {
        return "title: " + document.getTitle()
                + "\nsource: " + document.getSourceName()
                + "\nsummary: " + document.getSummary()
                + "\nchunk:\n" + chunkText;
    }

    private void validate(RagFileIngestCommandEntity command) {
        if (command == null) {
            throw new IllegalArgumentException("RAG file ingest command is required.");
        }
        if (command.getFiles() == null || command.getFiles().isEmpty()) {
            throw new IllegalArgumentException("files must not be empty.");
        }
        if (ragAssetRepository == null || payloadRepository == null || vectorIndexingService == null) {
            throw new IllegalStateException("RAG asset ingestion dependencies are not configured.");
        }
    }

    private void validateText(String sourceName, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("RAG content must not be blank: " + sourceName);
        }
    }

    private String summarize(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return preview(normalized, 300);
    }

    private String preview(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate RAG content hash.", e);
        }
    }
}
