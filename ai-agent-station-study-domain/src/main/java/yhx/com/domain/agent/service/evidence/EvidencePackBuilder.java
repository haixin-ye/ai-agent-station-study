package yhx.com.domain.agent.service.evidence;

import yhx.com.domain.agent.model.valobj.context.EvidenceCandidateVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;

import java.util.List;

public class EvidencePackBuilder {

    private final int maxSummaryChars;

    public EvidencePackBuilder() {
        this(500);
    }

    public EvidencePackBuilder(int maxSummaryChars) {
        this.maxSummaryChars = maxSummaryChars;
    }

    public List<MaterializedEvidenceVO> buildFromCandidates(List<EvidenceCandidateVO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream().map(candidate -> MaterializedEvidenceVO.builder()
                .evidenceId(candidate.getEvidenceId())
                .evidenceType(candidate.getEvidenceType())
                .sourceRef(candidate.getSourceRef())
                .summary(truncate(candidate.getSummary()))
                .boundedSnippet(truncate(candidate.getSummary()))
                .build()).toList();
    }

    private String truncate(String text) {
        if (text == null || text.length() <= maxSummaryChars) {
            return text;
        }
        return text.substring(0, maxSummaryChars);
    }
}
