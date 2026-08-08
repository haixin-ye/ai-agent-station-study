package yhx.com.domain.agent.service.evaluation;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IRecallEvaluationRepository;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseResultEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCorpusItemEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationDatasetEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationHitEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationRunEntity;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.RagCandidateVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.evaluation.DetailedRecallResultVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCaseMetricsVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationMetricsVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationRunConfigVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallExecutionOptionsVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallExpectedItemVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.service.memory.VectorContextRecallPreselector;
import yhx.com.domain.agent.service.rag.RagContextRecallPreselector;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RecallEvaluationRunner {

    private final IRecallEvaluationRepository repository;
    private final VectorContextRecallPreselector memoryRecall;
    private final RagContextRecallPreselector ragRecall;
    private final RecallEvaluationPlanner planner;
    private final RecallMetricsCalculator metricsCalculator;

    public RecallEvaluationRunner(IRecallEvaluationRepository repository,
                                  VectorContextRecallPreselector memoryRecall,
                                  RagContextRecallPreselector ragRecall,
                                  RecallEvaluationPlanner planner,
                                  RecallMetricsCalculator metricsCalculator) {
        this.repository = repository;
        this.memoryRecall = memoryRecall;
        this.ragRecall = ragRecall;
        this.planner = planner;
        this.metricsCalculator = metricsCalculator == null ? new RecallMetricsCalculator() : metricsCalculator;
    }

    public void execute(String evaluationRunId) {
        RecallEvaluationRunEntity run = repository.findRun(evaluationRunId)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation run does not exist: " + evaluationRunId));
        RecallEvaluationRunConfigVO config = JSON.parseObject(run.getConfigJson(), RecallEvaluationRunConfigVO.class);
        RecallEvaluationDatasetEntity dataset = repository.findDataset(run.getDatasetId())
                .orElseThrow(() -> new IllegalArgumentException("Evaluation dataset does not exist: " + run.getDatasetId()));
        List<RecallEvaluationCaseEntity> cases = repository.listCases(dataset.getDatasetId(), "ACTIVE",
                positive(config.getCaseLimit(), 10000), 0);
        run.setStatus("RUNNING");
        run.setTotalCaseCount(cases.size());
        run.setStartedAt(LocalDateTime.now());
        repository.updateRun(run);
        List<RecallCaseMetricsVO> completedMetrics = new ArrayList<>();
        for (RecallEvaluationCaseEntity testCase : cases) {
            if (cancelRequested(run.getEvaluationRunId())) {
                run.setStatus("CANCELLED");
                break;
            }
            try {
                RecallCaseMetricsVO metrics = executeCase(run, dataset, testCase, config);
                completedMetrics.add(metrics);
            } catch (Exception error) {
                persistFailure(run, testCase, error);
                run.setFailedCaseCount(number(run.getFailedCaseCount()) + 1);
            }
            run.setCompletedCaseCount(number(run.getCompletedCaseCount()) + 1);
            repository.updateRun(run);
        }
        RecallEvaluationMetricsVO aggregate = metricsCalculator.aggregate(completedMetrics);
        aggregate.setFailedCaseCount(number(run.getFailedCaseCount()));
        run.setMetricsJson(JSON.toJSONString(aggregate));
        if (!"CANCELLED".equals(run.getStatus())) {
            run.setStatus("COMPLETED");
        }
        run.setCompletedAt(LocalDateTime.now());
        repository.updateRun(run);
    }

    private RecallCaseMetricsVO executeCase(RecallEvaluationRunEntity run,
                                            RecallEvaluationDatasetEntity dataset,
                                            RecallEvaluationCaseEntity testCase,
                                            RecallEvaluationRunConfigVO config) {
        List<RecallExpectedItemVO> expected = resolveExpected(dataset.getDatasetId(), testCase.getExpectedJson());
        ContextPreparationCommand command = ContextPreparationCommand.builder()
                .runId(null)
                .userId(dataset.getEvalUserId())
                .sessionId(dataset.getEvalSessionId())
                .userInput(testCase.getQueryText())
                .build();
        RecallExecutionOptionsVO options = options(dataset.getDatasetId(), config);
        long retrievalStarted = System.currentTimeMillis();
        DetailedRecallResultVO memory = wantsMemory(testCase, config) && memoryRecall != null
                ? memoryRecall.recallDetailed(command, memoryOptions(options, config)) : null;
        DetailedRecallResultVO rag = wantsRag(testCase, config) && ragRecall != null
                ? ragRecall.recallDetailed(command, ragOptions(options, config)) : null;
        long retrievalLatency = System.currentTimeMillis() - retrievalStarted;
        List<RecallEvaluationHitEntity> hits = rankedHits(run, testCase, memory, rag,
                positive(config.getTopK(), 8));
        RecallCaseMetricsVO metrics = metricsCalculator.calculateCase(expected, hits, positive(config.getTopK(), 8));
        metrics.setRetrievalLatencyMs(retrievalLatency);

        PlannerOutcome plannerOutcome = Boolean.TRUE.equals(config.getPlannerEnabled())
                ? invokePlanner(memory, rag, hits, expected, config) : PlannerOutcome.notInvoked();
        metrics.setPlannerInvoked(plannerOutcome.invoked);
        metrics.setPlannerLatencyMs(plannerOutcome.latencyMs);
        metrics.setPlannerPrecision(plannerOutcome.precision);
        metrics.setPlannerRecall(plannerOutcome.recall);
        metrics.setPlannerHit(plannerOutcome.hit);
        metrics.setPlannerReciprocalRank(plannerOutcome.reciprocalRank);
        metrics.setPlannerNdcgAtK(plannerOutcome.ndcgAtK);
        metrics.setPlannerSelectedCount(plannerOutcome.selectedCount);
        metrics.setPlannerRelevantRetentionRate(plannerOutcome.relevantRetentionRate);
        metrics.setPlannerIrrelevantRemovalRate(plannerOutcome.irrelevantRemovalRate);
        metrics.setPlannerRelevantDroppedCount(plannerOutcome.relevantDroppedCount);
        metrics.setClarificationRequested(plannerOutcome.clarificationRequested);
        metrics.setPlannerFailed(plannerOutcome.failed);

        repository.saveHits(hits);
        repository.saveCaseResult(toResult(run, testCase, metrics, plannerOutcome));
        return metrics;
    }

    private RecallExecutionOptionsVO options(String datasetId, RecallEvaluationRunConfigVO config) {
        return RecallExecutionOptionsVO.builder()
                .topK(positive(config.getTopK(), 8))
                .minScore(config.getMinScore() == null ? 0.2D : config.getMinScore())
                .lexicalEnabled("HYBRID".equalsIgnoreCase(config.getRetrievalMode()))
                .metadataFilters(Map.of("evalDatasetId", datasetId))
                .build();
    }

    private RecallExecutionOptionsVO memoryOptions(RecallExecutionOptionsVO options, RecallEvaluationRunConfigVO config) {
        List<VectorCollectionTypeEnumVO> collections = selectedCollections(config,
                List.of(VectorCollectionTypeEnumVO.LONG_TERM_MEMORY, VectorCollectionTypeEnumVO.USER_PREFERENCE));
        return copyOptions(options, collections);
    }

    private RecallExecutionOptionsVO ragOptions(RecallExecutionOptionsVO options, RecallEvaluationRunConfigVO config) {
        List<VectorCollectionTypeEnumVO> collections = selectedCollections(config,
                List.of(VectorCollectionTypeEnumVO.RAG_DOCUMENT, VectorCollectionTypeEnumVO.RAG_CHUNK,
                        VectorCollectionTypeEnumVO.RAG_FILE_CHUNK, VectorCollectionTypeEnumVO.RAG_CODE_FILE_SUMMARY,
                        VectorCollectionTypeEnumVO.RAG_CODE_CHUNK));
        return copyOptions(options, collections);
    }

    private RecallExecutionOptionsVO copyOptions(RecallExecutionOptionsVO source,
                                                  List<VectorCollectionTypeEnumVO> collections) {
        return RecallExecutionOptionsVO.builder().topK(source.getTopK()).minScore(source.getMinScore())
                .lexicalEnabled(source.getLexicalEnabled()).metadataFilters(source.getMetadataFilters())
                .collectionTypes(collections).build();
    }

    private List<VectorCollectionTypeEnumVO> selectedCollections(RecallEvaluationRunConfigVO config,
                                                                  List<VectorCollectionTypeEnumVO> defaults) {
        if (config.getCollectionTypes() == null || config.getCollectionTypes().isEmpty()) {
            return defaults;
        }
        Set<String> selected = new HashSet<>(config.getCollectionTypes());
        return defaults.stream().filter(value -> selected.contains(value.name())).toList();
    }

    private List<RecallEvaluationHitEntity> rankedHits(RecallEvaluationRunEntity run,
                                                        RecallEvaluationCaseEntity testCase,
                                                        DetailedRecallResultVO memory,
                                                        DetailedRecallResultVO rag,
                                                        int topK) {
        Map<String, RankedRawHit> merged = new LinkedHashMap<>();
        addHits(merged, memory, "MEMORY");
        addHits(merged, rag, "RAG");
        List<RankedRawHit> ranked = merged.values().stream()
                .sorted(Comparator.comparing((RankedRawHit value) -> value.hit.getScore(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(topK)
                .toList();
        List<RecallEvaluationHitEntity> result = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            RankedRawHit value = ranked.get(index);
            VectorRecallHitVO hit = value.hit;
            result.add(RecallEvaluationHitEntity.builder()
                    .hitId("eval-hit-" + UUID.randomUUID())
                    .evaluationRunId(run.getEvaluationRunId())
                    .caseId(testCase.getCaseId())
                    .rankNo(index + 1)
                    .retrievalChannel(value.channel)
                    .collectionType(hit.getCollectionType() == null ? null : hit.getCollectionType().name())
                    .sourceType(hit.getSourceType() == null ? null : hit.getSourceType().name())
                    .sourceId(hit.getSourceId())
                    .parentSourceId(metadata(hit, "documentId"))
                    .score(decimal(hit.getScore()))
                    .selectedByPlanner(false)
                    .candidateJson(JSON.toJSONString(hit))
                    .build());
        }
        return result;
    }

    private void addHits(Map<String, RankedRawHit> merged, DetailedRecallResultVO result, String source) {
        if (result == null) {
            return;
        }
        addHits(merged, result.getVectorHits(), source + "_VECTOR");
        addHits(merged, result.getLexicalHits(), source + "_LEXICAL");
    }

    private void addHits(Map<String, RankedRawHit> merged, List<VectorRecallHitVO> hits, String channel) {
        if (hits == null) {
            return;
        }
        for (VectorRecallHitVO hit : hits) {
            if (hit == null || hit.getSourceId() == null) {
                continue;
            }
            String key = String.valueOf(hit.getCollectionType()) + "|" + hit.getSourceId();
            RankedRawHit existing = merged.get(key);
            if (existing == null || score(hit) > score(existing.hit)) {
                merged.put(key, new RankedRawHit(hit, channel));
            }
        }
    }

    private PlannerOutcome invokePlanner(DetailedRecallResultVO memory,
                                         DetailedRecallResultVO rag,
                                         List<RecallEvaluationHitEntity> hits,
                                         List<RecallExpectedItemVO> expected,
                                         RecallEvaluationRunConfigVO config) {
        long startedAt = System.currentTimeMillis();
        try {
            ContextCandidateBundleVO bundle = memory == null || memory.getCandidateBundle() == null
                    ? ContextCandidateBundleVO.builder().memoryCandidates(List.of()).sessionSummaries(List.of())
                    .artifactCandidates(List.of()).evidenceCandidates(List.of()).build()
                    : memory.getCandidateBundle();
            bundle.setRagCandidates(rag == null || rag.getRagCandidates() == null ? List.of() : rag.getRagCandidates());
            ContextPlannerOutputVO output = planner.plan(bundle, config);
            Set<String> selected = selectedIds(output);
            hits.forEach(hit -> hit.setSelectedByPlanner(selected.contains(hit.getSourceId())
                    || selected.contains(hit.getParentSourceId())));
            int selectedCount = (int) hits.stream().filter(hit -> Boolean.TRUE.equals(hit.getSelectedByPlanner())).count();
            int selectedRelevant = (int) hits.stream().filter(hit -> Boolean.TRUE.equals(hit.getSelectedByPlanner())
                    && hit.getExpectedGrade() != null).count();
            int relevantRetrieved = (int) hits.stream().filter(hit -> hit.getExpectedGrade() != null).count();
            int irrelevantRetrieved = hits.size() - relevantRetrieved;
            int selectedIrrelevant = selectedCount - selectedRelevant;
            List<RecallEvaluationHitEntity> selectedHits = hits.stream()
                    .filter(hit -> Boolean.TRUE.equals(hit.getSelectedByPlanner())).toList();
            RecallCaseMetricsVO selectedMetrics = metricsCalculator.calculateCase(expected, selectedHits,
                    positive(config.getTopK(), 8));
            return new PlannerOutcome(true, false,
                    output != null && output.getClarificationRequest() != null && !output.getClarificationRequest().isEmpty(),
                    System.currentTimeMillis() - startedAt,
                    selectedMetrics.getPrecisionAtK(), selectedMetrics.getRecallAtK(),
                    Boolean.TRUE.equals(selectedMetrics.getHit()), selectedMetrics.getReciprocalRank(),
                    selectedMetrics.getNdcgAtK(), selectedCount,
                    relevantRetrieved == 0 ? 0D : selectedRelevant / (double) relevantRetrieved,
                    irrelevantRetrieved == 0 ? 0D
                            : (irrelevantRetrieved - selectedIrrelevant) / (double) irrelevantRetrieved,
                    Math.max(0, relevantRetrieved - selectedRelevant),
                    output, selected);
        } catch (Exception error) {
            return new PlannerOutcome(true, true, false, System.currentTimeMillis() - startedAt,
                    0D, 0D, false, 0D, 0D, 0, 0D, 0D, 0,
                    ContextPlannerOutputVO.builder().status("FAILED").reason(readable(error)).build(), Set.of());
        }
    }

    private Set<String> selectedIds(ContextPlannerOutputVO output) {
        Set<String> ids = new HashSet<>();
        if (output == null || output.getSelectedContext() == null) {
            return ids;
        }
        for (Map<String, Object> item : output.getSelectedContext()) {
            if (item == null) {
                continue;
            }
            for (String key : List.of("candidateId", "sourceId", "memoryId", "summaryId", "documentId", "chunkId")) {
                Object value = item.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    ids.add(String.valueOf(value));
                }
            }
        }
        return ids;
    }

    private RecallEvaluationCaseResultEntity toResult(RecallEvaluationRunEntity run,
                                                       RecallEvaluationCaseEntity testCase,
                                                       RecallCaseMetricsVO metrics,
                                                       PlannerOutcome plannerOutcome) {
        return RecallEvaluationCaseResultEntity.builder()
                .evaluationRunId(run.getEvaluationRunId()).caseId(testCase.getCaseId()).status("COMPLETED")
                .retrievalLatencyMs(metrics.getRetrievalLatencyMs()).plannerLatencyMs(metrics.getPlannerLatencyMs())
                .hit(metrics.getHit()).precisionAtK(decimal(metrics.getPrecisionAtK())).recallAtK(decimal(metrics.getRecallAtK()))
                .reciprocalRank(decimal(metrics.getReciprocalRank())).ndcgAtK(decimal(metrics.getNdcgAtK()))
                .averagePrecisionAtK(decimal(metrics.getAveragePrecisionAtK()))
                .plannerStatus(plannerOutcome.output == null ? null : plannerOutcome.output.getStatus())
                .plannerReason(plannerOutcome.output == null ? null : plannerOutcome.output.getReason())
                .plannerSelectedIdsJson(JSON.toJSONString(plannerOutcome.selectedIds))
                .plannerOutputJson(plannerOutcome.output == null ? null : JSON.toJSONString(plannerOutcome.output))
                .build();
    }

    private List<RecallExpectedItemVO> resolveExpected(String datasetId, String expectedJson) {
        List<RecallExpectedItemVO> values = JSON.parseArray(expectedJson, RecallExpectedItemVO.class);
        if (values == null) {
            return List.of();
        }
        for (RecallExpectedItemVO value : values) {
            if (value.getSourceId() != null || value.getExternalId() == null) {
                continue;
            }
            RecallEvaluationCorpusItemEntity item = repository.findCorpusItemByExternalId(datasetId, value.getExternalId()).orElse(null);
            if (item != null) {
                value.setSourceId(item.getSourceId());
                if (value.getMatchMode() == null) {
                    value.setMatchMode("RAG_DOCUMENT".equals(item.getItemType()) ? "PARENT_DOCUMENT" : "EXACT_SOURCE");
                }
            }
        }
        return values;
    }

    private void persistFailure(RecallEvaluationRunEntity run, RecallEvaluationCaseEntity testCase, Exception error) {
        repository.saveCaseResult(RecallEvaluationCaseResultEntity.builder()
                .evaluationRunId(run.getEvaluationRunId()).caseId(testCase.getCaseId()).status("FAILED")
                .failureStage("CASE_EXECUTION").failureCode("EVALUATION_CASE_FAILED").failureMessage(readable(error)).build());
    }

    private boolean cancelRequested(String runId) {
        return repository.findRun(runId).map(value -> Boolean.TRUE.equals(value.getCancelRequested())).orElse(false);
    }

    private boolean wantsMemory(RecallEvaluationCaseEntity testCase, RecallEvaluationRunConfigVO config) {
        String scope = effectiveScope(testCase, config);
        return "MEMORY".equals(scope) || "MIXED".equals(scope);
    }

    private boolean wantsRag(RecallEvaluationCaseEntity testCase, RecallEvaluationRunConfigVO config) {
        String scope = effectiveScope(testCase, config);
        return "RAG".equals(scope) || "MIXED".equals(scope);
    }

    private String effectiveScope(RecallEvaluationCaseEntity testCase, RecallEvaluationRunConfigVO config) {
        String scope = testCase.getSourceScope();
        if (scope == null || scope.isBlank() || "DEFAULT".equalsIgnoreCase(scope)) {
            scope = config.getSourceScope();
        }
        return scope == null ? "MIXED" : scope.toUpperCase();
    }

    private String metadata(VectorRecallHitVO hit, String key) {
        Object value = hit.getMetadata() == null ? null : hit.getMetadata().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private double score(VectorRecallHitVO hit) {
        return hit == null || hit.getScore() == null ? 0D : hit.getScore();
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }

    private String readable(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private record RankedRawHit(VectorRecallHitVO hit, String channel) {
    }

    private record PlannerOutcome(boolean invoked, boolean failed, boolean clarificationRequested,
                                  long latencyMs, double precision, double recall, boolean hit,
                                  double reciprocalRank, double ndcgAtK, int selectedCount,
                                  double relevantRetentionRate, double irrelevantRemovalRate,
                                  int relevantDroppedCount,
                                  ContextPlannerOutputVO output, Set<String> selectedIds) {
        private static PlannerOutcome notInvoked() {
            return new PlannerOutcome(false, false, false, 0L, 0D, 0D, false,
                    0D, 0D, 0, 0D, 0D, 0, null, Set.of());
        }
    }
}
