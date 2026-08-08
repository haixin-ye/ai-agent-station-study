package yhx.com.test.domain.agent.evaluation;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IRecallEvaluationRepository;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseResultEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCorpusItemEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationDatasetEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationHitEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationRunEntity;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IVectorIndexRepository;
import yhx.com.domain.agent.adapter.repository.IRagAssetRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentVectorIndexEntity;
import yhx.com.domain.agent.model.entity.rag.RagChunkEntity;
import yhx.com.domain.agent.model.entity.rag.RagCodeFileEntity;
import yhx.com.domain.agent.model.entity.rag.RagCodeSymbolEntity;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.evaluation.DetailedRecallResultVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCorpusBatchActionResultVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallExecutionOptionsVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallRagAttachmentItemVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;
import yhx.com.domain.agent.service.memory.VectorContextRecallPreselector;
import yhx.com.domain.agent.service.memory.LongTermMemoryService;
import yhx.com.domain.agent.service.memory.MemoryVectorIndexingService;
import yhx.com.domain.agent.service.evaluation.RecallEvaluationRunner;
import yhx.com.domain.agent.service.evaluation.RecallEvaluationIngestionService;
import yhx.com.domain.agent.service.evaluation.RecallEvaluationFacade;
import yhx.com.domain.agent.service.evaluation.RecallMetricsCalculator;
import yhx.com.domain.agent.service.rag.RagVectorIndexingService;
import com.alibaba.fastjson.JSON;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RecallEvaluationFlowTest {

    @Test
    public void detailed_memory_recall_uses_run_parameters_and_exact_dataset_filter() {
        CapturingVectorRepository vectors = new CapturingVectorRepository();
        VectorContextRecallPreselector preselector = new VectorContextRecallPreselector(vectors, null, null, null, null);

        DetailedRecallResultVO result = preselector.recallDetailed(
                ContextPreparationCommand.builder()
                        .userId("eval-user:dataset-1")
                        .sessionId("eval-session:dataset-1")
                        .userInput("慢节奏旅行偏好")
                        .build(),
                RecallExecutionOptionsVO.builder()
                        .topK(17)
                        .minScore(0.61D)
                        .collectionTypes(List.of(VectorCollectionTypeEnumVO.LONG_TERM_MEMORY))
                        .metadataFilters(Map.of("evalDatasetId", "dataset-1"))
                        .build());

        Assert.assertEquals(1, vectors.queries.size());
        VectorRecallQueryVO query = vectors.queries.get(0);
        Assert.assertEquals(Integer.valueOf(17), query.getTopK());
        Assert.assertEquals(Double.valueOf(0.61D), query.getMinScore());
        Assert.assertEquals(List.of(VectorCollectionTypeEnumVO.LONG_TERM_MEMORY), query.getFilter().getCollectionTypes());
        Assert.assertEquals(Map.of("evalDatasetId", "dataset-1"), query.getFilter().getMetadataFilters());
        Assert.assertEquals(1, result.getVectorHits().size());
        Assert.assertNotNull(result.getCandidateBundle());
        Assert.assertTrue(result.getElapsedMs() >= 0L);
    }

    @Test
    public void external_memory_ingestion_persists_payload_source_and_dataset_vector_metadata() {
        FakePayloadRepository payloads = new FakePayloadRepository();
        FakeMemoryRepository memories = new FakeMemoryRepository();
        CapturingVectorRepository vectors = new CapturingVectorRepository();
        MemoryVectorIndexingService indexing = new MemoryVectorIndexingService(vectors, new FakeVectorIndexRepository(), payloads);
        LongTermMemoryService service = new LongTermMemoryService(payloads, memories, indexing);

        AgentMemoryEntity saved = service.ingestExternalMemory(
                AgentMemoryEntity.builder()
                        .memoryId("memory-eval-1")
                        .userId("eval-user:dataset-1")
                        .sessionId("eval-session:dataset-1")
                        .memoryType("LONG_TERM_MEMORY")
                        .summary("用户偏好慢节奏旅行")
                        .score(BigDecimal.ONE)
                        .build(),
                "用户每天最多安排两个主要景点，并保留午休时间。",
                Map.of("evalDatasetId", "dataset-1", "evalExternalId", "memory-travel-pace"));

        Assert.assertEquals("payload-1", saved.getContentRef());
        Assert.assertSame(saved, memories.saved);
        Assert.assertEquals("ACTIVE", saved.getStatus());
        Assert.assertNotNull(vectors.indexed);
        Assert.assertEquals("dataset-1", vectors.indexed.getMetadata().get("evalDatasetId"));
        Assert.assertEquals("memory-travel-pace", vectors.indexed.getMetadata().get("evalExternalId"));
    }

    @Test
    public void batch_disable_deduplicates_selected_items_and_reports_missing_items() {
        InMemoryEvaluationRepository evaluations = new InMemoryEvaluationRepository();
        evaluations.dataset = RecallEvaluationDatasetEntity.builder()
                .datasetId("dataset-1")
                .corpusCount(1)
                .readyCorpusCount(1)
                .build();
        evaluations.corpus = RecallEvaluationCorpusItemEntity.builder()
                .corpusItemId("corpus-1")
                .datasetId("dataset-1")
                .externalId("20001")
                .itemType("LONG_TERM_MEMORY")
                .sourceId("memory-1")
                .status("READY")
                .build();
        CapturingVectorRepository vectors = new CapturingVectorRepository();
        RecallEvaluationIngestionService ingestion = new RecallEvaluationIngestionService(
                evaluations, null, null, new FakeMemoryRepository(), null, vectors, null, null);
        RecallEvaluationFacade facade = new RecallEvaluationFacade(
                evaluations, ingestion, null, null, null, null);

        RecallCorpusBatchActionResultVO result = facade.disableCorpusBatch(
                "dataset-1", List.of("corpus-1", "corpus-1", "missing"));

        Assert.assertEquals(Integer.valueOf(1), result.getSucceededCount());
        Assert.assertEquals(Integer.valueOf(1), result.getFailedCount());
        Assert.assertEquals(List.of("memory-1"), vectors.disabledSourceIds);
        Assert.assertEquals("DISABLED", evaluations.corpus.getStatus());
        Assert.assertEquals(Integer.valueOf(1), evaluations.dataset.getCorpusCount());
        Assert.assertEquals(Integer.valueOf(0), evaluations.dataset.getReadyCorpusCount());
        Assert.assertTrue(result.getErrors().get(0).contains("missing"));
    }

    @Test
    public void uploaded_rag_document_is_attached_and_reindexed_with_evaluation_metadata() {
        InMemoryEvaluationRepository evaluations = new InMemoryEvaluationRepository();
        evaluations.dataset = RecallEvaluationDatasetEntity.builder()
                .datasetId("dataset-1")
                .corpusCount(0)
                .readyCorpusCount(0)
                .build();
        FakeRagAssetRepository ragAssets = new FakeRagAssetRepository();
        ragAssets.document = RagDocumentEntity.builder()
                .documentId("rag-doc-1")
                .sourceType("FILE")
                .sourceName("10001.md")
                .title("生产接口创建的文档")
                .summary("生产摘要")
                .contentRef("payload-document")
                .status("READY")
                .chunkCount(1)
                .build();
        ragAssets.chunk = RagChunkEntity.builder()
                .chunkId("rag-doc-1-chunk-1")
                .documentId("rag-doc-1")
                .chunkNo(1)
                .chunkType("FILE_CHUNK")
                .summary("生产分块摘要")
                .contentRef("payload-chunk")
                .retrievalTextRef("payload-chunk")
                .status("ACTIVE")
                .build();
        FakePayloadRepository payloads = new FakePayloadRepository();
        payloads.saved = AgentPayloadEntity.builder()
                .payloadId("payload-chunk")
                .content("生产接口生成的真实分块内容")
                .build();
        CapturingVectorRepository vectors = new CapturingVectorRepository();
        RagVectorIndexingService ragIndexing = new RagVectorIndexingService(
                vectors, new FakeVectorIndexRepository());
        RecallEvaluationIngestionService ingestion = new RecallEvaluationIngestionService(
                evaluations, ragAssets, null, null, payloads, vectors, null, ragIndexing);

        yhx.com.domain.agent.model.valobj.evaluation.RecallCorpusImportResultVO result =
                ingestion.attachUploadedRagDocuments("dataset-1", List.of(
                        RecallRagAttachmentItemVO.builder()
                                .externalId("10001")
                                .documentId("rag-doc-1")
                                .title("评测标题")
                                .build()));

        Assert.assertEquals(Integer.valueOf(1), result.getAcceptedCount());
        Assert.assertEquals("rag-doc-1", evaluations.corpus.getSourceId());
        Assert.assertEquals("READY", evaluations.corpus.getStatus());
        Assert.assertEquals("dataset-1", vectors.indexed.getMetadata().get("evalDatasetId"));
        Assert.assertEquals("10001", vectors.indexed.getMetadata().get("evalExternalId"));
        Assert.assertEquals(Integer.valueOf(1), evaluations.dataset.getReadyCorpusCount());
    }

    @Test
    public void batch_runner_skips_planner_when_disabled_and_isolates_case_failure() {
        InMemoryEvaluationRepository evaluations = new InMemoryEvaluationRepository();
        evaluations.dataset = RecallEvaluationDatasetEntity.builder()
                .datasetId("dataset-1").evalUserId("eval-user:dataset-1")
                .evalSessionId("eval-session:dataset-1").build();
        evaluations.corpus = RecallEvaluationCorpusItemEntity.builder()
                .corpusItemId("corpus-1").datasetId("dataset-1").externalId("memory-expected")
                .itemType("LONG_TERM_MEMORY").sourceId("memory-1").build();
        evaluations.cases.add(RecallEvaluationCaseEntity.builder().caseId("case-ok").datasetId("dataset-1")
                .queryText("slow travel").sourceScope("MEMORY").status("ACTIVE")
                .expectedJson("[{\"externalId\":\"memory-expected\",\"grade\":3}]").build());
        evaluations.cases.add(RecallEvaluationCaseEntity.builder().caseId("case-fail").datasetId("dataset-1")
                .queryText("explode retrieval").sourceScope("MEMORY").status("ACTIVE")
                .expectedJson("[{\"externalId\":\"memory-expected\",\"grade\":3}]").build());
        evaluations.run = RecallEvaluationRunEntity.builder().evaluationRunId("run-1").datasetId("dataset-1")
                .status("PENDING").configJson("{\"datasetId\":\"dataset-1\",\"sourceScope\":\"MEMORY\",\"topK\":3,\"minScore\":0.2,\"plannerEnabled\":false}")
                .completedCaseCount(0).failedCaseCount(0).build();

        FakeMemoryRepository memories = new FakeMemoryRepository();
        memories.saved = AgentMemoryEntity.builder().memoryId("memory-1").memoryType("LONG_TERM_MEMORY")
                .summary("slow travel preference").contentRef("payload-1").build();
        FakePayloadRepository payloads = new FakePayloadRepository();
        payloads.saved = AgentPayloadEntity.builder().payloadId("payload-1").content("slow travel preference").build();
        FailingByQueryVectorRepository vectors = new FailingByQueryVectorRepository();
        AtomicInteger plannerCalls = new AtomicInteger();
        RecallEvaluationRunner runner = new RecallEvaluationRunner(evaluations,
                new VectorContextRecallPreselector(vectors, null, null, memories, payloads), null,
                (candidates, config) -> {
                    plannerCalls.incrementAndGet();
                    return null;
                }, new RecallMetricsCalculator());

        runner.execute("run-1");

        Assert.assertEquals(0, plannerCalls.get());
        Assert.assertEquals("COMPLETED", evaluations.run.getStatus());
        Assert.assertEquals(Integer.valueOf(2), evaluations.run.getCompletedCaseCount());
        Assert.assertEquals(Integer.valueOf(1), evaluations.run.getFailedCaseCount());
        Assert.assertEquals(2, evaluations.results.size());
        Assert.assertEquals(1, evaluations.hits.size());
        Assert.assertNotNull(evaluations.run.getMetricsJson());
    }

    @Test
    public void planner_enabled_run_reports_raw_and_filtered_quality() {
        InMemoryEvaluationRepository evaluations = new InMemoryEvaluationRepository();
        evaluations.dataset = RecallEvaluationDatasetEntity.builder()
                .datasetId("dataset-1").evalUserId("eval-user:dataset-1")
                .evalSessionId("eval-session:dataset-1").build();
        evaluations.corpus = RecallEvaluationCorpusItemEntity.builder()
                .corpusItemId("corpus-1").datasetId("dataset-1").externalId("10001")
                .itemType("LONG_TERM_MEMORY").sourceId("memory-1").build();
        evaluations.cases.add(RecallEvaluationCaseEntity.builder().caseId("case-ok").datasetId("dataset-1")
                .queryText("slow travel").sourceScope("MEMORY").status("ACTIVE")
                .expectedJson("[{\"externalId\":\"10001\",\"grade\":3}]").build());
        evaluations.run = RecallEvaluationRunEntity.builder().evaluationRunId("run-planner").datasetId("dataset-1")
                .status("PENDING").configJson("{\"datasetId\":\"dataset-1\",\"sourceScope\":\"MEMORY\",\"topK\":3,\"minScore\":0.2,\"plannerEnabled\":true}")
                .completedCaseCount(0).failedCaseCount(0).build();

        FakeMemoryRepository memories = new FakeMemoryRepository();
        memories.saved = AgentMemoryEntity.builder().memoryId("memory-1").memoryType("LONG_TERM_MEMORY")
                .summary("slow travel preference").contentRef("payload-1").build();
        FakePayloadRepository payloads = new FakePayloadRepository();
        payloads.saved = AgentPayloadEntity.builder().payloadId("payload-1").content("slow travel preference").build();
        RecallEvaluationRunner runner = new RecallEvaluationRunner(evaluations,
                new VectorContextRecallPreselector(new FailingByQueryVectorRepository(), null, null, memories, payloads), null,
                (candidates, config) -> ContextPlannerOutputVO.builder().status("READY")
                        .reason("keep the matching memory")
                        .selectedContext(List.of(Map.of("sourceId", "memory-1"))).build(),
                new RecallMetricsCalculator());

        runner.execute("run-planner");

        yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationMetricsVO metrics = JSON.parseObject(
                evaluations.run.getMetricsJson(),
                yhx.com.domain.agent.model.valobj.evaluation.RecallEvaluationMetricsVO.class);
        Assert.assertEquals(Double.valueOf(1D), metrics.getHitRateAtK());
        Assert.assertEquals(Double.valueOf(1D), metrics.getPlannerHitRateAtK());
        Assert.assertEquals(Double.valueOf(1D), metrics.getPlannerPrecision());
        Assert.assertEquals(Double.valueOf(1D), metrics.getPlannerRecall());
        Assert.assertEquals(Double.valueOf(1D), metrics.getPlannerRelevantRetentionRate());
        Assert.assertEquals(Integer.valueOf(0), metrics.getPlannerRelevantDroppedCount());
        Assert.assertTrue(evaluations.hits.get(0).getSelectedByPlanner());
    }

    private static class CapturingVectorRepository implements IVectorMemoryRepository {
        private final List<VectorRecallQueryVO> queries = new ArrayList<>();
        private final List<String> disabledSourceIds = new ArrayList<>();
        private VectorIndexRecordVO indexed;

        @Override
        public String upsert(VectorIndexRecordVO record) {
            indexed = record;
            return "vector-1";
        }

        @Override
        public List<VectorRecallHitVO> search(VectorRecallQueryVO query) {
            queries.add(query);
            return List.of(VectorRecallHitVO.builder()
                    .collectionType(VectorCollectionTypeEnumVO.LONG_TERM_MEMORY)
                    .sourceType(VectorSourceTypeEnumVO.LONG_TERM_MEMORY)
                    .sourceId("memory-1")
                    .score(0.91D)
                    .build());
        }

        @Override
        public void disable(VectorCollectionTypeEnumVO collectionType, String sourceId) {
            disabledSourceIds.add(sourceId);
        }
    }

    private static class FakePayloadRepository implements IPayloadRepository {
        private AgentPayloadEntity saved;

        @Override
        public String savePayload(AgentPayloadEntity payload) {
            saved = payload;
            payload.setPayloadId("payload-1");
            return payload.getPayloadId();
        }

        @Override
        public Optional<AgentPayloadEntity> findPayload(String payloadId) {
            return Optional.ofNullable(saved);
        }
    }

    private static class FakeMemoryRepository implements IMemoryRepository {
        private AgentMemoryEntity saved;

        @Override
        public List<AgentMemoryEntity> findMemoryCandidates(String userId, String sessionId, String query, int limit) {
            return List.of();
        }

        @Override
        public Optional<AgentMemoryEntity> findMemory(String memoryId) {
            return Optional.ofNullable(saved);
        }

        @Override
        public String saveConversationSummary(AgentConversationSummaryEntity summary) {
            return summary.getSummaryId();
        }

        @Override
        public String saveLongTermMemory(AgentMemoryEntity memory) {
            saved = memory;
            return memory.getMemoryId();
        }

        @Override
        public String recordMemoryEvent(AgentMemoryEventEntity event) {
            return event.getEventId();
        }
    }

    private static class FakeVectorIndexRepository implements IVectorIndexRepository {
        @Override
        public String saveOrUpdate(AgentVectorIndexEntity index) {
            return index.getVectorId();
        }

        @Override
        public Optional<AgentVectorIndexEntity> findBySource(String collectionType, String sourceType, String sourceId) {
            return Optional.empty();
        }

        @Override
        public void markDisabled(String collectionType, String sourceType, String sourceId) {
        }
    }

    private static class FailingByQueryVectorRepository implements IVectorMemoryRepository {
        @Override
        public String upsert(VectorIndexRecordVO record) {
            return "vector-1";
        }

        @Override
        public List<VectorRecallHitVO> search(VectorRecallQueryVO query) {
            if (query.getQueryText().contains("explode")) {
                throw new IllegalStateException("synthetic retrieval failure");
            }
            return List.of(VectorRecallHitVO.builder().collectionType(VectorCollectionTypeEnumVO.LONG_TERM_MEMORY)
                    .sourceType(VectorSourceTypeEnumVO.LONG_TERM_MEMORY).sourceId("memory-1").score(0.9D).build());
        }

        @Override
        public void disable(VectorCollectionTypeEnumVO collectionType, String sourceId) {
        }
    }

    private static class FakeRagAssetRepository implements IRagAssetRepository {
        private RagDocumentEntity document;
        private RagChunkEntity chunk;

        @Override public void saveDocument(RagDocumentEntity value) { document = value; }
        @Override public void updateDocument(RagDocumentEntity value) { document = value; }
        @Override public Optional<RagDocumentEntity> findDocument(String documentId) {
            return document != null && documentId.equals(document.getDocumentId()) ? Optional.of(document) : Optional.empty();
        }
        @Override public List<RagDocumentEntity> findDocumentsByIds(List<String> documentIds) {
            return document != null && documentIds.contains(document.getDocumentId()) ? List.of(document) : List.of();
        }
        @Override public void saveChunk(RagChunkEntity value) { chunk = value; }
        @Override public void updateChunkStatus(String chunkId, String status) { if (chunk != null) chunk.setStatus(status); }
        @Override public List<RagChunkEntity> findChunksByDocumentId(String documentId) {
            return chunk != null && documentId.equals(chunk.getDocumentId()) ? List.of(chunk) : List.of();
        }
        @Override public Optional<RagChunkEntity> findChunk(String chunkId) {
            return chunk != null && chunkId.equals(chunk.getChunkId()) ? Optional.of(chunk) : Optional.empty();
        }
        @Override public List<RagChunkEntity> findChunksByIds(List<String> chunkIds) {
            return chunk != null && chunkIds.contains(chunk.getChunkId()) ? List.of(chunk) : List.of();
        }
        @Override public void saveCodeFile(RagCodeFileEntity codeFile) { }
        @Override public List<RagCodeFileEntity> findCodeFilesByDocumentId(String documentId) { return List.of(); }
        @Override public void saveCodeSymbol(RagCodeSymbolEntity symbol) { }
        @Override public List<RagCodeSymbolEntity> findCodeSymbolsByDocumentId(String documentId) { return List.of(); }
    }

    private static class InMemoryEvaluationRepository implements IRecallEvaluationRepository {
        private RecallEvaluationDatasetEntity dataset;
        private RecallEvaluationCorpusItemEntity corpus;
        private RecallEvaluationRunEntity run;
        private final List<RecallEvaluationCaseEntity> cases = new ArrayList<>();
        private final List<RecallEvaluationCaseResultEntity> results = new ArrayList<>();
        private final List<RecallEvaluationHitEntity> hits = new ArrayList<>();

        @Override public void saveDataset(RecallEvaluationDatasetEntity value) { dataset = value; }
        @Override public Optional<RecallEvaluationDatasetEntity> findDataset(String datasetId) { return Optional.ofNullable(dataset); }
        @Override public List<RecallEvaluationDatasetEntity> listDatasets() { return List.of(dataset); }
        @Override public void updateDataset(RecallEvaluationDatasetEntity value) { dataset = value; }
        @Override public void saveCorpusItem(RecallEvaluationCorpusItemEntity item) {
            if (item.getCorpusItemId() == null) item.setCorpusItemId("corpus-1");
            corpus = item;
        }
        @Override public Optional<RecallEvaluationCorpusItemEntity> findCorpusItem(String corpusItemId) {
            return corpus != null && corpusItemId.equals(corpus.getCorpusItemId()) ? Optional.of(corpus) : Optional.empty();
        }
        @Override public Optional<RecallEvaluationCorpusItemEntity> findCorpusItemByExternalId(String datasetId, String externalId) {
            return corpus != null && externalId.equals(corpus.getExternalId()) ? Optional.of(corpus) : Optional.empty();
        }
        @Override public List<RecallEvaluationCorpusItemEntity> listCorpusItems(String datasetId, String status, int limit, int offset) { return corpus == null ? List.of() : List.of(corpus); }
        @Override public void updateCorpusItem(RecallEvaluationCorpusItemEntity item) { corpus = item; }
        @Override public void saveCase(RecallEvaluationCaseEntity testCase) { cases.add(testCase); }
        @Override public Optional<RecallEvaluationCaseEntity> findCase(String caseId) { return cases.stream().filter(value -> caseId.equals(value.getCaseId())).findFirst(); }
        @Override public List<RecallEvaluationCaseEntity> listCases(String datasetId, String status, int limit, int offset) { return new ArrayList<>(cases); }
        @Override public void updateCase(RecallEvaluationCaseEntity testCase) { }
        @Override public void saveRun(RecallEvaluationRunEntity value) { run = value; }
        @Override public Optional<RecallEvaluationRunEntity> findRun(String evaluationRunId) { return Optional.ofNullable(run); }
        @Override public List<RecallEvaluationRunEntity> listRuns(String datasetId, int limit) { return List.of(run); }
        @Override public void updateRun(RecallEvaluationRunEntity value) { run = value; }
        @Override public void saveCaseResult(RecallEvaluationCaseResultEntity result) { results.add(result); }
        @Override public List<RecallEvaluationCaseResultEntity> listCaseResults(String evaluationRunId) { return new ArrayList<>(results); }
        @Override public void saveHits(List<RecallEvaluationHitEntity> values) { hits.addAll(values); }
        @Override public List<RecallEvaluationHitEntity> listHits(String evaluationRunId, String caseId) { return new ArrayList<>(hits); }
    }
}
