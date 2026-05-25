package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;
import yhx.com.domain.agent.model.valobj.context.MemoryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;
import yhx.com.domain.agent.service.memory.VectorContextRecallPreselector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ContextPreparationService {

    private final ContextCandidatePreselector contextCandidatePreselector;
    private final VectorContextRecallPreselector vectorContextRecallPreselector;
    private final Executor recallExecutor;
    private final Duration vectorRecallTimeout;

    public ContextPreparationService(ContextCandidatePreselector contextCandidatePreselector) {
        this(contextCandidatePreselector, null, null, Duration.ZERO);
    }

    public ContextPreparationService(ContextCandidatePreselector contextCandidatePreselector,
                                     VectorContextRecallPreselector vectorContextRecallPreselector,
                                     Executor recallExecutor,
                                     Duration vectorRecallTimeout) {
        this.contextCandidatePreselector = contextCandidatePreselector;
        this.vectorContextRecallPreselector = vectorContextRecallPreselector;
        this.recallExecutor = recallExecutor;
        this.vectorRecallTimeout = vectorRecallTimeout == null ? Duration.ZERO : vectorRecallTimeout;
    }

    public ContextCandidateBundleVO prepare(ContextPreparationCommand command) {
        if (vectorContextRecallPreselector == null || recallExecutor == null) {
            return contextCandidatePreselector.buildCandidates(command);
        }
        CompletableFuture<ContextCandidateBundleVO> mysqlFuture = CompletableFuture.supplyAsync(
                () -> contextCandidatePreselector.buildCandidates(command), recallExecutor);
        CompletableFuture<ContextCandidateBundleVO> vectorFuture = CompletableFuture.supplyAsync(
                        () -> vectorContextRecallPreselector.recall(command), recallExecutor)
                .completeOnTimeout(emptyBundle(), Math.max(1, vectorRecallTimeout.toMillis()), java.util.concurrent.TimeUnit.MILLISECONDS)
                .exceptionally(error -> emptyBundle());

        ContextCandidateBundleVO mysqlBundle = mysqlFuture.join();
        ContextCandidateBundleVO vectorBundle = vectorFuture.join();
        return merge(mysqlBundle, vectorBundle);
    }

    private ContextCandidateBundleVO merge(ContextCandidateBundleVO mysqlBundle, ContextCandidateBundleVO vectorBundle) {
        if (vectorBundle == null) {
            return mysqlBundle;
        }
        mysqlBundle.setSessionSummaries(mergeSummaries(mysqlBundle.getSessionSummaries(), vectorBundle.getSessionSummaries()));
        mysqlBundle.setArtifactCandidates(mergeArtifacts(mysqlBundle.getArtifactCandidates(), vectorBundle.getArtifactCandidates()));
        mysqlBundle.setMemoryCandidates(mergeMemories(mysqlBundle.getMemoryCandidates(), vectorBundle.getMemoryCandidates()));
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
        return existing;
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

    private MemoryCandidateVO mergeMemory(MemoryCandidateVO existing, MemoryCandidateVO vector) {
        existing.setSourceChannel(vector.getSourceChannel());
        existing.setSourceScore(vector.getSourceScore());
        existing.setRelevanceScore(vector.getRelevanceScore());
        return existing;
    }

    private ContextCandidateBundleVO emptyBundle() {
        return ContextCandidateBundleVO.builder()
                .sessionSummaries(List.of())
                .artifactCandidates(List.of())
                .memoryCandidates(List.of())
                .build();
    }
}
