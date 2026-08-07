package yhx.com.domain.agent.service.evaluation;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IRecallEvaluationRepository;
import yhx.com.domain.agent.adapter.repository.IRagAssetRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCorpusItemEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationDatasetEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;
import yhx.com.domain.agent.model.entity.rag.RagFileIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagFilePayloadEntity;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCorpusImportItemVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCorpusImportResultVO;
import yhx.com.domain.agent.service.memory.LongTermMemoryService;
import yhx.com.domain.agent.service.memory.MemoryVectorIndexingService;
import yhx.com.domain.agent.service.rag.RagAssetIngestionService;
import yhx.com.domain.agent.service.rag.RagVectorIndexingService;
import yhx.com.domain.agent.model.entity.rag.RagChunkEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecallEvaluationIngestionService {

    private final IRecallEvaluationRepository evaluationRepository;
    private final RagAssetIngestionService ragAssetIngestionService;
    private final IRagAssetRepository ragAssetRepository;
    private final LongTermMemoryService longTermMemoryService;
    private final IMemoryRepository memoryRepository;
    private final IPayloadRepository payloadRepository;
    private final IVectorMemoryRepository vectorMemoryRepository;
    private final MemoryVectorIndexingService memoryVectorIndexingService;
    private final RagVectorIndexingService ragVectorIndexingService;

    public RecallEvaluationIngestionService(IRecallEvaluationRepository evaluationRepository,
                                             RagAssetIngestionService ragAssetIngestionService,
                                             IRagAssetRepository ragAssetRepository,
                                             LongTermMemoryService longTermMemoryService,
                                             IMemoryRepository memoryRepository,
                                             IPayloadRepository payloadRepository,
                                             IVectorMemoryRepository vectorMemoryRepository,
                                             MemoryVectorIndexingService memoryVectorIndexingService,
                                             RagVectorIndexingService ragVectorIndexingService) {
        this.evaluationRepository = evaluationRepository;
        this.ragAssetIngestionService = ragAssetIngestionService;
        this.ragAssetRepository = ragAssetRepository;
        this.longTermMemoryService = longTermMemoryService;
        this.memoryRepository = memoryRepository;
        this.payloadRepository = payloadRepository;
        this.vectorMemoryRepository = vectorMemoryRepository;
        this.memoryVectorIndexingService = memoryVectorIndexingService;
        this.ragVectorIndexingService = ragVectorIndexingService;
    }

    public void disableDataset(String datasetId) {
        for (RecallEvaluationCorpusItemEntity item : evaluationRepository.listCorpusItems(datasetId, null, 10000, 0)) {
            disableItem(item.getCorpusItemId());
        }
    }

    public RecallEvaluationCorpusItemEntity disableItem(String corpusItemId) {
        RecallEvaluationCorpusItemEntity item = evaluationRepository.findCorpusItem(corpusItemId)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation corpus item does not exist: " + corpusItemId));
        if (isMemory(item)) {
            if (!isBlank(item.getSourceId())) {
                VectorCollectionTypeEnumVO collection = "USER_PREFERENCE".equals(item.getItemType())
                        ? VectorCollectionTypeEnumVO.USER_PREFERENCE : VectorCollectionTypeEnumVO.LONG_TERM_MEMORY;
                vectorMemoryRepository.disable(collection, item.getSourceId());
                memoryRepository.updateMemoryLifecycle(item.getSourceId(), "DISABLED", null);
            }
        } else {
            if (!isBlank(item.getSourceId())) {
                ragAssetRepository.findDocument(item.getSourceId()).ifPresent(document -> {
                    document.setStatus("DELETED");
                    ragAssetRepository.updateDocument(document);
                });
            }
            for (String chunkId : sourceRefs(item)) {
                ragAssetRepository.findChunk(chunkId).ifPresent(chunk -> {
                    vectorMemoryRepository.disable(ragCollection(chunk), chunkId);
                    ragAssetRepository.updateChunkStatus(chunkId, "DELETED");
                });
            }
        }
        item.setStatus("DISABLED");
        evaluationRepository.updateCorpusItem(item);
        return item;
    }

    public RecallEvaluationCorpusItemEntity reindexItem(String corpusItemId) {
        RecallEvaluationCorpusItemEntity item = evaluationRepository.findCorpusItem(corpusItemId)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation corpus item does not exist: " + corpusItemId));
        RecallEvaluationDatasetEntity dataset = evaluationRepository.findDataset(item.getDatasetId())
                .orElseThrow(() -> new IllegalArgumentException("Evaluation dataset does not exist: " + item.getDatasetId()));
        Map<String, Object> metadata = Map.of("evalDatasetId", dataset.getDatasetId(),
                "evalExternalId", item.getExternalId(), "evalCorpusItemId", item.getCorpusItemId());
        if (isMemory(item)) {
            if (isBlank(item.getSourceId())) {
                throw new IllegalStateException("Memory corpus item has no persisted source to reindex.");
            }
            AgentMemoryEntity memory = memoryRepository.findMemory(item.getSourceId())
                    .orElseThrow(() -> new IllegalStateException("Memory source no longer exists: " + item.getSourceId()));
            memoryVectorIndexingService.indexMemory(memory, metadata);
        } else {
            int indexed = 0;
            for (String chunkId : sourceRefs(item)) {
                RagChunkEntity chunk = ragAssetRepository.findChunk(chunkId).orElse(null);
                if (chunk == null) continue;
                String text = payloadRepository.findContent(firstNonBlank(chunk.getRetrievalTextRef(), chunk.getContentRef())).orElse(null);
                if (isBlank(text)) continue;
                ragVectorIndexingService.indexChunk(chunk, text, metadata);
                ragAssetRepository.updateChunkStatus(chunkId, "ACTIVE");
                indexed++;
            }
            if (indexed == 0) {
                throw new IllegalStateException("RAG corpus item has no chunks to reindex.");
            }
            ragAssetRepository.findDocument(item.getSourceId()).ifPresent(document -> {
                document.setStatus("READY");
                ragAssetRepository.updateDocument(document);
            });
        }
        item.setStatus("READY");
        clearFailure(item);
        evaluationRepository.updateCorpusItem(item);
        return item;
    }

    public RecallCorpusImportResultVO importBatch(String datasetId, List<RecallCorpusImportItemVO> imports) {
        RecallEvaluationDatasetEntity dataset = evaluationRepository.findDataset(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation dataset does not exist: " + datasetId));
        if (imports == null || imports.isEmpty()) {
            return RecallCorpusImportResultVO.builder().acceptedCount(0).failedCount(0).items(List.of()).build();
        }
        List<RecallEvaluationCorpusItemEntity> results = new ArrayList<>();
        int accepted = 0;
        int failed = 0;
        int persisted = 0;
        for (RecallCorpusImportItemVO input : imports) {
            RecallEvaluationCorpusItemEntity item = createPending(dataset, input);
            try {
                validate(datasetId, input);
                evaluationRepository.saveCorpusItem(item);
                persisted++;
                ingest(dataset, input, item);
                item.setStatus("READY");
                clearFailure(item);
                evaluationRepository.updateCorpusItem(item);
                accepted++;
            } catch (Exception error) {
                item.setStatus("FAILED");
                item.setFailureStage("INGESTION");
                item.setFailureCode(error instanceof IllegalArgumentException ? "INVALID_CORPUS_ITEM" : "CORPUS_INGESTION_FAILED");
                item.setFailureMessage(readable(error));
                if (item.getCorpusItemId() != null) {
                    evaluationRepository.updateCorpusItem(item);
                }
                failed++;
            }
            results.add(item);
        }
        dataset.setCorpusCount(number(dataset.getCorpusCount()) + persisted);
        dataset.setReadyCorpusCount(number(dataset.getReadyCorpusCount()) + accepted);
        dataset.setStatus(failed > 0 ? "ERROR" : "ACTIVE");
        evaluationRepository.updateDataset(dataset);
        return RecallCorpusImportResultVO.builder().acceptedCount(accepted).failedCount(failed).items(results).build();
    }

    private RecallEvaluationCorpusItemEntity createPending(RecallEvaluationDatasetEntity dataset,
                                                            RecallCorpusImportItemVO input) {
        return RecallEvaluationCorpusItemEntity.builder()
                .datasetId(dataset.getDatasetId())
                .externalId(input == null ? null : input.getExternalId())
                .itemType(input == null ? null : normalizedType(input.getType()))
                .title(input == null ? null : input.getTitle())
                .summary(input == null ? null : input.getSummary())
                .tagsJson(JSON.toJSONString(input == null || input.getTags() == null ? List.of() : input.getTags()))
                .status("PENDING")
                .build();
    }

    private void ingest(RecallEvaluationDatasetEntity dataset,
                        RecallCorpusImportItemVO input,
                        RecallEvaluationCorpusItemEntity item) {
        Map<String, Object> metadata = metadata(dataset, input, item);
        if ("RAG_DOCUMENT".equals(item.getItemType())) {
            ingestRag(dataset, input, item, metadata);
            return;
        }
        AgentMemoryEntity memory = longTermMemoryService.ingestExternalMemory(AgentMemoryEntity.builder()
                        .userId(dataset.getEvalUserId())
                        .sessionId(dataset.getEvalSessionId())
                        .memoryType(item.getItemType())
                        .summary(firstNonBlank(input.getSummary(), input.getTitle()))
                        .score(input.getScore() == null ? BigDecimal.ONE : input.getScore())
                        .build(),
                input.getContent(), metadata);
        item.setSourceType(item.getItemType());
        item.setSourceId(memory.getMemoryId());
        item.setSourceRefsJson(JSON.toJSONString(List.of(memory.getMemoryId())));
        item.setContentRef(memory.getContentRef());
    }

    private void ingestRag(RecallEvaluationDatasetEntity dataset,
                           RecallCorpusImportItemVO input,
                           RecallEvaluationCorpusItemEntity item,
                           Map<String, Object> metadata) {
        List<RagDocumentEntity> documents = ragAssetIngestionService.ingestFiles(RagFileIngestCommandEntity.builder()
                .userId(dataset.getEvalUserId())
                .sessionId(dataset.getEvalSessionId())
                .knowledgeTag(dataset.getDatasetId())
                .indexingMetadata(metadata)
                .files(List.of(RagFilePayloadEntity.builder()
                        .fileName(firstNonBlank(input.getTitle(), input.getExternalId()) + ".md")
                        .content(input.getContent().getBytes(StandardCharsets.UTF_8))
                        .build()))
                .build());
        if (documents.isEmpty()) {
            throw new IllegalStateException("RAG ingestion did not create a document.");
        }
        RagDocumentEntity document = documents.get(0);
        List<String> chunkIds = ragAssetRepository.findChunksByDocumentId(document.getDocumentId()).stream()
                .map(chunk -> chunk.getChunkId()).toList();
        item.setTitle(firstNonBlank(input.getTitle(), document.getTitle()));
        item.setSummary(firstNonBlank(input.getSummary(), document.getSummary()));
        item.setContentRef(document.getContentRef());
        item.setSourceType("RAG_DOCUMENT");
        item.setSourceId(document.getDocumentId());
        item.setParentSourceId(document.getDocumentId());
        item.setSourceRefsJson(JSON.toJSONString(chunkIds));
    }

    private void validate(String datasetId, RecallCorpusImportItemVO input) {
        if (input == null || isBlank(input.getExternalId()) || isBlank(input.getContent())) {
            throw new IllegalArgumentException("externalId and content are required.");
        }
        String type = normalizedType(input.getType());
        if (!List.of("RAG_DOCUMENT", "LONG_TERM_MEMORY", "USER_PREFERENCE").contains(type)) {
            throw new IllegalArgumentException("Unsupported corpus type: " + input.getType());
        }
        if (evaluationRepository.findCorpusItemByExternalId(datasetId, input.getExternalId()).isPresent()) {
            throw new IllegalArgumentException("Duplicate corpus externalId: " + input.getExternalId());
        }
    }

    private Map<String, Object> metadata(RecallEvaluationDatasetEntity dataset,
                                         RecallCorpusImportItemVO input,
                                         RecallEvaluationCorpusItemEntity item) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("evalDatasetId", dataset.getDatasetId());
        metadata.put("evalExternalId", input.getExternalId());
        metadata.put("evalCorpusItemId", item.getCorpusItemId());
        return metadata;
    }

    private String normalizedType(String type) {
        return type == null ? null : type.trim().toUpperCase();
    }

    private void clearFailure(RecallEvaluationCorpusItemEntity item) {
        item.setFailureStage(null);
        item.setFailureCode(null);
        item.setFailureMessage(null);
    }

    private String readable(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isMemory(RecallEvaluationCorpusItemEntity item) {
        return item != null && ("LONG_TERM_MEMORY".equals(item.getItemType()) || "USER_PREFERENCE".equals(item.getItemType()));
    }

    private List<String> sourceRefs(RecallEvaluationCorpusItemEntity item) {
        if (item == null || isBlank(item.getSourceRefsJson())) {
            return List.of();
        }
        try {
            List<String> values = JSON.parseArray(item.getSourceRefsJson(), String.class);
            return values == null ? List.of() : values;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private VectorCollectionTypeEnumVO ragCollection(RagChunkEntity chunk) {
        if (chunk != null && "CODE_CHUNK".equals(chunk.getChunkType())) {
            return VectorCollectionTypeEnumVO.RAG_CODE_CHUNK;
        }
        if (chunk != null && "FILE_CHUNK".equals(chunk.getChunkType())) {
            return VectorCollectionTypeEnumVO.RAG_FILE_CHUNK;
        }
        return VectorCollectionTypeEnumVO.RAG_CHUNK;
    }
}
