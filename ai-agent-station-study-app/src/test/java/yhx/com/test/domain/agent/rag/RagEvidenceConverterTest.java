package yhx.com.test.domain.agent.rag;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.valobj.rag.RagHitVO;
import yhx.com.domain.agent.service.rag.runtime.RagEvidenceConverter;
import yhx.com.domain.agent.service.rag.runtime.RagEvidenceSnippetPolicy;

import java.math.BigDecimal;
import java.util.List;

public class RagEvidenceConverterTest {

    @Test
    public void convert_hit_to_rag_evidence() {
        List<AgentEvidenceEntity> evidence = new RagEvidenceConverter().convert("run-001", "sess-001", "rag-query-1",
                List.of(hit("rag-hit-1", "RAG workflow text", 0.9)));

        Assert.assertEquals(1, evidence.size());
        Assert.assertEquals("RAG", evidence.get(0).getEvidenceType());
        Assert.assertEquals("rag-hit-1", evidence.get(0).getSourceRef());
    }

    @Test
    public void empty_chunk_is_dropped() {
        List<AgentEvidenceEntity> evidence = new RagEvidenceConverter().convert("run-001", "sess-001", "rag-query-1",
                List.of(hit("rag-hit-1", " ", 0.9)));

        Assert.assertTrue(evidence.isEmpty());
    }

    @Test
    public void long_chunk_is_bounded_by_snippet_policy() {
        RagEvidenceConverter converter = new RagEvidenceConverter(new RagEvidenceSnippetPolicy(), 8);

        List<AgentEvidenceEntity> evidence = converter.convert("run-001", "sess-001", "rag-query-1",
                List.of(hit("rag-hit-1", "1234567890", 0.9)));

        Assert.assertTrue(evidence.get(0).getSummary().contains("12345678"));
        Assert.assertFalse(evidence.get(0).getSummary().contains("90"));
    }

    @Test
    public void confidence_uses_retrieval_score() {
        AgentEvidenceEntity evidence = new RagEvidenceConverter().convert("run-001", "sess-001", "rag-query-1",
                List.of(hit("rag-hit-1", "RAG workflow text", 0.75))).get(0);

        Assert.assertEquals(BigDecimal.valueOf(0.75), evidence.getConfidence());
    }

    private RagHitVO hit(String hitId, String chunk, double score) {
        return RagHitVO.builder()
                .ragHitId(hitId)
                .title("doc")
                .chunkText(chunk)
                .score(score)
                .build();
    }
}
