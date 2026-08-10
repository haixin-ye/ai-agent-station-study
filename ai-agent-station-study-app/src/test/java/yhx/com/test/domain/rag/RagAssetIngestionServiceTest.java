package yhx.com.test.domain.rag;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRagAssetRepository;
import yhx.com.domain.agent.adapter.repository.IVectorIndexRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentVectorIndexEntity;
import yhx.com.domain.agent.model.entity.rag.RagChunkEntity;
import yhx.com.domain.agent.model.entity.rag.RagCodeFileEntity;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;
import yhx.com.domain.agent.model.entity.rag.RagFileIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagFilePayloadEntity;
import yhx.com.domain.agent.model.entity.rag.RagGitFileEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.model.valobj.rag.RagAssetAnalysisResultVO;
import yhx.com.domain.agent.service.rag.RagAssetAnalyzer;
import yhx.com.domain.agent.service.rag.RagAssetIngestionService;
import yhx.com.domain.agent.service.rag.RagParagraphChunker;
import yhx.com.domain.agent.service.rag.RagVectorIndexingService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RagAssetIngestionServiceTest {

    @Test
    public void ingestFiles_usesRawChunkTextWhenDeterministicAnalyzerIsActive() {
        FakeRagAssetRepository assetRepository = new FakeRagAssetRepository();
        FakePayloadRepository payloadRepository = new FakePayloadRepository();
        FakeVectorMemoryRepository vectorMemoryRepository = new FakeVectorMemoryRepository();
        RagAssetIngestionService service = new RagAssetIngestionService(
                assetRepository,
                payloadRepository,
                new RagParagraphChunker(512, 100),
                new RagVectorIndexingService(vectorMemoryRepository, new FakeVectorIndexRepository()));
        String chunkText = "The blue ticket number is NZ-417 and it is stored inside the red book.";

        service.ingestFiles(RagFileIngestCommandEntity.builder()
                .userId("user-1")
                .sessionId("session-1")
                .files(List.of(RagFilePayloadEntity.builder()
                        .fileName("ticket.txt")
                        .content(chunkText.getBytes(StandardCharsets.UTF_8))
                        .build()))
                .build());

        VectorIndexRecordVO chunkRecord = vectorMemoryRepository.records.stream()
                .filter(record -> record.getCollectionType() == VectorCollectionTypeEnumVO.RAG_FILE_CHUNK)
                .findFirst()
                .orElseThrow();
        Assert.assertEquals(chunkText, chunkRecord.getText());
        Assert.assertFalse(chunkRecord.getText().contains("sourceType:"));
        Assert.assertFalse(chunkRecord.getText().contains("summary:"));
        Assert.assertFalse(chunkRecord.getText().contains("content:"));
    }

    @Test
    public void ingestFiles_usesRawChunkTextWhenChunkAnalysisFallsBack() {
        FakeRagAssetRepository assetRepository = new FakeRagAssetRepository();
        FakePayloadRepository payloadRepository = new FakePayloadRepository();
        FakeVectorMemoryRepository vectorMemoryRepository = new FakeVectorMemoryRepository();
        RagAssetIngestionService service = new RagAssetIngestionService(
                assetRepository,
                payloadRepository,
                new RagParagraphChunker(512, 100),
                new RagVectorIndexingService(vectorMemoryRepository, new FakeVectorIndexRepository()),
                new FailingChunkAnalyzer());
        String chunkText = "Fallback retrieval must embed this chunk text directly.";

        service.ingestFiles(RagFileIngestCommandEntity.builder()
                .userId("user-1")
                .sessionId("session-1")
                .files(List.of(RagFilePayloadEntity.builder()
                        .fileName("fallback.txt")
                        .content(chunkText.getBytes(StandardCharsets.UTF_8))
                        .build()))
                .build());

        VectorIndexRecordVO chunkRecord = vectorMemoryRepository.records.stream()
                .filter(record -> record.getCollectionType() == VectorCollectionTypeEnumVO.RAG_FILE_CHUNK)
                .findFirst()
                .orElseThrow();
        Assert.assertEquals(chunkText, chunkRecord.getText());
    }

    @Test
    public void ingestFiles_persistsDocumentChunksPayloadsAndVectorsUsingAnalyzerSummaries() {
        FakeRagAssetRepository assetRepository = new FakeRagAssetRepository();
        FakePayloadRepository payloadRepository = new FakePayloadRepository();
        FakeVectorMemoryRepository vectorMemoryRepository = new FakeVectorMemoryRepository();
        RagAssetIngestionService service = new RagAssetIngestionService(
                assetRepository,
                payloadRepository,
                new RagParagraphChunker(80, 20),
                new RagVectorIndexingService(vectorMemoryRepository, new FakeVectorIndexRepository()),
                new StaticAnalyzer());

        service.ingestFiles(RagFileIngestCommandEntity.builder()
                .userId("user-1")
                .sessionId("session-1")
                .files(List.of(RagFilePayloadEntity.builder()
                        .fileName("rag-design.txt")
                        .content(("第一段介绍 RAG asset 的设计目标。\n\n" +
                                "第二段介绍 chunk 召回和 materializer 注入。\n\n" +
                                "第三段介绍 ContextPlanner 只选择必要候选。").getBytes(StandardCharsets.UTF_8))
                        .build()))
                .build());

        Assert.assertEquals(1, assetRepository.documents.size());
        RagDocumentEntity document = assetRepository.documents.get(0);
        Assert.assertEquals("user-1", document.getUserId());
        Assert.assertEquals("session-1", document.getSessionId());
        Assert.assertEquals("FILE", document.getSourceType());
        Assert.assertEquals("rag-design.txt", document.getSourceName());
        Assert.assertEquals("LLM document summary", document.getSummary());
        Assert.assertEquals("READY", document.getStatus());
        Assert.assertFalse(assetRepository.chunks.isEmpty());
        Assert.assertEquals("LLM chunk summary", assetRepository.chunks.get(0).getSummary());
        Assert.assertFalse(payloadRepository.payloads.isEmpty());
        Assert.assertTrue(vectorMemoryRepository.records.stream()
                .anyMatch(record -> record.getCollectionType() == VectorCollectionTypeEnumVO.RAG_FILE_CHUNK));
        Assert.assertFalse(vectorMemoryRepository.records.stream()
                .anyMatch(record -> record.getCollectionType() == VectorCollectionTypeEnumVO.RAG_DOCUMENT));
    }

    @Test
    public void ingestGitFiles_persistsRepositoryMetadataAndCodeFileSummary() {
        FakeRagAssetRepository assetRepository = new FakeRagAssetRepository();
        FakePayloadRepository payloadRepository = new FakePayloadRepository();
        FakeVectorMemoryRepository vectorMemoryRepository = new FakeVectorMemoryRepository();
        RagAssetIngestionService service = new RagAssetIngestionService(
                assetRepository,
                payloadRepository,
                new RagParagraphChunker(120, 20),
                new RagVectorIndexingService(vectorMemoryRepository, new FakeVectorIndexRepository()),
                new StaticAnalyzer());

        service.ingestGitFiles("user-1", "session-1", "https://github.com/example/demo.git", "main", List.of(
                RagGitFileEntity.builder()
                        .repositoryName("demo")
                        .relativePath("src/main/java/demo/App.java")
                        .language("java")
                        .content("public class App { void run() {} }")
                        .build()));

        Assert.assertEquals(1, assetRepository.documents.size());
        RagDocumentEntity document = assetRepository.documents.get(0);
        Assert.assertEquals("GIT_FILE", document.getSourceType());
        Assert.assertEquals("https://github.com/example/demo.git", document.getRepositoryUrl());
        Assert.assertEquals("demo", document.getRepositoryName());
        Assert.assertEquals("main", document.getBranchName());
        Assert.assertEquals("src/main/java/demo/App.java", document.getRelativePath());
        Assert.assertEquals(1, assetRepository.codeFiles.size());
        Assert.assertEquals("java", assetRepository.codeFiles.get(0).getLanguage());
        Assert.assertEquals("LLM document summary", assetRepository.codeFiles.get(0).getFileSummary());
    }

    private static class StaticAnalyzer implements RagAssetAnalyzer {

        @Override
        public RagAssetAnalysisResultVO analyzeDocument(String sourceName, String sourceType, String text) {
            return RagAssetAnalysisResultVO.builder()
                    .title(sourceName)
                    .summary("LLM document summary")
                    .retrievalText("LLM retrieval text for " + sourceName)
                    .build();
        }

        @Override
        public RagAssetAnalysisResultVO analyzeChunk(String sourceName, String sourceType, String text) {
            return RagAssetAnalysisResultVO.builder()
                    .summary("LLM chunk summary")
                    .retrievalText("LLM chunk retrieval text for " + sourceName)
                    .build();
        }
    }

    private static class FailingChunkAnalyzer implements RagAssetAnalyzer {

        @Override
        public RagAssetAnalysisResultVO analyzeDocument(String sourceName, String sourceType, String text) {
            return RagAssetAnalysisResultVO.builder()
                    .title(sourceName)
                    .summary("Document summary")
                    .retrievalText("Document retrieval text")
                    .build();
        }

        @Override
        public RagAssetAnalysisResultVO analyzeChunk(String sourceName, String sourceType, String text) {
            throw new IllegalStateException("simulated analyzer failure");
        }
    }

    private static class FakeRagAssetRepository implements IRagAssetRepository {
        private final List<RagDocumentEntity> documents = new ArrayList<>();
        private final List<RagChunkEntity> chunks = new ArrayList<>();
        private final List<RagCodeFileEntity> codeFiles = new ArrayList<>();

        @Override
        public void saveDocument(RagDocumentEntity document) {
            documents.add(document);
        }

        @Override
        public void updateDocument(RagDocumentEntity document) {
            documents.removeIf(item -> item.getDocumentId().equals(document.getDocumentId()));
            documents.add(document);
        }

        @Override
        public Optional<RagDocumentEntity> findDocument(String documentId) {
            return documents.stream().filter(item -> item.getDocumentId().equals(documentId)).findFirst();
        }

        @Override
        public List<RagDocumentEntity> findDocumentsByIds(List<String> documentIds) {
            return documents.stream().filter(item -> documentIds.contains(item.getDocumentId())).toList();
        }

        @Override
        public void saveChunk(RagChunkEntity chunk) {
            chunks.add(chunk);
        }

        @Override
        public List<RagChunkEntity> findChunksByDocumentId(String documentId) {
            return chunks.stream().filter(item -> item.getDocumentId().equals(documentId)).toList();
        }

        @Override
        public Optional<RagChunkEntity> findChunk(String chunkId) {
            return chunks.stream().filter(item -> item.getChunkId().equals(chunkId)).findFirst();
        }

        @Override
        public List<RagChunkEntity> findChunksByIds(List<String> chunkIds) {
            return chunks.stream().filter(item -> chunkIds.contains(item.getChunkId())).toList();
        }

        @Override
        public void saveCodeFile(RagCodeFileEntity codeFile) {
            codeFiles.add(codeFile);
        }

        @Override
        public List<RagCodeFileEntity> findCodeFilesByDocumentId(String documentId) {
            return codeFiles.stream().filter(item -> item.getDocumentId().equals(documentId)).toList();
        }

        @Override
        public void saveCodeSymbol(yhx.com.domain.agent.model.entity.rag.RagCodeSymbolEntity symbol) {
        }

        @Override
        public List<yhx.com.domain.agent.model.entity.rag.RagCodeSymbolEntity> findCodeSymbolsByDocumentId(String documentId) {
            return List.of();
        }
    }

    private static class FakePayloadRepository implements IPayloadRepository {
        private final List<AgentPayloadEntity> payloads = new ArrayList<>();

        @Override
        public String savePayload(AgentPayloadEntity payload) {
            payloads.add(payload);
            return "payload-" + payloads.size();
        }

        @Override
        public Optional<AgentPayloadEntity> findPayload(String payloadId) {
            return Optional.empty();
        }
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
        @Override
        public String saveOrUpdate(AgentVectorIndexEntity index) {
            return "idx";
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
