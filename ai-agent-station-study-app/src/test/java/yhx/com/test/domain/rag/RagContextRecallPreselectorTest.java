package yhx.com.test.domain.rag;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IRagAssetRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.rag.RagChunkEntity;
import yhx.com.domain.agent.model.entity.rag.RagCodeFileEntity;
import yhx.com.domain.agent.model.entity.rag.RagCodeSymbolEntity;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.RagCandidateVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.service.rag.RagContextRecallPreselector;

import java.util.List;
import java.util.Optional;

public class RagContextRecallPreselectorTest {

    @Test
    public void recall_usesLexicalFallbackForDistinctiveRagTokenWhenVectorMisses() {
        FakeVectorMemoryRepository vectorMemoryRepository = new FakeVectorMemoryRepository();
        FakeRagAssetRepository ragAssetRepository = new FakeRagAssetRepository();
        RagContextRecallPreselector preselector = new RagContextRecallPreselector(
                vectorMemoryRepository,
                ragAssetRepository,
                5,
                0.4);

        List<RagCandidateVO> candidates = preselector.recall(ContextPreparationCommand.builder()
                .userId("user-1")
                .sessionId("session-1")
                .userInput("SB-Q1 是什么意思？")
                .build());

        Assert.assertTrue(vectorMemoryRepository.vectorSearchCalled);
        Assert.assertTrue(vectorMemoryRepository.lexicalSearchCalled);
        Assert.assertEquals(1, candidates.size());
        RagCandidateVO candidate = candidates.get(0);
        Assert.assertEquals("chunk-sb-q1", candidate.getCandidateId());
        Assert.assertEquals("RAG_FILE_CHUNK", candidate.getSourceType());
        Assert.assertEquals("CHUNK_TEXT", candidate.getInjectMode());
        Assert.assertEquals(Double.valueOf(1.0D), candidate.getSourceScore());
    }

    private static class FakeVectorMemoryRepository implements IVectorMemoryRepository {

        private boolean vectorSearchCalled;
        private boolean lexicalSearchCalled;

        @Override
        public String upsert(VectorIndexRecordVO record) {
            return "vector-1";
        }

        @Override
        public List<VectorRecallHitVO> search(VectorRecallQueryVO query) {
            vectorSearchCalled = true;
            return List.of();
        }

        @Override
        public List<VectorRecallHitVO> lexicalSearch(VectorRecallQueryVO query) {
            lexicalSearchCalled = true;
            return List.of(VectorRecallHitVO.builder()
                    .collectionType(VectorCollectionTypeEnumVO.RAG_FILE_CHUNK)
                    .sourceType(VectorSourceTypeEnumVO.RAG_FILE_CHUNK)
                    .sourceId("chunk-sb-q1")
                    .score(1.0D)
                    .summary("SB-Q1 means Silver Button Questions 1.")
                    .snippet("白信封的新编号写成 SB-Q1，意思是 Silver Button Questions 1。")
                    .build());
        }

        @Override
        public void disable(VectorCollectionTypeEnumVO collectionType, String sourceId) {
        }
    }

    private static class FakeRagAssetRepository implements IRagAssetRepository {

        @Override
        public void saveDocument(RagDocumentEntity document) {
        }

        @Override
        public void updateDocument(RagDocumentEntity document) {
        }

        @Override
        public Optional<RagDocumentEntity> findDocument(String documentId) {
            return Optional.empty();
        }

        @Override
        public List<RagDocumentEntity> findDocumentsByIds(List<String> documentIds) {
            return List.of();
        }

        @Override
        public void saveChunk(RagChunkEntity chunk) {
        }

        @Override
        public List<RagChunkEntity> findChunksByDocumentId(String documentId) {
            return List.of();
        }

        @Override
        public Optional<RagChunkEntity> findChunk(String chunkId) {
            if (!"chunk-sb-q1".equals(chunkId)) {
                return Optional.empty();
            }
            return Optional.of(RagChunkEntity.builder()
                    .chunkId("chunk-sb-q1")
                    .documentId("doc-sb-q1")
                    .chunkNo(1)
                    .chunkType("FILE_CHUNK")
                    .summary("SB-Q1 means Silver Button Questions 1.")
                    .contentRef("payload-sb-q1")
                    .status("ACTIVE")
                    .build());
        }

        @Override
        public List<RagChunkEntity> findChunksByIds(List<String> chunkIds) {
            return List.of();
        }

        @Override
        public void saveCodeFile(RagCodeFileEntity codeFile) {
        }

        @Override
        public List<RagCodeFileEntity> findCodeFilesByDocumentId(String documentId) {
            return List.of();
        }

        @Override
        public void saveCodeSymbol(RagCodeSymbolEntity symbol) {
        }

        @Override
        public List<RagCodeSymbolEntity> findCodeSymbolsByDocumentId(String documentId) {
            return List.of();
        }
    }
}
