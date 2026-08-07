package yhx.com.domain.agent.service.evaluation;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IRecallEvaluationRepository;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCorpusItemEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationDatasetEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationRunEntity;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCaseImportItemVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCaseImportResultVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCorpusImportItemVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCorpusImportResultVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationComparisonVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationMetricsVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationRunConfigVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationRunDetailVO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public class RecallEvaluationFacade {

    private final IRecallEvaluationRepository repository;
    private final RecallEvaluationIngestionService ingestionService;
    private final RecallEvaluationRunner runner;
    private final RecallEvaluationComparisonService comparisonService;
    private final Executor executor;

    public RecallEvaluationFacade(IRecallEvaluationRepository repository,
                                  RecallEvaluationIngestionService ingestionService,
                                  RecallEvaluationRunner runner,
                                  RecallEvaluationComparisonService comparisonService,
                                  Executor executor) {
        this.repository = repository;
        this.ingestionService = ingestionService;
        this.runner = runner;
        this.comparisonService = comparisonService;
        this.executor = executor;
    }

    public RecallEvaluationDatasetEntity createDataset(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Dataset name is required.");
        }
        RecallEvaluationDatasetEntity dataset = RecallEvaluationDatasetEntity.builder()
                .datasetId("eval-dataset-" + UUID.randomUUID())
                .name(name.trim())
                .description(description)
                .status("ACTIVE")
                .corpusCount(0)
                .readyCorpusCount(0)
                .caseCount(0)
                .build();
        repository.saveDataset(dataset);
        return dataset;
    }

    public List<RecallEvaluationDatasetEntity> listDatasets() {
        return repository.listDatasets();
    }

    public RecallEvaluationDatasetEntity getDataset(String datasetId) {
        return repository.findDataset(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation dataset does not exist: " + datasetId));
    }

    public RecallEvaluationDatasetEntity updateDataset(String datasetId, String name, String description) {
        RecallEvaluationDatasetEntity dataset = getDataset(datasetId);
        if (name != null && !name.isBlank()) dataset.setName(name.trim());
        if (description != null) dataset.setDescription(description);
        repository.updateDataset(dataset);
        return dataset;
    }

    public RecallEvaluationDatasetEntity deleteDataset(String datasetId) {
        RecallEvaluationDatasetEntity dataset = getDataset(datasetId);
        dataset.setStatus("DELETING");
        repository.updateDataset(dataset);
        try {
            ingestionService.disableDataset(datasetId);
            dataset.setStatus("DELETED");
            dataset.setFailureCode(null);
            dataset.setFailureMessage(null);
        } catch (Exception error) {
            dataset.setStatus("ERROR");
            dataset.setFailureCode("DATASET_CLEANUP_FAILED");
            dataset.setFailureMessage(readable(error));
        }
        repository.updateDataset(dataset);
        return dataset;
    }

    public RecallCorpusImportResultVO importCorpus(String datasetId, List<RecallCorpusImportItemVO> items) {
        return ingestionService.importBatch(datasetId, items);
    }

    public List<RecallEvaluationCorpusItemEntity> listCorpus(String datasetId, String status, int limit, int offset) {
        getDataset(datasetId);
        return repository.listCorpusItems(datasetId, status, limit, offset);
    }

    public RecallEvaluationCorpusItemEntity disableCorpus(String datasetId, String corpusItemId) {
        requireCorpusDataset(datasetId, corpusItemId);
        return ingestionService.disableItem(corpusItemId);
    }

    public RecallEvaluationCorpusItemEntity reindexCorpus(String datasetId, String corpusItemId) {
        requireCorpusDataset(datasetId, corpusItemId);
        return ingestionService.reindexItem(corpusItemId);
    }

    public RecallCaseImportResultVO importCases(String datasetId, List<RecallCaseImportItemVO> items) {
        RecallEvaluationDatasetEntity dataset = getDataset(datasetId);
        List<RecallCaseImportItemVO> inputs = items == null ? List.of() : items;
        Set<String> existing = new HashSet<>(repository.listCases(datasetId, null, 10000, 0).stream()
                .map(RecallEvaluationCaseEntity::getExternalId).toList());
        List<RecallEvaluationCaseEntity> saved = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (RecallCaseImportItemVO input : inputs) {
            try {
                validateCase(input, existing);
                RecallEvaluationCaseEntity value = RecallEvaluationCaseEntity.builder()
                        .datasetId(datasetId)
                        .externalId(input.getExternalId())
                        .queryText(input.getQuery())
                        .sourceScope(input.getSourceScope() == null ? "MIXED" : input.getSourceScope().toUpperCase())
                        .expectedJson(JSON.toJSONString(input.getExpected() == null ? List.of() : input.getExpected()))
                        .tagsJson(JSON.toJSONString(input.getTags() == null ? List.of() : input.getTags()))
                        .status("ACTIVE")
                        .build();
                repository.saveCase(value);
                saved.add(value);
                existing.add(input.getExternalId());
            } catch (Exception error) {
                errors.add((input == null ? "unknown" : input.getExternalId()) + ": " + readable(error));
            }
        }
        dataset.setCaseCount(number(dataset.getCaseCount()) + saved.size());
        repository.updateDataset(dataset);
        return RecallCaseImportResultVO.builder().acceptedCount(saved.size()).failedCount(errors.size())
                .cases(saved).errors(errors).build();
    }

    public List<RecallEvaluationCaseEntity> listCases(String datasetId, String status, int limit, int offset) {
        getDataset(datasetId);
        return repository.listCases(datasetId, status, limit, offset);
    }

    public RecallEvaluationCaseEntity updateCase(String datasetId, RecallEvaluationCaseEntity update) {
        RecallEvaluationCaseEntity current = repository.findCase(update.getCaseId())
                .orElseThrow(() -> new IllegalArgumentException("Evaluation case does not exist: " + update.getCaseId()));
        if (!datasetId.equals(current.getDatasetId())) throw new IllegalArgumentException("Case does not belong to dataset.");
        if (update.getQueryText() != null) current.setQueryText(update.getQueryText());
        if (update.getSourceScope() != null) current.setSourceScope(update.getSourceScope());
        if (update.getExpectedJson() != null) current.setExpectedJson(update.getExpectedJson());
        if (update.getTagsJson() != null) current.setTagsJson(update.getTagsJson());
        if (update.getStatus() != null) current.setStatus(update.getStatus());
        repository.updateCase(current);
        return current;
    }

    public RecallEvaluationRunEntity startRun(RecallEvaluationRunConfigVO config) {
        normalizeRun(config);
        validateRun(config);
        getDataset(config.getDatasetId());
        RecallEvaluationRunEntity run = RecallEvaluationRunEntity.builder()
                .evaluationRunId("eval-run-" + UUID.randomUUID())
                .datasetId(config.getDatasetId())
                .name(config.getName())
                .status("PENDING")
                .configJson(JSON.toJSONString(config))
                .totalCaseCount(0).completedCaseCount(0).failedCaseCount(0).cancelRequested(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        repository.saveRun(run);
        try {
            executor.execute(() -> {
                try {
                    runner.execute(run.getEvaluationRunId());
                } catch (Exception error) {
                    RecallEvaluationRunEntity failed = repository.findRun(run.getEvaluationRunId()).orElse(run);
                    failed.setStatus("FAILED");
                    failed.setFailureCode("EVALUATION_RUN_FAILED");
                    failed.setFailureMessage(readable(error));
                    failed.setCompletedAt(LocalDateTime.now());
                    repository.updateRun(failed);
                }
            });
        } catch (RejectedExecutionException error) {
            run.setStatus("FAILED");
            run.setFailureCode("EVALUATION_EXECUTOR_REJECTED");
            run.setFailureMessage(readable(error));
            run.setCompletedAt(LocalDateTime.now());
            repository.updateRun(run);
        }
        return run;
    }

    public RecallEvaluationRunEntity cancelRun(String runId) {
        RecallEvaluationRunEntity run = requiredRun(runId);
        run.setCancelRequested(true);
        repository.updateRun(run);
        return run;
    }

    public List<RecallEvaluationRunEntity> listRuns(String datasetId, int limit) {
        getDataset(datasetId);
        return repository.listRuns(datasetId, limit);
    }

    public RecallEvaluationRunDetailVO getRun(String runId) {
        RecallEvaluationRunEntity run = requiredRun(runId);
        RecallEvaluationMetricsVO metrics = run.getMetricsJson() == null ? null
                : JSON.parseObject(run.getMetricsJson(), RecallEvaluationMetricsVO.class);
        return RecallEvaluationRunDetailVO.builder().run(run).metrics(metrics)
                .results(repository.listCaseResults(runId)).hits(repository.listHits(runId, null)).build();
    }

    public RecallEvaluationComparisonVO compare(String leftRunId, String rightRunId) {
        return comparisonService.compare(leftRunId, rightRunId);
    }

    private void validateCase(RecallCaseImportItemVO input, Set<String> existing) {
        if (input == null || input.getExternalId() == null || input.getExternalId().isBlank()
                || input.getQuery() == null || input.getQuery().isBlank()) {
            throw new IllegalArgumentException("externalId and query are required.");
        }
        if (existing.contains(input.getExternalId())) {
            throw new IllegalArgumentException("Duplicate case externalId.");
        }
        if (input.getExpected() == null || input.getExpected().isEmpty()) {
            throw new IllegalArgumentException("At least one expected label is required.");
        }
    }

    private void validateRun(RecallEvaluationRunConfigVO config) {
        if (config == null || config.getDatasetId() == null || config.getDatasetId().isBlank()) {
            throw new IllegalArgumentException("Run datasetId is required.");
        }
        if (config.getTopK() == null || config.getTopK() < 1 || config.getTopK() > 200) {
            throw new IllegalArgumentException("topK must be between 1 and 200.");
        }
        if (config.getMinScore() == null || config.getMinScore() < -1D || config.getMinScore() > 1D) {
            throw new IllegalArgumentException("minScore must be between -1 and 1.");
        }
    }

    private void normalizeRun(RecallEvaluationRunConfigVO config) {
        if (config == null) return;
        if (config.getName() == null || config.getName().isBlank()) config.setName("Recall evaluation");
        if (config.getSourceScope() == null || config.getSourceScope().isBlank()) config.setSourceScope("MIXED");
        if (config.getRetrievalMode() == null || config.getRetrievalMode().isBlank()) config.setRetrievalMode("HYBRID");
        if (config.getTopK() == null) config.setTopK(10);
        if (config.getMinScore() == null) config.setMinScore(0.2D);
        if (config.getPlannerEnabled() == null) config.setPlannerEnabled(false);
        if (config.getCaseLimit() == null || config.getCaseLimit() < 1) config.setCaseLimit(10000);
    }

    private void requireCorpusDataset(String datasetId, String corpusItemId) {
        RecallEvaluationCorpusItemEntity item = repository.findCorpusItem(corpusItemId)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation corpus item does not exist: " + corpusItemId));
        if (!datasetId.equals(item.getDatasetId())) throw new IllegalArgumentException("Corpus item does not belong to dataset.");
    }

    private RecallEvaluationRunEntity requiredRun(String runId) {
        return repository.findRun(runId)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation run does not exist: " + runId));
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }

    private String readable(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
