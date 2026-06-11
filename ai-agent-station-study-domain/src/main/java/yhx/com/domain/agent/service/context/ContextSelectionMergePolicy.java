package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.MessageCandidateVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ContextSelectionMergePolicy {

    public List<ContextSelectionVO> merge(List<ContextSelectionVO> selections, ContextCandidateBundleVO candidates) {
        if (selections == null || selections.isEmpty()) {
            return List.of();
        }
        Map<String, ContextSelectionVO> merged = new LinkedHashMap<>();
        for (ContextSelectionVO selection : selections) {
            if (selection == null || selection.getSourceId() == null || selection.getContextLevel() == null) {
                continue;
            }
            String key = sourceKey(selection, candidates);
            ContextSelectionVO existing = merged.get(key);
            if (existing == null || stronger(selection, existing)) {
                merged.put(key, selection);
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(ContextSelectionVO::getPriority, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    public boolean coveredByFixedContext(SummaryCandidateVO summary, ContextCandidateBundleVO candidates) {
        if (summary == null || candidates == null || candidates.getFixedRecentMessages() == null) {
            return false;
        }
        if (summary.getTurnId() == null && summary.getMessageStartSeq() == null && summary.getMessageEndSeq() == null) {
            return false;
        }
        for (MessageCandidateVO message : candidates.getFixedRecentMessages()) {
            if (message == null) {
                continue;
            }
            if (summary.getTurnId() != null && summary.getTurnId().equals(message.getTurnId())) {
                return true;
            }
            if (message.getSeq() != null && summary.getMessageStartSeq() != null && summary.getMessageEndSeq() != null
                    && message.getSeq() >= summary.getMessageStartSeq()
                    && message.getSeq() <= summary.getMessageEndSeq()) {
                return true;
            }
        }
        return false;
    }

    private boolean stronger(ContextSelectionVO candidate, ContextSelectionVO existing) {
        int candidateRank = levelRank(candidate.getContextLevel());
        int existingRank = levelRank(existing.getContextLevel());
        if (candidateRank != existingRank) {
            return candidateRank > existingRank;
        }
        Integer candidatePriority = candidate.getPriority();
        Integer existingPriority = existing.getPriority();
        if (candidatePriority == null) {
            return false;
        }
        if (existingPriority == null) {
            return true;
        }
        return candidatePriority < existingPriority;
    }

    private String sourceKey(ContextSelectionVO selection, ContextCandidateBundleVO candidates) {
        String sourceType = selection.getSourceType() == null ? "" : selection.getSourceType();
        String sourceId = selection.getSourceId();
        if ("TURN_SUMMARY".equals(sourceType) || "SESSION_SUMMARY".equals(sourceType) || "SUMMARY".equals(sourceType)) {
            return summaryKey(sourceId, candidates);
        }
        if ("TURN".equals(sourceType)) {
            return "TURN:" + sourceId;
        }
        if ("LONG_TERM_MEMORY".equals(sourceType) || "USER_PREFERENCE".equals(sourceType)) {
            return "MEMORY:" + sourceId;
        }
        if ("ARTIFACT".equals(sourceType) || "ARTIFACT_CHUNK".equals(sourceType)) {
            return artifactKey(sourceType, sourceId, candidates);
        }
        if ("RAG".equals(sourceType) || "RAG_CHUNK".equals(sourceType) || "RAG_DOCUMENT".equals(sourceType)) {
            return "RAG:" + sourceId;
        }
        return sourceType + ":" + sourceId;
    }

    private String artifactKey(String sourceType, String sourceId, ContextCandidateBundleVO candidates) {
        if ("ARTIFACT".equals(sourceType)) {
            return "ARTIFACT:" + sourceId;
        }
        if (candidates != null && candidates.getArtifactCandidates() != null) {
            return candidates.getArtifactCandidates().stream()
                    .filter(artifact -> artifact.getMatchedChunks() != null)
                    .filter(artifact -> artifact.getMatchedChunks().stream()
                            .anyMatch(chunk -> Objects.equals(sourceId, chunk.getChunkId()) || Objects.equals(sourceId, chunk.getSourceId())))
                    .map(artifact -> "ARTIFACT:" + artifact.getArtifactId())
                    .findFirst()
                    .orElse("ARTIFACT_CHUNK:" + sourceId);
        }
        return "ARTIFACT_CHUNK:" + sourceId;
    }

    private String summaryKey(String summaryId, ContextCandidateBundleVO candidates) {
        if (candidates != null && candidates.getSessionSummaries() != null) {
            return candidates.getSessionSummaries().stream()
                    .filter(summary -> Objects.equals(summaryId, summary.getSummaryId()))
                    .map(summary -> summary.getTurnId() == null ? "SUMMARY:" + summaryId : "TURN:" + summary.getTurnId())
                    .findFirst()
                    .orElse("SUMMARY:" + summaryId);
        }
        return "SUMMARY:" + summaryId;
    }

    private int levelRank(ContextLevelEnumVO level) {
        if (level == null) {
            return 0;
        }
        return switch (level) {
            case METADATA_ONLY -> 1;
            case SUMMARY_ONLY -> 2;
            case SUMMARY_PLUS_SNIPPET -> 3;
            case CHUNKED_CONTEXT -> 4;
            case FULL_TEXT -> 5;
        };
    }
}
