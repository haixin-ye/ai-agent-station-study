package yhx.com.domain.agent.service.rag.runtime;

import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.RagHitEntity;
import yhx.com.domain.agent.model.entity.persistence.RagQueryEntity;
import yhx.com.domain.agent.model.valobj.rag.RagVerifierInputBuildCommandVO;
import yhx.com.domain.agent.model.valobj.rag.RagVerifierInputVO;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class RagVerifierInputBuilder {

    private static final int DEFAULT_MAX_SNIPPET_CHARS = 1200;

    private final IPayloadRepository payloadRepository;
    private final RagEvidenceSnippetPolicy snippetPolicy;

    public RagVerifierInputBuilder(IPayloadRepository payloadRepository) {
        this(payloadRepository, new RagEvidenceSnippetPolicy());
    }

    public RagVerifierInputBuilder(IPayloadRepository payloadRepository, RagEvidenceSnippetPolicy snippetPolicy) {
        this.payloadRepository = payloadRepository;
        this.snippetPolicy = snippetPolicy == null ? new RagEvidenceSnippetPolicy() : snippetPolicy;
    }

    public RagVerifierInputVO build(RagVerifierInputBuildCommandVO command) {
        if (command == null || !Boolean.TRUE.equals(command.getRagWasUsed())) {
            throw new IllegalArgumentException("RagVerifierInput requires ragWasUsed=true.");
        }
        List<RagQueryEntity> queries = command.getRagQueries() == null ? List.of() : command.getRagQueries();
        List<RagHitEntity> hits = command.getRagHits() == null ? List.of() : command.getRagHits();
        List<AgentEvidenceEntity> evidenceList = command.getRagEvidence() == null ? List.of() : command.getRagEvidence();
        Map<String, List<RagHitEntity>> hitsByQuery = hits.stream().collect(Collectors.groupingBy(RagHitEntity::getRagQueryId));
        Map<String, RagHitEntity> hitsById = hits.stream().filter(hit -> hit.getRagHitId() != null)
                .collect(Collectors.toMap(RagHitEntity::getRagHitId, hit -> hit, (left, right) -> left));

        return RagVerifierInputVO.builder()
                .runMeta(RagVerifierInputVO.RunMeta.builder()
                        .runId(command.getRunId())
                        .sessionId(command.getSessionId())
                        .loopIndex(command.getLoopIndex())
                        .build())
                .userRequest(RagVerifierInputVO.UserRequest.builder()
                        .messageId(command.getUserMessageId())
                        .content(command.getUserInput())
                        .requiresKnowledgeBaseGrounding(Boolean.TRUE.equals(command.getRequiresKnowledgeBaseGrounding()))
                        .build())
                .finalAnswerCandidate(finalCandidate(command.getFinalAnswerCandidate(), command))
                .ragContext(RagVerifierInputVO.RagContext.builder()
                        .ragWasUsed(true)
                        .queryCount(queries.size())
                        .queries(queries.stream().map(query -> queryItem(query, hitsByQuery)).toList())
                        .noHit(hits.isEmpty() || evidenceList.isEmpty())
                        .build())
                .evidence(evidenceList.stream()
                        .filter(evidence -> "RAG".equals(evidence.getEvidenceType()))
                        .map(evidence -> evidenceItem(evidence, hitsById, maxSnippetChars(command)))
                        .filter(Objects::nonNull)
                        .toList())
                .verificationMode(RagVerifierInputVO.VerificationMode.builder()
                        .mode("GROUNDING_HONESTY")
                        .strictCitationCheck(true)
                        .allowGeneralKnowledgeWhenNotClaimingRag(true)
                        .build())
                .outputContractVersion("verification-result-v1")
                .build();
    }

    private RagVerifierInputVO.FinalCandidate finalCandidate(FinalAnswerCandidateVO finalAnswerCandidate,
                                                             RagVerifierInputBuildCommandVO command) {
        String content = finalAnswerCandidate == null ? null : finalAnswerCandidate.getContent();
        String targetId = finalAnswerCandidate == null ? null : finalAnswerCandidate.getContentRef();
        return RagVerifierInputVO.FinalCandidate.builder()
                .targetId(targetId)
                .content(content)
                .claimsKnowledgeBaseGrounding(Boolean.TRUE.equals(command.getClaimsKnowledgeBaseGrounding()))
                .citations(command.getCitations() == null ? List.of() : command.getCitations().stream()
                        .map(id -> RagVerifierInputVO.Citation.builder().evidenceId(id).usage("USED_AS_SUPPORT").build())
                        .toList())
                .build();
    }

    private RagVerifierInputVO.QueryItem queryItem(RagQueryEntity query, Map<String, List<RagHitEntity>> hitsByQuery) {
        List<RagHitEntity> hits = hitsByQuery.getOrDefault(query.getRagQueryId(), List.of());
        return RagVerifierInputVO.QueryItem.builder()
                .ragQueryId(query.getRagQueryId())
                .query(query.getQueryText())
                .status(query.getStatus())
                .hitCount(hits.size())
                .build();
    }

    private RagVerifierInputVO.EvidenceItem evidenceItem(AgentEvidenceEntity evidence,
                                                         Map<String, RagHitEntity> hitsById,
                                                         int maxSnippetChars) {
        RagHitEntity hit = hitsById.get(evidence.getSourceRef());
        String chunk = hit == null || hit.getChunkRef() == null || payloadRepository == null
                ? null
                : payloadRepository.findContent(hit.getChunkRef()).orElse(null);
        String snippet = snippetPolicy.boundedSnippet(chunk, maxSnippetChars);
        return RagVerifierInputVO.EvidenceItem.builder()
                .evidenceId(evidence.getEvidenceId())
                .ragQueryId(hit == null ? null : hit.getRagQueryId())
                .sourceTitle(hit == null ? evidence.getSourceRef() : hit.getSourceTitle())
                .chunkSummary(evidence.getSummary())
                .chunkSnippet(snippet)
                .citationLabel(hit == null ? evidence.getEvidenceId() : hit.getSourceTitle())
                .relevance(relevance(evidence.getConfidence()))
                .build();
    }

    private String relevance(BigDecimal confidence) {
        if (confidence == null) {
            return "UNKNOWN";
        }
        if (confidence.doubleValue() >= 0.75) {
            return "HIGH";
        }
        if (confidence.doubleValue() >= 0.4) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private int maxSnippetChars(RagVerifierInputBuildCommandVO command) {
        Integer value = command.getMaxEvidenceSnippetChars();
        return value == null || value <= 0 ? DEFAULT_MAX_SNIPPET_CHARS : value;
    }
}
