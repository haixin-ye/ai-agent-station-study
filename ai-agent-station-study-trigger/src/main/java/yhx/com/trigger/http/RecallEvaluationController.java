package yhx.com.trigger.http;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import yhx.com.api.dto.agent.evaluation.RecallEvaluationDTO;
import yhx.com.api.response.Response;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseEntity;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCaseImportResultVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCorpusImportItemVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCorpusImportResultVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCorpusBatchActionResultVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallRagAttachmentItemVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationRunDetailVO;
import yhx.com.domain.agent.service.evaluation.RecallEvaluationFacade;
import yhx.com.trigger.http.support.AgentResponseSupport;
import yhx.com.trigger.http.support.RecallEvaluationApiMapper;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@Profile("dev")
@CrossOrigin("*")
@RequestMapping("/api/v1/dev/recall-evaluations")
public class RecallEvaluationController {
    private final RecallEvaluationFacade facade;
    private final int maxBatchItems;

    public RecallEvaluationController(RecallEvaluationFacade facade,
                                      @Value("${auto-agent.recall-evaluation.max-batch-items:500}") int maxBatchItems) {
        this.facade = facade;
        this.maxBatchItems = maxBatchItems;
    }

    @GetMapping("/datasets")
    public Response<List<RecallEvaluationDTO.DatasetView>> listDatasets() {
        return call(() -> facade.listDatasets().stream().map(RecallEvaluationApiMapper::dataset).toList());
    }

    @PostMapping("/datasets")
    public Response<RecallEvaluationDTO.DatasetView> createDataset(@RequestBody RecallEvaluationDTO.DatasetRequest request) {
        return call(() -> RecallEvaluationApiMapper.dataset(facade.createDataset(request.getName(), request.getDescription())));
    }

    @GetMapping("/datasets/{datasetId}")
    public Response<RecallEvaluationDTO.DatasetView> getDataset(@PathVariable("datasetId") String datasetId) {
        return call(() -> RecallEvaluationApiMapper.dataset(facade.getDataset(datasetId)));
    }

    @PatchMapping("/datasets/{datasetId}")
    public Response<RecallEvaluationDTO.DatasetView> updateDataset(@PathVariable("datasetId") String datasetId,
                                                                   @RequestBody RecallEvaluationDTO.DatasetRequest request) {
        return call(() -> RecallEvaluationApiMapper.dataset(
                facade.updateDataset(datasetId, request.getName(), request.getDescription())));
    }

    @DeleteMapping("/datasets/{datasetId}")
    public Response<RecallEvaluationDTO.DatasetView> deleteDataset(@PathVariable("datasetId") String datasetId) {
        return call(() -> RecallEvaluationApiMapper.dataset(facade.deleteDataset(datasetId)));
    }

    @GetMapping("/datasets/{datasetId}/corpus")
    public Response<List<RecallEvaluationDTO.CorpusItemView>> listCorpus(
            @PathVariable("datasetId") String datasetId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "200") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return call(() -> facade.listCorpus(datasetId, status, bounded(limit), Math.max(0, offset)).stream()
                .map(RecallEvaluationApiMapper::corpus).toList());
    }

    @PostMapping("/datasets/{datasetId}/corpus/batch")
    public Response<RecallEvaluationDTO.ImportView<RecallEvaluationDTO.CorpusItemView>> importCorpus(
            @PathVariable("datasetId") String datasetId,
            @RequestBody RecallEvaluationDTO.CorpusBatchRequest request) {
        return call(() -> {
            requireBatch(request == null ? null : request.getItems());
            List<RecallCorpusImportItemVO> inputs = request.getItems().stream().map(this::corpusInput).toList();
            return corpusImportView(facade.importCorpus(datasetId, inputs));
        });
    }

    @PostMapping("/datasets/{datasetId}/corpus/rag/attachments")
    public Response<RecallEvaluationDTO.ImportView<RecallEvaluationDTO.CorpusItemView>> attachUploadedRagDocuments(
            @PathVariable("datasetId") String datasetId,
            @RequestBody RecallEvaluationDTO.RagAttachmentBatchRequest request) {
        return call(() -> {
            requireBatch(request == null ? null : request.getItems());
            List<RecallRagAttachmentItemVO> items = request.getItems().stream()
                    .map(value -> RecallRagAttachmentItemVO.builder()
                            .externalId(value.getExternalId())
                            .documentId(value.getDocumentId())
                            .title(value.getTitle())
                            .summary(value.getSummary())
                            .tags(value.getTags())
                            .build())
                    .toList();
            return corpusImportView(facade.attachUploadedRagDocuments(datasetId, items));
        });
    }

    @PostMapping("/datasets/{datasetId}/corpus/{corpusItemId}/reindex")
    public Response<RecallEvaluationDTO.CorpusItemView> reindexCorpus(@PathVariable("datasetId") String datasetId,
                                                                     @PathVariable("corpusItemId") String corpusItemId) {
        return call(() -> RecallEvaluationApiMapper.corpus(facade.reindexCorpus(datasetId, corpusItemId)));
    }

    @PostMapping("/datasets/{datasetId}/corpus/batch/reindex")
    public Response<RecallEvaluationDTO.ImportView<RecallEvaluationDTO.CorpusItemView>> reindexCorpusBatch(
            @PathVariable("datasetId") String datasetId,
            @RequestBody RecallEvaluationDTO.CorpusBatchActionRequest request) {
        return call(() -> corpusBatchActionView(facade.reindexCorpusBatch(datasetId,
                request == null ? null : request.getCorpusItemIds())));
    }

    @PostMapping("/datasets/{datasetId}/corpus/batch/disable")
    public Response<RecallEvaluationDTO.ImportView<RecallEvaluationDTO.CorpusItemView>> disableCorpusBatch(
            @PathVariable("datasetId") String datasetId,
            @RequestBody RecallEvaluationDTO.CorpusBatchActionRequest request) {
        return call(() -> corpusBatchActionView(facade.disableCorpusBatch(datasetId,
                request == null ? null : request.getCorpusItemIds())));
    }

    @GetMapping("/datasets/{datasetId}/vectors")
    public Response<List<RecallEvaluationDTO.VectorRecordView>> listVectorRecords(
            @PathVariable("datasetId") String datasetId,
            @RequestParam("itemType") String itemType,
            @RequestParam(name = "limit", defaultValue = "1000") int limit) {
        return call(() -> facade.listVectorRecords(datasetId, itemType, bounded(limit)).stream()
                .map(RecallEvaluationApiMapper::vectorRecord).toList());
    }

    @DeleteMapping("/datasets/{datasetId}/corpus/{corpusItemId}")
    public Response<RecallEvaluationDTO.CorpusItemView> disableCorpus(@PathVariable("datasetId") String datasetId,
                                                                     @PathVariable("corpusItemId") String corpusItemId) {
        return call(() -> RecallEvaluationApiMapper.corpus(facade.disableCorpus(datasetId, corpusItemId)));
    }

    @GetMapping("/datasets/{datasetId}/cases")
    public Response<List<RecallEvaluationDTO.CaseView>> listCases(
            @PathVariable("datasetId") String datasetId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "200") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return call(() -> facade.listCases(datasetId, status, bounded(limit), Math.max(0, offset)).stream()
                .map(RecallEvaluationApiMapper::testCase).toList());
    }

    @PostMapping("/datasets/{datasetId}/cases/batch")
    public Response<RecallEvaluationDTO.ImportView<RecallEvaluationDTO.CaseView>> importCases(
            @PathVariable("datasetId") String datasetId,
            @RequestBody RecallEvaluationDTO.CaseBatchRequest request) {
        return call(() -> {
            requireBatch(request == null ? null : request.getItems());
            RecallCaseImportResultVO result = facade.importCases(datasetId,
                    request.getItems().stream().map(RecallEvaluationApiMapper::caseInput).toList());
            return RecallEvaluationDTO.ImportView.<RecallEvaluationDTO.CaseView>builder()
                    .acceptedCount(result.getAcceptedCount()).failedCount(result.getFailedCount())
                    .items(result.getCases().stream().map(RecallEvaluationApiMapper::testCase).toList())
                    .errors(result.getErrors()).build();
        });
    }

    @PatchMapping("/datasets/{datasetId}/cases/{caseId}")
    public Response<RecallEvaluationDTO.CaseView> updateCase(
            @PathVariable("datasetId") String datasetId,
            @PathVariable("caseId") String caseId,
            @RequestBody RecallEvaluationDTO.CaseUpdateRequest request) {
        return call(() -> RecallEvaluationApiMapper.testCase(facade.updateCase(datasetId,
                RecallEvaluationCaseEntity.builder().caseId(caseId).queryText(request.getQuery())
                        .sourceScope(request.getSourceScope())
                        .expectedJson(request.getExpected() == null ? null
                                : JSON.toJSONString(RecallEvaluationApiMapper.expected(request.getExpected())))
                        .tagsJson(request.getTags() == null ? null : JSON.toJSONString(request.getTags()))
                        .status(request.getStatus()).build())));
    }

    @GetMapping("/runs")
    public Response<List<RecallEvaluationDTO.RunView>> listRuns(@RequestParam("datasetId") String datasetId,
                                                                @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return call(() -> facade.listRuns(datasetId, Math.min(200, Math.max(1, limit))).stream()
                .map(RecallEvaluationApiMapper::run).toList());
    }

    @PostMapping("/runs")
    public Response<RecallEvaluationDTO.RunView> startRun(@RequestBody RecallEvaluationDTO.RunRequest request) {
        return call(() -> RecallEvaluationApiMapper.run(facade.startRun(RecallEvaluationApiMapper.runConfig(request))));
    }

    @GetMapping("/runs/{runId}")
    public Response<RecallEvaluationDTO.RunDetailView> getRun(@PathVariable("runId") String runId) {
        return call(() -> runDetail(facade.getRun(runId)));
    }

    @PostMapping("/runs/{runId}/cancel")
    public Response<RecallEvaluationDTO.RunView> cancelRun(@PathVariable("runId") String runId) {
        return call(() -> RecallEvaluationApiMapper.run(facade.cancelRun(runId)));
    }

    @GetMapping("/compare")
    public Response<RecallEvaluationDTO.ComparisonView> compare(@RequestParam("leftRunId") String leftRunId,
                                                               @RequestParam("rightRunId") String rightRunId) {
        return call(() -> RecallEvaluationApiMapper.comparison(facade.compare(leftRunId, rightRunId)));
    }

    private RecallEvaluationDTO.RunDetailView runDetail(RecallEvaluationRunDetailVO value) {
        return RecallEvaluationDTO.RunDetailView.builder().run(RecallEvaluationApiMapper.run(value.getRun()))
                .metrics(RecallEvaluationApiMapper.metrics(value.getMetrics()))
                .results(value.getResults().stream().map(RecallEvaluationApiMapper::result).toList())
                .hits(value.getHits().stream().map(RecallEvaluationApiMapper::hit).toList()).build();
    }

    private RecallEvaluationDTO.ImportView<RecallEvaluationDTO.CorpusItemView> corpusImportView(
            RecallCorpusImportResultVO result) {
        List<String> errors = result.getItems().stream().filter(item -> item.getFailureMessage() != null)
                .map(item -> item.getExternalId() + ": " + item.getFailureMessage()).toList();
        return RecallEvaluationDTO.ImportView.<RecallEvaluationDTO.CorpusItemView>builder()
                .acceptedCount(result.getAcceptedCount()).failedCount(result.getFailedCount())
                .items(result.getItems().stream().map(RecallEvaluationApiMapper::corpus).toList()).errors(errors).build();
    }

    private RecallEvaluationDTO.ImportView<RecallEvaluationDTO.CorpusItemView> corpusBatchActionView(
            RecallCorpusBatchActionResultVO result) {
        return RecallEvaluationDTO.ImportView.<RecallEvaluationDTO.CorpusItemView>builder()
                .acceptedCount(result.getSucceededCount())
                .failedCount(result.getFailedCount())
                .items(result.getItems().stream().map(RecallEvaluationApiMapper::corpus).toList())
                .errors(result.getErrors())
                .build();
    }

    private RecallCorpusImportItemVO corpusInput(RecallEvaluationDTO.CorpusItemRequest value) {
        return RecallCorpusImportItemVO.builder().externalId(value.getExternalId()).type(value.getType())
                .title(value.getTitle()).summary(value.getSummary()).content(value.getContent())
                .score(value.getScore() == null ? BigDecimal.ONE : value.getScore()).tags(value.getTags()).build();
    }

    private int bounded(int limit) {
        return Math.min(1000, Math.max(1, limit));
    }

    private void requireBatch(List<?> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("At least one item is required.");
        if (values.size() > maxBatchItems) throw new IllegalArgumentException("Batch size exceeds " + maxBatchItems + " items.");
    }

    private <T> Response<T> call(CheckedSupplier<T> supplier) {
        try {
            return AgentResponseSupport.success(supplier.get());
        } catch (Exception error) {
            log.warn("[RecallEvaluation] request failed: {}", error.getMessage(), error);
            return AgentResponseSupport.failed(error.getMessage());
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
