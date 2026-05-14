package yhx.com.domain.agent.service.artifact;

import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;
import yhx.com.domain.agent.service.context.ContextTokenEstimator;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArtifactCandidateRanker {

    private final ContextTokenEstimator tokenEstimator;

    public ArtifactCandidateRanker(ContextTokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    public List<ArtifactCandidateVO> rank(String userInput, List<AgentArtifactEntity> artifacts, int limit) {
        if (artifacts == null || artifacts.isEmpty()) {
            return List.of();
        }
        return artifacts.stream()
                .map(artifact -> toCandidate(userInput == null ? "" : userInput, artifact))
                .sorted(Comparator.comparing(ArtifactCandidateVO::getTotalScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(limit, 0))
                .toList();
    }

    private ArtifactCandidateVO toCandidate(String userInput, AgentArtifactEntity artifact) {
        String lowerInput = userInput.toLowerCase();
        List<String> aliases = buildAliases(artifact);
        List<String> reasons = new ArrayList<>();
        double titleScore = contains(lowerInput, artifact.getTitle()) ? 10.0 : 0.0;
        if (titleScore > 0) {
            reasons.add("title-mentioned");
        }
        double aliasScore = aliases.stream().anyMatch(alias -> contains(lowerInput, alias)) ? 8.0 : 0.0;
        if (aliasScore > 0) {
            reasons.add("alias-mentioned");
        }
        double typeScore = contains(lowerInput, artifact.getArtifactType()) ? 4.0 : 0.0;
        if (typeScore > 0) {
            reasons.add("type-mentioned");
        }
        double referenceScore = containsAny(lowerInput, "this", "that", "previous", "latest", "second", "version", "这个", "那个", "上一", "最新", "第二") ? 3.0 : 0.0;
        if (referenceScore > 0) {
            reasons.add("reference-word");
        }
        double recencyScore = recencyScore(artifact.getUpdatedAt() == null ? artifact.getCreatedAt() : artifact.getUpdatedAt());
        if (recencyScore > 0) {
            reasons.add("recent");
        }
        double totalScore = titleScore + aliasScore + typeScore + referenceScore + recencyScore;
        return ArtifactCandidateVO.builder()
                .artifactId(artifact.getArtifactId())
                .artifactType(artifact.getArtifactType())
                .title(artifact.getTitle())
                .summary(artifact.getSummary())
                .aliases(aliases)
                .contentRef(artifact.getContentRef())
                .tokenCount(tokenEstimator.estimateTextTokens(artifact.getSummary()))
                .version(artifact.getVersion())
                .createdAt(artifact.getCreatedAt())
                .updatedAt(artifact.getUpdatedAt())
                .recencyScore(recencyScore)
                .aliasScore(aliasScore)
                .titleScore(titleScore)
                .totalScore(totalScore)
                .reasons(reasons)
                .build();
    }

    private boolean contains(String lowerInput, String value) {
        return value != null && !value.isBlank() && lowerInput.contains(value.toLowerCase());
    }

    private boolean containsAny(String lowerInput, String... values) {
        for (String value : values) {
            if (lowerInput.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildAliases(AgentArtifactEntity artifact) {
        List<String> aliases = new ArrayList<>();
        if (artifact.getTitle() != null && !artifact.getTitle().isBlank()) {
            aliases.add(artifact.getTitle());
        }
        if (artifact.getArtifactType() != null && !artifact.getArtifactType().isBlank()) {
            aliases.add(artifact.getArtifactType());
        }
        return aliases;
    }

    private double recencyScore(LocalDateTime time) {
        if (time == null) {
            return 0.0;
        }
        long hours = Math.max(1, Duration.between(time, LocalDateTime.now()).toHours());
        return Math.max(0.0, 5.0 - Math.min(5.0, hours / 24.0));
    }
}
