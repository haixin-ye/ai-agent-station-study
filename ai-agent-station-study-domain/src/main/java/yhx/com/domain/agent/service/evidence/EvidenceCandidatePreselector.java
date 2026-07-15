package yhx.com.domain.agent.service.evidence;

import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.valobj.context.EvidenceCandidateVO;

import java.util.Comparator;
import java.util.List;

public class EvidenceCandidatePreselector {

    private final IPayloadRepository payloadRepository;

    public EvidenceCandidatePreselector() {
        this(null);
    }

    public EvidenceCandidatePreselector(IPayloadRepository payloadRepository) {
        this.payloadRepository = payloadRepository;
    }

    public List<EvidenceCandidateVO> select(String userInput, List<AgentEvidenceEntity> evidenceList, int limit) {
        if (evidenceList == null || evidenceList.isEmpty()) {
            return List.of();
        }
        String query = userInput == null ? "" : userInput.toLowerCase();
        return evidenceList.stream()
                .map(evidence -> toCandidate(query, evidence))
                .sorted(Comparator.comparing(EvidenceCandidateVO::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(limit, 0))
                .map(this::loadCandidateContent)
                .toList();
    }

    private EvidenceCandidateVO toCandidate(String query, AgentEvidenceEntity evidence) {
        String summary = evidence.getSummary() == null ? "" : evidence.getSummary().toLowerCase();
        double score = summary.isBlank() ? 0.0 : overlap(query, summary);
        return EvidenceCandidateVO.builder()
                .evidenceId(evidence.getEvidenceId())
                .evidenceType(evidence.getEvidenceType())
                .sourceRef(evidence.getSourceRef())
                .summary(evidence.getSummary())
                .contentRef(evidence.getContentRef())
                .contentFormat(evidence.getContentFormat())
                .verificationStatus(evidence.getVerificationStatus())
                .failureCode(evidence.getFailureCode())
                .createdAt(evidence.getCreatedAt())
                .score(score)
                .build();
    }

    private EvidenceCandidateVO loadCandidateContent(EvidenceCandidateVO candidate) {
        candidate.setContent(loadContent(candidate.getContentRef()));
        return candidate;
    }

    private String loadContent(String contentRef) {
        if (payloadRepository == null || contentRef == null || contentRef.isBlank()) {
            return null;
        }
        return payloadRepository.findContent(contentRef).orElse(null);
    }

    private double overlap(String query, String text) {
        double score = 0.0;
        for (String token : query.split("\\s+")) {
            if (!token.isBlank() && text.contains(token)) {
                score += 1.0;
            }
        }
        return score;
    }
}
