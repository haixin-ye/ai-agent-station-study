package yhx.com.test.domain.agent.context;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextMaterializationCommand;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedRagVO;
import yhx.com.domain.agent.model.valobj.context.RagCandidateVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;
import yhx.com.domain.agent.service.artifact.ArtifactPayloadLoader;
import yhx.com.domain.agent.service.context.ContextBudgetManager;
import yhx.com.domain.agent.service.context.ContextMaterializer;
import yhx.com.domain.agent.service.context.ContextSelectionValidator;
import yhx.com.domain.agent.service.context.ContextTokenEstimator;
import yhx.com.domain.agent.service.context.MainAgentStateViewBuilder;
import yhx.com.domain.agent.service.evidence.EvidencePackBuilder;

import java.util.List;
import java.util.Optional;

public class ContextMaterializerRagTest {

    @Test
    public void materialize_selectedFileChunkAsChunkText() {
        FakePayloadRepository payloadRepository = new FakePayloadRepository();
        ContextTokenEstimator tokenEstimator = new ContextTokenEstimator();
        ContextMaterializer materializer = new ContextMaterializer(
                new ContextSelectionValidator(),
                new ArtifactPayloadLoader(payloadRepository, tokenEstimator),
                new EvidencePackBuilder(200),
                new ContextBudgetManager(tokenEstimator),
                new MainAgentStateViewBuilder(),
                null,
                payloadRepository);

        MainAgentStateViewVO stateView = materializer.materialize(ContextMaterializationCommand.builder()
                .candidates(ContextCandidateBundleVO.builder()
                        .ragCandidates(List.of(RagCandidateVO.builder()
                                .candidateId("chunk-1")
                                .sourceType("RAG_FILE_CHUNK")
                                .documentId("doc-1")
                                .chunkId("chunk-1")
                                .title("RAG module design")
                                .summary("chunk summary")
                                .snippet("chunk snippet")
                                .contentRef("payload-1")
                                .injectMode("CHUNK_TEXT")
                                .build()))
                        .build())
                .forcedSelections(List.of(ContextSelectionVO.builder()
                        .sourceType("RAG_FILE_CHUNK")
                        .sourceId("chunk-1")
                        .contextLevel(ContextLevelEnumVO.CHUNKED_CONTEXT)
                        .priority(1)
                        .build()))
                .tokenBudget(TokenBudgetVO.builder().maxStateViewTokens(6000).maxArtifactInlineChars(4000).build())
                .build());

        Assert.assertEquals(1, stateView.getRagPack().size());
        MaterializedRagVO rag = stateView.getRagPack().get(0);
        Assert.assertEquals("chunk-1", rag.getChunkId());
        Assert.assertEquals("chunk content from payload", rag.getContent());
        Assert.assertEquals("CHUNK_TEXT", rag.getInjectMode());
        Assert.assertEquals(ContextLevelEnumVO.CHUNKED_CONTEXT, rag.getContextLevel());
    }

    @Test
    public void materialize_selectedCodeFileSummaryAsSummaryOnly() {
        FakePayloadRepository payloadRepository = new FakePayloadRepository();
        ContextTokenEstimator tokenEstimator = new ContextTokenEstimator();
        ContextMaterializer materializer = new ContextMaterializer(
                new ContextSelectionValidator(),
                new ArtifactPayloadLoader(payloadRepository, tokenEstimator),
                new EvidencePackBuilder(200),
                new ContextBudgetManager(tokenEstimator),
                new MainAgentStateViewBuilder(),
                null,
                payloadRepository);

        MainAgentStateViewVO stateView = materializer.materialize(ContextMaterializationCommand.builder()
                .candidates(ContextCandidateBundleVO.builder()
                        .ragCandidates(List.of(RagCandidateVO.builder()
                                .candidateId("doc-1")
                                .sourceType("RAG_CODE_FILE_SUMMARY")
                                .documentId("doc-1")
                                .title("App.java")
                                .summary("LLM file summary")
                                .contentRef("payload-1")
                                .injectMode("SUMMARY_ONLY")
                                .build()))
                        .build())
                .forcedSelections(List.of(ContextSelectionVO.builder()
                        .sourceType("RAG_CODE_FILE_SUMMARY")
                        .sourceId("doc-1")
                        .contextLevel(ContextLevelEnumVO.SUMMARY_ONLY)
                        .priority(1)
                        .build()))
                .tokenBudget(TokenBudgetVO.builder().maxStateViewTokens(6000).maxArtifactInlineChars(4000).build())
                .build());

        Assert.assertEquals(1, stateView.getRagPack().size());
        MaterializedRagVO rag = stateView.getRagPack().get(0);
        Assert.assertEquals("doc-1", rag.getDocumentId());
        Assert.assertEquals("LLM file summary", rag.getSummary());
        Assert.assertNull(rag.getContent());
        Assert.assertEquals("SUMMARY_ONLY", rag.getInjectMode());
    }

    private static class FakePayloadRepository implements IPayloadRepository {

        @Override
        public String savePayload(AgentPayloadEntity payload) {
            return payload.getPayloadId();
        }

        @Override
        public Optional<AgentPayloadEntity> findPayload(String payloadId) {
            return Optional.of(AgentPayloadEntity.builder()
                    .payloadId(payloadId)
                    .content("chunk content from payload")
                    .preview("chunk preview")
                    .build());
        }
    }
}
