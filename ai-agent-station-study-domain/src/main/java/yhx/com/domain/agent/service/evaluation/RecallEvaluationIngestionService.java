package yhx.com.domain.agent.service.evaluation;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IRecallEvaluationRepository;
import yhx.com.domain.agent.adapter.repository.IRagAssetRepository;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCorpusItemEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationDatasetEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.rag.RagDocumentEntity;
import yhx.com.domain.agent.model.entity.rag.RagFileIngestCommandEntity;
import yhx.com.domain.agent.model.entity.rag.RagFilePayloadEntity;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCorpusImportItemVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallCorpusImportResultVO;
import yhx.com.domain.agent.service.memory.LongTermMemoryService;
import yhx.com.domain.agent.service.rag.RagAssetIngestionService;

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

    public RecallEvaluationIngestionService(IRecallEvaluationRepository evaluationRepository,
                                             RagAssetIngestionService ragAssetIngestionService,
                                             IRagAssetRepository ragAssetRepository,
                                             LongTermMemoryService longTermMemoryService) {
        this.evaluationRepository = evaluationRepository;
        this.ragAssetIngestionService = ragAssetIngestionService;
        this.ragAssetRepository = ragAssetRepository;
        this.longTermMemoryService = longTermMemoryService;
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
        for (RecallCorpusImportItemVO input : imports) {
            RecallEvaluationCorpusItemEntity item = createPending(dataset, input);
            try {
                validate(datasetId, input);
                evaluationRepository.saveCorpusItem(item);
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
                if (item.getCorpusItemId() == null) {
                    evaluationRepository.saveCorpusItem(item);
                } else {
                    evaluationRepository.updateCorpusItem(item);
                }
                failed++;
            }
            results.add(item);
        }
        dataset.setCorpusCount(number(dataset.getCorpusCount()) + imports.size());
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
}
