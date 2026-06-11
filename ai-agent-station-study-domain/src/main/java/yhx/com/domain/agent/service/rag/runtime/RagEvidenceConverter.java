package yhx.com.domain.agent.service.rag.runtime;

import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.valobj.rag.RagHitVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class RagEvidenceConverter {

    private static final int DEFAULT_MAX_CHARS = 512;

    private final RagEvidenceSnippetPolicy snippetPolicy;
    private final int maxSummaryChars;

    public RagEvidenceConverter() {
        this(new RagEvidenceSnippetPolicy(), DEFAULT_MAX_CHARS);
    }

    public RagEvidenceConverter(RagEvidenceSnippetPolicy snippetPolicy, int maxSummaryChars) {
        this.snippetPolicy = snippetPolicy;
        this.maxSummaryChars = maxSummaryChars;
    }

    public List<AgentEvidenceEntity> convert(String runId, String sessionId, String ragQueryId, List<RagHitVO> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        return hits.stream()
                .filter(this::usable)
                .map(hit -> toEvidence(runId, hit, now))
                .toList();
    }

    private boolean usable(RagHitVO hit) {
        return hit != null
                && hit.getRagHitId() != null
                && !hit.getRagHitId().isBlank()
                && snippetPolicy.boundedSnippet(hit.getChunkText(), maxSummaryChars) != null;
    }

    private AgentEvidenceEntity toEvidence(String runId, RagHitVO hit, LocalDateTime now) {
        return AgentEvidenceEntity.builder()
                .runId(runId)
                .evidenceType("RAG")
                .sourceRef(hit.getRagHitId())
                .summary(snippetPolicy.summarizeHit(hit, maxSummaryChars))
                .confidence(hit.getScore() == null ? null : BigDecimal.valueOf(hit.getScore()))
                .usedByFinal(false)
                .createdAt(now)
                .build();
    }
}
