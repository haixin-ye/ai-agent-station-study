package yhx.com.domain.agent.service.rag.runtime;

import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IRagExecutionRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.RagHitEntity;
import yhx.com.domain.agent.model.entity.persistence.RagQueryEntity;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerificationRouteCommandVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerificationRouteResultVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerifierInputBuildCommandVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerifierInputVO;
import yhx.com.domain.agent.service.node.ragverifier.RagVerifierNodeService;

import java.util.List;

public class RagVerificationRouter {

    private final IRagExecutionRepository ragExecutionRepository;
    private final IEvidenceRepository evidenceRepository;
    private final RagVerifierInputBuilder inputBuilder;
    private final RagVerifierNodeService verifierNodeService;

    public RagVerificationRouter(IRagExecutionRepository ragExecutionRepository,
                                 IEvidenceRepository evidenceRepository,
                                 RagVerifierInputBuilder inputBuilder,
                                 RagVerifierNodeService verifierNodeService) {
        this.ragExecutionRepository = ragExecutionRepository;
        this.evidenceRepository = evidenceRepository;
        this.inputBuilder = inputBuilder;
        this.verifierNodeService = verifierNodeService;
    }

    public RagVerificationRouteResultVO verifyIfRequired(RagVerificationRouteCommandVO command) {
        if (command == null || !Boolean.TRUE.equals(command.getRagWasUsed())) {
            return RagVerificationRouteResultVO.builder()
                    .verificationRequired(false)
                    .verificationResult(VerificationResultVO.builder()
                            .status("SKIPPED")
                            .detail("RAG was not used by Runtime.")
                            .build())
                    .message("RAG verification skipped.")
                    .build();
        }
        List<RagQueryEntity> queries = ragExecutionRepository.listRagQueries(command.getRunId());
        List<RagHitEntity> hits = ragExecutionRepository.listRagHits(command.getRunId());
        List<AgentEvidenceEntity> evidence = evidenceRepository.listRunEvidence(command.getRunId()).stream()
                .filter(item -> "RAG".equals(item.getEvidenceType()))
                .toList();
        RagVerifierInputVO input = inputBuilder.build(RagVerifierInputBuildCommandVO.builder()
                .runId(command.getRunId())
                .sessionId(command.getSessionId())
                .loopIndex(command.getLoopIndex())
                .userMessageId(command.getUserMessageId())
                .userInput(command.getUserInput())
                .finalAnswerCandidate(command.getFinalAnswerCandidate())
                .ragWasUsed(true)
                .requiresKnowledgeBaseGrounding(command.getRequiresKnowledgeBaseGrounding())
                .claimsKnowledgeBaseGrounding(command.getClaimsKnowledgeBaseGrounding())
                .citations(command.getCitations())
                .ragQueries(queries)
                .ragHits(hits)
                .ragEvidence(evidence)
                .maxEvidenceSnippetChars(command.getMaxEvidenceSnippetChars())
                .build());
        VerificationResultVO result = verifierNodeService.verify(command.getAgentId(), input);
        RuntimeFailureCodeEnumVO failureCode = "FAILED".equals(result.getStatus())
                ? RuntimeFailureCodeEnumVO.RAG_VERIFICATION_FAILED
                : null;
        return RagVerificationRouteResultVO.builder()
                .verificationRequired(true)
                .verificationResult(result)
                .failureCode(failureCode)
                .message(result.getDetail())
                .build();
    }
}
