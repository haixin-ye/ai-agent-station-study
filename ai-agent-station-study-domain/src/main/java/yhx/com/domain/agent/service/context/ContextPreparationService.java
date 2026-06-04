package yhx.com.domain.agent.service.context;

import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;
import yhx.com.domain.agent.model.valobj.context.ArtifactChunkVO;
import yhx.com.domain.agent.model.valobj.context.MemoryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.EvidenceCandidateVO;
import yhx.com.domain.agent.model.valobj.context.RagCandidateVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;
import yhx.com.domain.agent.service.memory.VectorContextRecallPreselector;
import yhx.com.domain.agent.service.observability.AutoAgentHumanLog;
import yhx.com.domain.agent.service.rag.RagContextRecallPreselector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ContextPreparationService {

    private final ContextCandidatePreselector contextCandidatePreselector;
    private final VectorContextRecallPreselector vectorContextRecallPreselector;
    private final RagContextRecallPreselector ragContextRecallPreselector;
    private final Executor recallExecutor;
    private final Duration vectorRecallTimeout;
    private final Duration ragRecallTimeout;

    public ContextPreparationService(ContextCandidatePreselector contextCandidatePreselector) {
        this(contextCandidatePreselector, null, null, Duration.ZERO);
    }

    public ContextPreparationService(ContextCandidatePreselector contextCandidatePreselector,
                                     VectorContextRecallPreselector vectorContextRecallPreselector,
                                     Executor recallExecutor,
                                     Duration vectorRecallTimeout) {
        this(contextCandidatePreselector, vectorContextRecallPreselector, null, recallExecutor, vectorRecallTimeout, Duration.ZERO);
    }

    public ContextPreparationService(ContextCandidatePreselector contextCandidatePreselector,
                                     VectorContextRecallPreselector vectorContextRecallPreselector,
                                     RagContextRecallPreselector ragContextRecallPreselector,
                                     Executor recallExecutor,
                                     Duration vectorRecallTimeout,
                                     Duration ragRecallTimeout) {
        this.contextCandidatePreselector = contextCandidatePreselector;
        this.vectorContextRecallPreselector = vectorContextRecallPreselector;
        this.ragContextRecallPreselector = ragContextRecallPreselector;
        this.recallExecutor = recallExecutor;
        this.vectorRecallTimeout = vectorRecallTimeout == null ? Duration.ZERO : vectorRecallTimeout;
        this.ragRecallTimeout = ragRecallTimeout == null ? Duration.ZERO : ragRecallTimeout;
    }

    public ContextCandidateBundleVO prepare(ContextPreparationCommand command) {
        boolean vectorEnabled = command == null || !Boolean.FALSE.equals(command.getVectorRecallEnabled());
        boolean ragEnabled = command == null || !Boolean.FALSE.equals(command.getRagRecallEnabled());
        boolean hasVectorRecall = vectorEnabled && vectorContextRecallPreselector != null;
        boolean hasRagRecall = ragEnabled && ragContextRecallPreselector != null;
        if ((!hasVectorRecall && !hasRagRecall) || recallExecutor == null) {
            ContextCandidateBundleVO candidates = contextCandidatePreselector.buildCandidates(command);
            if (candidates != null && candidates.getRagCandidates() == null) {
                candidates.setRagCandidates(List.of());
            }
            return candidates;
        }
        CompletableFuture<ContextCandidateBundleVO> mysqlFuture = CompletableFuture.supplyAsync(
                () -> contextCandidatePreselector.buildCandidates(command), recallExecutor);
        CompletableFuture<ContextCandidateBundleVO> vectorFuture = hasVectorRecall
                ? vectorFuture(command)
                : CompletableFuture.completedFuture(emptyBundle());
        CompletableFuture<List<RagCandidateVO>> ragFuture = hasRagRecall
                ? ragFuture(command)
                : CompletableFuture.completedFuture(List.of());

        ContextCandidateBundleVO mysqlBundle = mysqlFuture.join();
        ContextCandidateBundleVO vectorBundle = vectorFuture.join();
        List<RagCandidateVO> ragCandidates = ragFuture.join();
        return merge(mysqlBundle, vectorBundle, ragCandidates);
    }

    private CompletableFuture<ContextCandidateBundleVO> vectorFuture(ContextPreparationCommand command) {
        if (vectorContextRecallPreselector == null) {
            return CompletableFuture.completedFuture(emptyBundle());
        }
        CompletableFuture<ContextCandidateBundleVO> vectorFuture = CompletableFuture.supplyAsync(
                () -> vectorContextRecallPreselector.recall(command), recallExecutor);
        if (!vectorRecallTimeout.isZero() && !vectorRecallTimeout.isNegative()) {
            vectorFuture = vectorFuture.orTimeout(Math.max(1, vectorRecallTimeout.toMillis()), TimeUnit.MILLISECONDS);
        }
        return vectorFuture.exceptionally(error -> {
            log.warn("[AutoAgent][memory-vector-recall-failed] runId={}, sessionId={}, reason={}",
                    command == null ? null : command.getRunId(),
                    command == null ? null : command.getSessionId(),
                    error == null ? null : error.toString());
            AutoAgentHumanLog.vectorRecallFailed(command == null ? null : command.getRunId(),
                    command == null ? null : command.getSessionId(),
                    vectorRecallTimeout.toMillis(),
                    error);
            return emptyBundle();
        });
    }

    private CompletableFuture<List<RagCandidateVO>> ragFuture(ContextPreparationCommand command) {
        if (ragContextRecallPreselector == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        CompletableFuture<List<RagCandidateVO>> ragFuture = CompletableFuture.supplyAsync(
                () -> ragContextRecallPreselector.recall(command), recallExecutor);
        if (!ragRecallTimeout.isZero() && !ragRecallTimeout.isNegative()) {
            ragFuture = ragFuture.orTimeout(Math.max(1, ragRecallTimeout.toMillis()), TimeUnit.MILLISECONDS);
        }
        return ragFuture.exceptionally(error -> {
            log.warn("[AutoAgent][rag-recall-failed] runId={}, sessionId={}, reason={}",
                    command == null ? null : command.getRunId(),
                    command == null ? null : command.getSessionId(),
                    error == null ? null : error.toString());
            return List.of();
        });
    }

    private ContextCandidateBundleVO merge(ContextCandidateBundleVO mysqlBundle, ContextCandidateBundleVO vectorBundle, List<RagCandidateVO> ragCandidates) {
        if (mysqlBundle == null) {
            mysqlBundle = emptyBundle();
        }
        if (vectorBundle == null) {
            vectorBundle = emptyBundle();
        }
        mysqlBundle.setSessionSummaries(mergeSummaries(mysqlBundle.getSessionSummaries(), vectorBundle.getSessionSummaries()));
        mysqlBundle.setArtifactCandidates(List.of());
        mysqlBundle.setMemoryCandidates(mergeMemories(mysqlBundle.getMemoryCandidates(), vectorBundle.getMemoryCandidates()));
        mysqlBundle.setEvidenceCandidates(mergeEvidence(mysqlBundle.getEvidenceCandidates(), vectorBundle.getEvidenceCandidates()));
        mysqlBundle.setRagCandidates(ragCandidates == null ? List.of() : ragCandidates);
        if (mysqlBundle.getTokenBudget() != null) {
            mysqlBundle.getTokenBudget().setCurrentCandidateTokens(new ContextTokenEstimator().estimateObjectTokens(mysqlBundle));
        }
        return mysqlBundle;
    }

    private List<SummaryCandidateVO> mergeSummaries(List<SummaryCandidateVO> mysql, List<SummaryCandidateVO> vector) {
        Map<String, SummaryCandidateVO> merged = new LinkedHashMap<>();
        if (mysql != null) {
            mysql.forEach(summary -> merged.put(summary.getSummaryId(), summary));
        }
        if (vector != null) {
            vector.forEach(summary -> merged.putIfAbsent(summary.getSummaryId(), summary));
        }
        return new ArrayList<>(merged.values());
    }

    private List<ArtifactCandidateVO> mergeArtifacts(List<ArtifactCandidateVO> mysql, List<ArtifactCandidateVO> vector) {
        Map<String, ArtifactCandidateVO> merged = new LinkedHashMap<>();
        if (mysql != null) {
            mysql.forEach(artifact -> merged.put(artifact.getArtifactId(), artifact));
        }
        if (vector != null) {
            vector.forEach(artifact -> merged.merge(artifact.getArtifactId(), artifact, this::mergeArtifact));
        }
        return new ArrayList<>(merged.values());
    }

    private ArtifactCandidateVO mergeArtifact(ArtifactCandidateVO existing, ArtifactCandidateVO vector) {
        existing.setSourceChannel(vector.getSourceChannel());
        existing.setSourceScore(vector.getSourceScore());
        List<String> reasons = new ArrayList<>();
        if (existing.getReasons() != null) {
            reasons.addAll(existing.getReasons());
        }
        if (vector.getReasons() != null) {
            reasons.addAll(vector.getReasons());
        }
        existing.setReasons(reasons);
        existing.setMatchedChunks(mergeChunks(existing.getMatchedChunks(), vector.getMatchedChunks()));
        return existing;
    }

    private List<ArtifactChunkVO> mergeChunks(List<ArtifactChunkVO> existing, List<ArtifactChunkVO> incoming) {
        Map<String, ArtifactChunkVO> merged = new LinkedHashMap<>();
        if (existing != null) {
            existing.forEach(chunk -> merged.put(chunkKey(chunk), chunk));
        }
        if (incoming != null) {
            incoming.forEach(chunk -> merged.putIfAbsent(chunkKey(chunk), chunk));
        }
        return new ArrayList<>(merged.values());
    }

    private String chunkKey(ArtifactChunkVO chunk) {
        if (chunk == null) {
            return "";
        }
        if (chunk.getChunkId() != null && !chunk.getChunkId().isBlank()) {
            return chunk.getChunkId();
        }
        return chunk.getSourceId() == null ? "" : chunk.getSourceId();
    }

    private List<MemoryCandidateVO> mergeMemories(List<MemoryCandidateVO> mysql, List<MemoryCandidateVO> vector) {
        Map<String, MemoryCandidateVO> merged = new LinkedHashMap<>();
        if (mysql != null) {
            mysql.forEach(memory -> merged.put(memory.getMemoryId(), memory));
        }
        if (vector != null) {
            vector.forEach(memory -> merged.merge(memory.getMemoryId(), memory, this::mergeMemory));
        }
        return new ArrayList<>(merged.values());
    }

    private List<EvidenceCandidateVO> mergeEvidence(List<EvidenceCandidateVO> mysql, List<EvidenceCandidateVO> vector) {
        Map<String, EvidenceCandidateVO> merged = new LinkedHashMap<>();
        if (mysql != null) {
            mysql.stream()
                    .filter(evidence -> evidence != null && evidence.getEvidenceId() != null)
                    .forEach(evidence -> merged.put(evidence.getEvidenceId(), evidence));
        }
        if (vector != null) {
            vector.stream()
                    .filter(evidence -> evidence != null && evidence.getEvidenceId() != null)
                    .forEach(evidence -> merged.putIfAbsent(evidence.getEvidenceId(), evidence));
        }
        return new ArrayList<>(merged.values());
    }

    private MemoryCandidateVO mergeMemory(MemoryCandidateVO existing, MemoryCandidateVO vector) {
        existing.setSourceChannel(vector.getSourceChannel());
        existing.setSourceScore(vector.getSourceScore());
        existing.setRelevanceScore(vector.getRelevanceScore());
        if ((existing.getContent() == null || existing.getContent().isBlank()) && vector.getContent() != null && !vector.getContent().isBlank()) {
            existing.setContent(vector.getContent());
        }
        return existing;
    }

    private ContextCandidateBundleVO emptyBundle() {
        return ContextCandidateBundleVO.builder()
                .sessionSummaries(List.of())
                .artifactCandidates(List.of())
                .memoryCandidates(List.of())
                .evidenceCandidates(List.of())
                .ragCandidates(List.of())
                .build();
    }
}
