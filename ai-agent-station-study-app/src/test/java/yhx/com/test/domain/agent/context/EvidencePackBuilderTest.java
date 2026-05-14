package yhx.com.test.domain.agent.context;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.valobj.context.EvidenceCandidateVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.service.evidence.EvidencePackBuilder;
import yhx.com.domain.agent.service.evidence.ToolReceiptSummarizer;

import java.util.List;

public class EvidencePackBuilderTest {

    @Test
    public void rag_evidence_keeps_summary_and_bounded_snippet() {
        List<MaterializedEvidenceVO> pack = new EvidencePackBuilder(10).buildFromCandidates(List.of(
                EvidenceCandidateVO.builder().evidenceId("e1").evidenceType("RAG").summary("123456789012345").build()));

        Assert.assertEquals("1234567890", pack.get(0).getSummary());
    }

    @Test
    public void tool_evidence_keeps_status_url_and_id_only() {
        MaterializedEvidenceVO evidence = new ToolReceiptSummarizer().summarizeToolEvidence(
                AgentEvidenceEntity.builder().evidenceId("e1").evidenceType("TOOL").summary("tool ok").build(),
                AgentPayloadEntity.builder().content("{\"status\":\"OK\",\"url\":\"https://x\",\"id\":\"42\",\"token\":\"secret\"}").build());

        Assert.assertTrue(evidence.getBoundedSnippet().contains("status"));
        Assert.assertTrue(evidence.getBoundedSnippet().contains("url"));
        Assert.assertFalse(evidence.getBoundedSnippet().contains("secret"));
    }
}
