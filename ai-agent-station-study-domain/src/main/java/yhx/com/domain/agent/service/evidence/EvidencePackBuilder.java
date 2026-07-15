package yhx.com.domain.agent.service.evidence;

import yhx.com.domain.agent.model.valobj.context.EvidenceCandidateVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                .boundedSnippet(truncate(firstNonBlank(candidate.getContent(), candidate.getSummary())))
                .content(candidate.getContent())
                .contentRef(candidate.getContentRef())
                .contentFormat(candidate.getContentFormat())
                .metadata(metadata(candidate))
                .build()).toList();
    }

    private Map<String, Object> metadata(EvidenceCandidateVO candidate) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfNotBlank(metadata, "verificationStatus", candidate.getVerificationStatus());
        putIfNotBlank(metadata, "failureCode", candidate.getFailureCode());
        return metadata.isEmpty() ? null : metadata;
    }

    private void putIfNotBlank(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String truncate(String text) {
        if (text == null || text.length() <= maxSummaryChars) {
            return text;
        }
        return text.substring(0, maxSummaryChars);
    }
}
