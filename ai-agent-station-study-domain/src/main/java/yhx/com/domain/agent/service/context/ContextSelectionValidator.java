package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContextSelectionValidator {

    public List<ContextSelectionVO> validate(List<ContextSelectionVO> selections, ContextCandidateBundleVO candidates) {
        if (selections == null || selections.isEmpty()) {
            return List.of();
        }
        Set<String> validIds = new HashSet<>();
        if (candidates.getRecentMessages() != null) {
            candidates.getRecentMessages().forEach(message -> validIds.add(message.getMessageId()));
        }
        if (candidates.getSessionSummaries() != null) {
            candidates.getSessionSummaries().forEach(summary -> {
                validIds.add(summary.getSummaryId());
                validIds.add(summary.getTurnId());
            });
        }
        if (candidates.getArtifactCandidates() != null) {
            candidates.getArtifactCandidates().forEach(artifact -> validIds.add(artifact.getArtifactId()));
        }
        if (candidates.getMemoryCandidates() != null) {
            candidates.getMemoryCandidates().forEach(memory -> validIds.add(memory.getMemoryId()));
        }
        if (candidates.getEvidenceCandidates() != null) {
            candidates.getEvidenceCandidates().forEach(evidence -> validIds.add(evidence.getEvidenceId()));
        }
        return selections.stream()
                .filter(selection -> selection.getSourceId() != null && validIds.contains(selection.getSourceId()))
                .filter(selection -> selection.getContextLevel() != null)
                .toList();
    }
}
