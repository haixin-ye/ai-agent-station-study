package yhx.com.test.domain.agent.rag;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.RagRuntimeStatusEnumVO;
import yhx.com.domain.agent.model.valobj.rag.RagHitVO;
import yhx.com.domain.agent.model.valobj.rag.RagRetrievalCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeResultVO;
import yhx.com.domain.agent.service.rag.runtime.RagRuntime;
import yhx.com.domain.agent.service.rag.runtime.RagRetrieverPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RagRuntimeTest {

    @Test
    public void retrieve_sets_rag_was_used_before_retriever_call() {
        RagTestSupport.FullRepository repository = repository();
        FlagCheckingRetriever retriever = new FlagCheckingRetriever(repository);
        RagRuntime runtime = new RagRuntime(repository, repository, repository, repository, retriever);

        runtime.retrieve(command());

        Assert.assertTrue(retriever.flagWasSetBeforeCall);
        Assert.assertEquals(1, repository.markRagWasUsedCalls);
    }

    @Test
    public void retrieve_success_persists_query_hits_payloads_and_evidence() {
        RagTestSupport.FullRepository repository = repository();
        RagRuntime runtime = new RagRuntime(repository, repository, repository, repository, successRetriever());

        RagRuntimeResultVO result = runtime.retrieve(command());

        Assert.assertEquals(RagRuntimeStatusEnumVO.SUCCESS, result.getStatus());
        Assert.assertEquals("SUCCESS", repository.queries.get(0).getStatus());
        Assert.assertEquals(1, repository.hits.size());
        Assert.assertEquals(2, repository.payloads.size());
        Assert.assertEquals(1, repository.evidence.size());
        Assert.assertEquals(List.of("evidence-1"), result.getEvidenceIds());
    }

    @Test
    public void retrieve_no_hit_still_keeps_rag_was_used_true() {
        RagTestSupport.FullRepository repository = repository();
        RagRuntime runtime = new RagRuntime(repository, repository, repository, repository, command -> List.of());

        RagRuntimeResultVO result = runtime.retrieve(command());

        Assert.assertEquals(RagRuntimeStatusEnumVO.NO_HIT, result.getStatus());
        Assert.assertTrue(repository.runs.get("run-001").getRagWasUsed());
        Assert.assertEquals("NO_HIT", repository.queries.get(0).getStatus());
    }

    @Test
    public void retrieve_failure_records_failed_query_status() {
        RagTestSupport.FullRepository repository = repository();
        RagRuntime runtime = new RagRuntime(repository, repository, repository, repository, command -> {
            throw new IllegalStateException("vector store down");
        });

        RagRuntimeResultVO result = runtime.retrieve(command());

        Assert.assertEquals(RagRuntimeStatusEnumVO.FAILED, result.getStatus());
        Assert.assertEquals("FAILED", repository.queries.get(0).getStatus());
        Assert.assertEquals("RAG_RETRIEVAL_FAILED", repository.queries.get(0).getFailureCode());
    }

    private RagTestSupport.FullRepository repository() {
        RagTestSupport.FullRepository repository = new RagTestSupport.FullRepository();
        repository.createRun(AgentRunEntity.builder().runId("run-001").ragWasUsed(false).build());
        return repository;
    }

    private RagRuntimeCommandVO command() {
        return RagRuntimeCommandVO.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .loopIndex(1)
                .query("RAG workflow")
                .knowledgeName("kb")
                .options(Map.of("topK", 3))
                .build();
    }

    private RagRetrieverPort successRetriever() {
        return command -> List.of(RagHitVO.builder()
                .sourceId("doc-1")
                .title("RAG workflow notes")
                .chunkText("The workflow includes query rewriting, retrieval, reranking, and answer generation.")
                .score(0.91)
                .build());
    }

    private static class FlagCheckingRetriever implements RagRetrieverPort {
        private final RagTestSupport.FullRepository repository;
        private boolean flagWasSetBeforeCall;

        private FlagCheckingRetriever(RagTestSupport.FullRepository repository) {
            this.repository = repository;
        }

        @Override
        public List<RagHitVO> retrieve(RagRetrievalCommandVO command) {
            flagWasSetBeforeCall = Boolean.TRUE.equals(repository.runs.get(command.getRunId()).getRagWasUsed());
            return new ArrayList<>(List.of(RagHitVO.builder().title("doc").chunkText("content").score(0.8).build()));
        }
    }
}
