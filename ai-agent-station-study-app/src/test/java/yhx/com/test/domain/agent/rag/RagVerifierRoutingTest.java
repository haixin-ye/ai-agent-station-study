package yhx.com.test.domain.agent.rag;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.RagHitEntity;
import yhx.com.domain.agent.model.entity.persistence.RagQueryEntity;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerificationRouteCommandVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerificationRouteResultVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerifierInputVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;
import yhx.com.domain.agent.service.rag.runtime.RagVerificationRouter;
import yhx.com.domain.agent.service.rag.runtime.RagVerifierInputBuilder;
import yhx.com.domain.agent.service.node.ragverifier.RagVerifierNodeService;

import java.util.List;

public class RagVerifierRoutingTest {

    @Test
    public void rag_verifier_runs_when_rag_was_used_true() {
        Fixture fixture = fixture(VerificationResultVO.builder().status("PASSED").detail("ok").build());

        RagVerificationRouteResultVO result = fixture.router.verifyIfRequired(route(true, "normal answer"));

        Assert.assertTrue(result.isVerificationRequired());
        Assert.assertEquals(1, fixture.verifier.calls);
    }

    @Test
    public void rag_verifier_skips_when_rag_was_used_false_even_if_answer_mentions_knowledge_base() {
        Fixture fixture = fixture(VerificationResultVO.builder().status("PASSED").detail("ok").build());

        RagVerificationRouteResultVO result = fixture.router.verifyIfRequired(route(false, "According to the knowledge base..."));

        Assert.assertFalse(result.isVerificationRequired());
        Assert.assertEquals(0, fixture.verifier.calls);
    }

    @Test
    public void rag_verifier_skips_when_rag_was_used_false_even_if_answer_has_citations() {
        Fixture fixture = fixture(VerificationResultVO.builder().status("PASSED").detail("ok").build());

        RagVerificationRouteResultVO result = fixture.router.verifyIfRequired(route(false, "Answer [evidence-1]"));

        Assert.assertFalse(result.isVerificationRequired());
        Assert.assertEquals(0, fixture.verifier.calls);
    }

    @Test
    public void rag_verifier_failure_returns_verification_result() {
        Fixture fixture = fixture(VerificationResultVO.builder().status("FAILED").failureCode("RAG_UNGROUNDED").detail("unsupported").build());

        RagVerificationRouteResultVO result = fixture.router.verifyIfRequired(route(true, "unsupported answer"));

        Assert.assertEquals("FAILED", result.getVerificationResult().getStatus());
        Assert.assertEquals("RAG_UNGROUNDED", result.getVerificationResult().getFailureCode());
        Assert.assertEquals("RAG_VERIFICATION_FAILED", result.getFailureCode().code());
    }

    private Fixture fixture(VerificationResultVO verifierResult) {
        RagTestSupport.FullRepository repository = new RagTestSupport.FullRepository();
        repository.savePayload(AgentPayloadEntity.builder().payloadId("payload-1").content("retrieved evidence").build());
        repository.queries.add(RagQueryEntity.builder().ragQueryId("rag-query-1").runId("run-001").queryText("RAG").status("SUCCESS").build());
        repository.hits.add(RagHitEntity.builder().ragHitId("rag-hit-1").ragQueryId("rag-query-1").runId("run-001").chunkRef("payload-1").sourceTitle("doc").build());
        repository.evidence.add(AgentEvidenceEntity.builder().evidenceId("evidence-1").runId("run-001").evidenceType("RAG").sourceRef("rag-hit-1").summary("retrieved evidence").build());
        FakeVerifier verifier = new FakeVerifier(verifierResult);
        return new Fixture(new RagVerificationRouter(repository, repository, new RagVerifierInputBuilder(repository), verifier), verifier);
    }

    private RagVerificationRouteCommandVO route(boolean ragWasUsed, String answer) {
        return RagVerificationRouteCommandVO.builder()
                .runId("run-001")
                .sessionId("sess-001")
                .agentId("agent-001")
                .loopIndex(1)
                .userInput("Explain RAG.")
                .ragWasUsed(ragWasUsed)
                .citations(List.of("evidence-1"))
                .finalAnswerCandidate(FinalAnswerCandidateVO.builder().content(answer).build())
                .build();
    }

    private record Fixture(RagVerificationRouter router, FakeVerifier verifier) {
    }

    private static class FakeVerifier extends RagVerifierNodeService {
        private final VerificationResultVO result;
        private int calls;

        private FakeVerifier(VerificationResultVO result) {
            super(null);
            this.result = result;
        }

        @Override
        public VerificationResultVO verify(String agentId, RagVerifierInputVO input) {
            calls++;
            return result;
        }
    }
}
