package yhx.com.domain.agent.service.artifact;

import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ArtifactResolver {

    private final IArtifactRepository artifactRepository;

    public ArtifactResolver(IArtifactRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    public Optional<AgentArtifactEntity> resolveById(String artifactId) {
        if (artifactId == null || artifactId.isBlank()) {
            return Optional.empty();
        }
        return artifactRepository.findArtifact(artifactId);
    }

    public Optional<ArtifactCandidateVO> resolveCandidate(String userInput, List<ArtifactCandidateVO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        String input = userInput == null ? "" : userInput.toLowerCase();
        Optional<ArtifactCandidateVO> exactId = candidates.stream()
                .filter(candidate -> candidate.getArtifactId() != null && input.contains(candidate.getArtifactId().toLowerCase()))
                .findFirst();
        if (exactId.isPresent()) {
            return exactId;
        }
        return candidates.stream()
                .sorted(Comparator.comparing(ArtifactCandidateVO::getTotalScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst();
    }

    public List<ArtifactCandidateVO> ambiguousCandidates(List<ArtifactCandidateVO> candidates) {
        if (candidates == null || candidates.size() <= 1) {
            return List.of();
        }
        List<ArtifactCandidateVO> sorted = candidates.stream()
                .sorted(Comparator.comparing(ArtifactCandidateVO::getTotalScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Double first = sorted.get(0).getTotalScore();
        Double second = sorted.get(1).getTotalScore();
        if (first == null || second == null || first - second < 2.0) {
            return sorted.subList(0, Math.min(3, sorted.size()));
        }
        return List.of();
    }
}
