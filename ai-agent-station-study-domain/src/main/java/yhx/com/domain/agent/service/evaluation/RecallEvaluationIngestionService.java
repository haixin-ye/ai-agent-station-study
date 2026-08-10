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
import yhx.com.domain.agent.model.valobj.evaluation.RecallCorpusBatchActionResultVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallRagAttachmentItemVO;
import yhx.com.domain.agent.service.memory.LongTermMemoryService;
import yhx.com.domain.agent.service.memory.MemoryVectorIndexingService;
import yhx.com.domain.agent.service.rag.IRagDomainService;
import yhx.com.domain.agent.service.rag.RagVectorIndexingService;
import yhx.com.domain.agent.model.entity.rag.RagChunkEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class RecallEvaluationIngestionService {

    private final IRecallEvaluationRepository evaluationRepository;
    private final IRagAssetRepository ragAssetRepository;
    private final LongTermMemoryService longTermMemoryService;
    private final IMemoryRepository memoryRepository;
    private final IPayloadRepository payloadRepository;
    private final IVectorMemoryRepository vectorMemoryRepository;
    private final MemoryVectorIndexingService memoryVectorIndexingService;
    private final RagVectorIndexingService ragVectorIndexingService;
    private final IRagDomainService ragDomainService;

    public RecallEvaluationIngestionService(IRecallEvaluationRepository evaluationRepository,
                                             IRagAssetRepository ragAssetRepository,
                                             LongTermMemoryService longTermMemoryService,
                                             IMemoryRepository memoryRepository,
                                             IPayloadRepository payloadRepository,
                                              IVectorMemoryRepository vectorMemoryRepository,
                                              MemoryVectorIndexingService memoryVectorIndexingService,
                                              RagVectorIndexingService ragVectorIndexingService,
                                              IRagDomainService ragDomainService) {
        this.evaluationRepository = evaluationRepository;
        this.ragAssetRepository = ragAssetRepository;
        this.longTermMemoryService = longTermMemoryService;
        this.memoryRepository = memoryRepository;
        this.payloadRepository = payloadRepository;
        this.vectorMemoryRepository = vectorMemoryRepository;
        this.memoryVectorIndexingService = memoryVectorIndexingService;
        this.ragVectorIndexingService = ragVectorIndexingService;
        this.ragDomainService = ragDomainService;
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
            String documentId = firstNonBlank(item.getParentSourceId(), item.getSourceId());
            if (!isBlank(documentId)) {
                ragAssetRepository.findDocument(documentId).ifPresent(document -> {
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
            List<String> chunkIds = sourceRefs(item);
            if (chunkIds.isEmpty() && "RAG_CHUNK".equals(item.getItemType())) {
                chunkIds = recoverRagChunkBinding(item, dataset);
            }
            int indexed = 0;
            for (String chunkId : chunkIds) {
                RagChunkEntity chunk = ragAssetRepository.findChunk(chunkId).orElse(null);
                if (chunk == null) continue;
                String text = payloadRepository.findContent(
                        firstNonBlank(chunk.getRetrievalTextRef(), chunk.getContentRef())).orElse(null);
                if (isBlank(text)) continue;
                ragVectorIndexingService.indexChunk(chunk, text, metadata);
                ragAssetRepository.updateChunkStatus(chunkId, "ACTIVE");
                indexed++;
            }
            if (indexed == 0) {
                throw new IllegalStateException("RAG corpus item has no chunks to reindex.");
            }
            String documentId = firstNonBlank(item.getParentSourceId(), item.getSourceId());
            if (!isBlank(documentId)) {
                ragAssetRepository.findDocument(documentId).ifPresent(document -> {
                    document.setStatus("READY");
                    ragAssetRepository.updateDocument(document);
                });
            }
        }
        item.setStatus("READY");
        clearFailure(item);
        evaluationRepository.updateCorpusItem(item);
        return item;
    }

    public RecallCorpusBatchActionResultVO reindexBatch(String datasetId, List<String> corpusItemIds) {
        return batchAction(datasetId, corpusItemIds, true);
    }

    public RecallCorpusBatchActionResultVO disableBatch(String datasetId, List<String> corpusItemIds) {
        return batchAction(datasetId, corpusItemIds, false);
    }

    private RecallCorpusBatchActionResultVO batchAction(String datasetId,
                                                         List<String> corpusItemIds,
                                                         boolean reindex) {
        if (isBlank(datasetId)) {
            throw new IllegalArgumentException("Evaluation datasetId is required.");
        }
        List<String> ids = corpusItemIds == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(corpusItemIds.stream()
                .filter(id -> !isBlank(id)).toList()));
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("At least one corpus item is required.");
        }
        if (ids.size() > 500) {
            throw new IllegalArgumentException("Corpus batch action cannot exceed 500 items.");
        }
        List<RecallEvaluationCorpusItemEntity> succeeded = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String corpusItemId : ids) {
            try {
                RecallEvaluationCorpusItemEntity current = evaluationRepository.findCorpusItem(corpusItemId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Evaluation corpus item does not exist: " + corpusItemId));
                if (!datasetId.equals(current.getDatasetId())) {
                    throw new IllegalArgumentException("Corpus item does not belong to dataset: " + corpusItemId);
                }
                succeeded.add(reindex ? reindexItem(corpusItemId) : disableItem(corpusItemId));
            } catch (Exception error) {
                errors.add(corpusItemId + ": " + readable(error));
            }
        }
        return RecallCorpusBatchActionResultVO.builder()
                .succeededCount(succeeded.size())
                .failedCount(errors.size())
                .items(succeeded)
                .errors(errors)
                .build();
    }

    public RecallCorpusImportResultVO importBatch(String datasetId, List<RecallCorpusImportItemVO> imports) {
        RecallEvaluationDatasetEntity dataset = evaluationRepository.findDataset(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation dataset does not exist: " + datasetId));
        if (imports == null || imports.isEmpty()) {
            return RecallCorpusImportResultVO.builder()
                    .acceptedCount(0).skippedCount(0).failedCount(0).items(List.of()).build();
        }
        List<RecallEvaluationCorpusItemEntity> results = new ArrayList<>();
        int accepted = 0;
        int skipped = 0;
        int failed = 0;
        int persisted = 0;
        for (RecallCorpusImportItemVO input : imports) {
            RecallEvaluationCorpusItemEntity item = createPending(dataset, input);
            try {
                validate(datasetId, input);
                if (evaluationRepository.findCorpusItemByExternalId(datasetId, input.getExternalId()).isPresent()) {
                    skipped++;
                    continue;
                }
                item.setExternalId(input.getExternalId());
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
        return RecallCorpusImportResultVO.builder()
                .acceptedCount(accepted).skippedCount(skipped).failedCount(failed).items(results).build();
    }

    public RecallCorpusImportResultVO attachUploadedRagDocuments(
            String datasetId,
            List<RecallRagAttachmentItemVO> attachments) {
        RecallEvaluationDatasetEntity dataset = evaluationRepository.findDataset(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation dataset does not exist: " + datasetId));
        if (attachments == null || attachments.isEmpty()) {
            return RecallCorpusImportResultVO.builder()
                    .acceptedCount(0).failedCount(0).items(List.of()).build();
        }
        List<RecallEvaluationCorpusItemEntity> results = new ArrayList<>();
        int accepted = 0;
        int failed = 0;
        for (RecallRagAttachmentItemVO input : attachments) {
            RecallEvaluationCorpusItemEntity item = createPendingAttachment(datasetId, input);
            try {
                input.setExternalId(RecallEvaluationIdPolicy.requireNumericId(
                        input.getExternalId(), "Corpus externalId"));
                RecallEvaluationCorpusItemEntity existing = evaluationRepository
                        .findCorpusItemByExternalId(datasetId, input.getExternalId()).orElse(null);
                if (existing != null && "READY".equals(existing.getStatus())) {
                    throw new IllegalArgumentException("Duplicate corpus externalId: " + input.getExternalId());
                }
                RagDocumentEntity document = validateAttachment(input);
                List<RagChunkEntity> chunks = ragAssetRepository.findChunksByDocumentId(document.getDocumentId());
                if (chunks == null || chunks.isEmpty()) {
                    throw new IllegalStateException("Uploaded RAG document has no chunks: " + document.getDocumentId());
                }
                if (existing != null) {
                    item = existing;
                    prepareReusableAttachment(item, input);
                    evaluationRepository.updateCorpusItem(item);
                } else {
                    item.setExternalId(input.getExternalId());
                    evaluationRepository.saveCorpusItem(item);
                }
                item.setExternalId(input.getExternalId());
                item.setTitle(firstNonBlank(input.getTitle(), document.getTitle()));
                item.setSummary(firstNonBlank(input.getSummary(), document.getSummary()));
                item.setContentRef(document.getContentRef());
                item.setSourceType("RAG_DOCUMENT");
                item.setSourceId(document.getDocumentId());
                item.setParentSourceId(document.getDocumentId());
                item.setSourceRefsJson(JSON.toJSONString(chunks.stream().map(RagChunkEntity::getChunkId).toList()));
                evaluationRepository.updateCorpusItem(item);
                attachEvaluationMetadata(dataset, item, chunks);
                item.setStatus("READY");
                clearFailure(item);
                evaluationRepository.updateCorpusItem(item);
                accepted++;
            } catch (Exception error) {
                item.setStatus("FAILED");
                item.setFailureStage("ATTACHMENT");
                item.setFailureCode(error instanceof IllegalArgumentException
                        ? "INVALID_RAG_ATTACHMENT" : "RAG_ATTACHMENT_FAILED");
                item.setFailureMessage(readable(error));
                if (item.getCorpusItemId() != null) {
                    evaluationRepository.updateCorpusItem(item);
                }
                failed++;
            }
            results.add(item);
        }
        List<RecallEvaluationCorpusItemEntity> current = evaluationRepository
                .listCorpusItems(datasetId, null, 10000, 0);
        dataset.setCorpusCount(current.size());
        dataset.setReadyCorpusCount((int) current.stream().filter(value -> "READY".equals(value.getStatus())).count());
        dataset.setStatus(failed > 0 ? "ERROR" : "ACTIVE");
        evaluationRepository.updateDataset(dataset);
        return RecallCorpusImportResultVO.builder()
                .acceptedCount(accepted).failedCount(failed).items(results).build();
    }

    private void prepareReusableAttachment(RecallEvaluationCorpusItemEntity item,
                                           RecallRagAttachmentItemVO input) {
        item.setItemType("RAG_DOCUMENT");
        item.setTitle(input.getTitle());
        item.setSummary(input.getSummary());
        item.setTagsJson(JSON.toJSONString(input.getTags() == null ? List.of() : input.getTags()));
        item.setStatus("PENDING");
        clearFailure(item);
    }

    private void attachEvaluationMetadata(RecallEvaluationDatasetEntity dataset,
                                          RecallEvaluationCorpusItemEntity item,
                                          List<RagChunkEntity> chunks) {
        Map<String, Object> metadata = Map.of(
                "evalDatasetId", dataset.getDatasetId(),
                "evalExternalId", item.getExternalId(),
                "evalCorpusItemId", item.getCorpusItemId());
        int attached = 0;
        for (RagChunkEntity chunk : chunks) {
            attached += ragVectorIndexingService.mergeChunkMetadata(chunk, metadata);
        }
        if (attached != chunks.size()) {
            throw new IllegalStateException("Uploaded RAG document has " + chunks.size()
                    + " chunks, but only " + attached + " vector rows accepted evaluation metadata.");
        }
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
        if ("RAG_CHUNK".equals(item.getItemType())) {
            ingestRagChunk(dataset, input, item, metadata);
            return;
        }
        if ("RAG_DOCUMENT".equals(item.getItemType())) {
            throw new IllegalArgumentException(
                    "RAG documents must be uploaded through /api/v1/rag/knowledge/files before attachment.");
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

    private void ingestRagChunk(RecallEvaluationDatasetEntity dataset,
                                RecallCorpusImportItemVO input,
                                RecallEvaluationCorpusItemEntity item,
                                Map<String, Object> metadata) {
        if (ragDomainService == null || ragAssetRepository == null || ragVectorIndexingService == null) {
            throw new IllegalStateException("Production RAG ingestion is not configured for evaluation.");
        }
        List<RagDocumentEntity> documents = ragDomainService.ingestFiles(RagFileIngestCommandEntity.builder()
                .userId(dataset.getEvalUserId())
                .sessionId(dataset.getEvalSessionId())
                .knowledgeTag(dataset.getDatasetId())
                // Bind evaluation metadata only after proving that this input produced one chunk.
                // A malformed manual import must not leak multiple vectors into the dataset filter.
                .indexingMetadata(Map.of())
                .files(List.of(RagFilePayloadEntity.builder()
                        .fileName(input.getExternalId() + ".md")
                        .content(input.getContent().getBytes(StandardCharsets.UTF_8))
                        .build()))
                .build());
        if (documents == null || documents.size() != 1) {
            throw new IllegalStateException("RAG chunk ingestion must create exactly one document.");
        }
        RagDocumentEntity document = documents.get(0);
        List<RagChunkEntity> chunks = ragAssetRepository.findChunksByDocumentId(document.getDocumentId());
        if (chunks == null || chunks.size() != 1) {
            throw new IllegalArgumentException("RAG_CHUNK content must produce exactly one chunk, but produced "
                    + (chunks == null ? 0 : chunks.size()) + ". Remove blank-line paragraph breaks or shorten the content.");
        }
        RagChunkEntity chunk = chunks.get(0);
        int attached = ragVectorIndexingService.mergeChunkMetadata(chunk, metadata);
        if (attached != 1) {
            throw new IllegalStateException("The generated RAG chunk vector could not be bound to the evaluation dataset.");
        }
        item.setTitle(firstNonBlank(input.getTitle(), document.getTitle()));
        item.setSummary(firstNonBlank(input.getSummary(), chunk.getSummary()));
        item.setContentRef(chunk.getContentRef());
        item.setSourceType(ragCollection(chunk).name());
        item.setSourceId(chunk.getChunkId());
        item.setParentSourceId(document.getDocumentId());
        item.setSourceRefsJson(JSON.toJSONString(List.of(chunk.getChunkId())));
    }

    private void validate(String datasetId, RecallCorpusImportItemVO input) {
        if (input == null || isBlank(input.getExternalId()) || isBlank(input.getContent())) {
            throw new IllegalArgumentException("externalId and content are required.");
        }
        input.setExternalId(RecallEvaluationIdPolicy.requireNumericId(input.getExternalId(), "Corpus externalId"));
        String type = normalizedType(input.getType());
        if (!List.of("RAG_CHUNK", "RAG_DOCUMENT", "LONG_TERM_MEMORY", "USER_PREFERENCE").contains(type)) {
            throw new IllegalArgumentException("Unsupported corpus type: " + input.getType());
        }
    }

    private List<String> recoverRagChunkBinding(RecallEvaluationCorpusItemEntity item,
                                                 RecallEvaluationDatasetEntity dataset) {
        RagDocumentEntity document = ragAssetRepository.findLatestDocument(
                        dataset.getEvalUserId(), dataset.getEvalSessionId(), item.getExternalId() + ".md")
                .orElseThrow(() -> new IllegalStateException(
                        "No recoverable RAG document was found for externalId " + item.getExternalId() + "."));
        List<RagChunkEntity> chunks = ragAssetRepository.findChunksByDocumentId(document.getDocumentId());
        if (chunks == null || chunks.size() != 1) {
            throw new IllegalStateException("RAG_CHUNK recovery requires exactly one persisted chunk, but found "
                    + (chunks == null ? 0 : chunks.size()) + ".");
        }
        RagChunkEntity chunk = chunks.get(0);
        item.setTitle(firstNonBlank(item.getTitle(), document.getTitle()));
        item.setSummary(firstNonBlank(item.getSummary(), chunk.getSummary()));
        item.setContentRef(chunk.getContentRef());
        item.setSourceType(ragCollection(chunk).name());
        item.setSourceId(chunk.getChunkId());
        item.setParentSourceId(document.getDocumentId());
        item.setSourceRefsJson(JSON.toJSONString(List.of(chunk.getChunkId())));
        // Persist the recovered binding before embedding. A second transient failure can then be retried directly.
        evaluationRepository.updateCorpusItem(item);
        return List.of(chunk.getChunkId());
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

    private RecallEvaluationCorpusItemEntity createPendingAttachment(
            String datasetId,
            RecallRagAttachmentItemVO input) {
        return RecallEvaluationCorpusItemEntity.builder()
                .datasetId(datasetId)
                .externalId(input == null ? null : input.getExternalId())
                .itemType("RAG_DOCUMENT")
                .title(input == null ? null : input.getTitle())
                .summary(input == null ? null : input.getSummary())
                .tagsJson(JSON.toJSONString(input == null || input.getTags() == null ? List.of() : input.getTags()))
                .status("PENDING")
                .build();
    }

    private RagDocumentEntity validateAttachment(RecallRagAttachmentItemVO input) {
        if (input == null || isBlank(input.getExternalId()) || isBlank(input.getDocumentId())) {
            throw new IllegalArgumentException("externalId and documentId are required.");
        }
        return ragAssetRepository.findDocument(input.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Uploaded RAG document does not exist: " + input.getDocumentId()));
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
