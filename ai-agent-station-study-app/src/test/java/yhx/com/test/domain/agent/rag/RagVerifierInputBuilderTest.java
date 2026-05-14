package yhx.com.test.domain.agent.rag;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.RagHitEntity;
import yhx.com.domain.agent.model.entity.persistence.RagQueryEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerifierInputBuildCommandVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerifierInputVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.service.rag.runtime.RagVerifierInputBuilder;

import java.math.BigDecimal;
import java.util.List;

public class RagVerifierInputBuilderTest {

    @Test
    public void builder_includes_only_bounded_rag_evidence() {
        RagTestSupport.FullRepository repository = new RagTestSupport.FullRepository();
        repository.savePayload(AgentPayloadEntity.builder()
                .payloadId("payload-1")
                .payloadType(PayloadTypeEnumVO.RAG_CHUNK)
                .content("1234567890")
                .build());

        RagVerifierInputVO input = new RagVerifierInputBuilder(repository).build(command(repository, 5));

        Assert.assertEquals("12345", input.getEvidence().get(0).getChunkSnippet());
        Assert.assertEquals(1, input.getEvidence().size());
    }

    @Test
    public void builder_excludes_raw_prompt_trace_tool_receipt_and_unrelated_artifact() {
        RagTestSupport.FullRepository repository = new RagTestSupport.FullRepository();
        repository.savePayload(AgentPayloadEntity.builder().payloadId("payload-1").content("safe chunk").build());

        RagVerifierInputVO input = new RagVerifierInputBuilder(repository).build(command(repository, 100));

        String rendered = com.alibaba.fastjson.JSON.toJSONString(input);
        Assert.assertFalse(rendered.contains("rawPrompt"));
        Assert.assertFalse(rendered.contains("toolReceipt"));
        Assert.assertFalse(rendered.contains("developerTrace"));
        Assert.assertFalse(rendered.contains("unrelatedArtifact"));
    }

    @Test
    public void builder_copies_final_candidate_citations() {
        RagTestSupport.FullRepository repository = new RagTestSupport.FullRepository();
        repository.savePayload(AgentPayloadEntity.builder().payloadId("payload-1").content("safe chunk").build());

        RagVerifierInputVO input = new RagVerifierInputBuilder(repository).build(command(repository, 100));

        Assert.assertEquals("evidence-1", input.getFinalAnswerCandidate().getCitations().get(0).getEvidenceId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void builder_requires_rag_was_used_route_before_invocation() {
        new RagVerifierInputBuilder(new RagTestSupport.FullRepository()).build(RagVerifierInputBuildCommandVO.builder()
                .ragWasUsed(false)
                .build());
    }

    private RagVerifierInputBuildCommandVO command(RagTestSupport.FullRepository repository, int maxSnippetChars) {
        return RagVerifierInputBuildCommandVO.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .loopIndex(1)
                .userMessageId("msg-001")
                .userInput("Explain RAG from knowledge base.")
                .ragWasUsed(true)
                .requiresKnowledgeBaseGrounding(true)
                .claimsKnowledgeBaseGrounding(true)
                .citations(List.of("evidence-1"))
                .finalAnswerCandidate(FinalAnswerCandidateVO.builder().content("Answer with citation.").build())
                .ragQueries(List.of(RagQueryEntity.builder().ragQueryId("rag-query-1").runId("run-001").queryText("RAG").status("SUCCESS").build()))
                .ragHits(List.of(RagHitEntity.builder().ragHitId("rag-hit-1").ragQueryId("rag-query-1").runId("run-001").chunkRef("payload-1").sourceTitle("doc").build()))
                .ragEvidence(List.of(
                        AgentEvidenceEntity.builder().evidenceId("evidence-1").runId("run-001").evidenceType("RAG").sourceRef("rag-hit-1").summary("safe").confidence(BigDecimal.valueOf(0.9)).build(),
                        AgentEvidenceEntity.builder().evidenceId("evidence-2").runId("run-001").evidenceType("TOOL").sourceRef("tool-1").summary("tool").build()))
                .maxEvidenceSnippetChars(maxSnippetChars)
                .build();
    }
}
