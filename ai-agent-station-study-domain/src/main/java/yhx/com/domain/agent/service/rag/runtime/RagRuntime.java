package yhx.com.domain.agent.service.rag.runtime;

import com.alibaba.fastjson.JSON;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.IRagExecutionRepository;
import yhx.com.domain.agent.adapter.repository.IRunRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentEvidenceEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.RagHitEntity;
import yhx.com.domain.agent.model.entity.persistence.RagQueryEntity;
import yhx.com.domain.agent.model.valobj.enums.persistence.PayloadTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.persistence.RagQueryStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RagRuntimeStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.rag.RagHitVO;
import yhx.com.domain.agent.model.valobj.rag.RagRetrievalCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeCommandVO;
import yhx.com.domain.agent.model.valobj.runtime.RagRuntimeResultVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeSafeFailureVO;
import yhx.com.domain.agent.service.runtime.DeveloperTraceRecorder;
import yhx.com.domain.agent.service.runtime.RunEventPublisher;
import yhx.com.domain.agent.service.runtime.RuntimeFailureFactory;
import yhx.com.domain.agent.service.runtime.port.RagRuntimePort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RagRuntime implements RagRuntimePort {

    private final IRunRepository runRepository;
    private final IRagExecutionRepository ragExecutionRepository;
    private final IPayloadRepository payloadRepository;
    private final IEvidenceRepository evidenceRepository;
    private final RagRetrieverPort ragRetrieverPort;
    private final RagEvidenceConverter evidenceConverter;
    private final RunEventPublisher eventPublisher;
    private final DeveloperTraceRecorder traceRecorder;
    private final RuntimeFailureFactory failureFactory;
    private final int defaultTopK;
    private final int maxTopK;
    private final int maxHitChars;

    public RagRuntime(IRunRepository runRepository,
                      IRagExecutionRepository ragExecutionRepository,
                      IPayloadRepository payloadRepository,
                      IEvidenceRepository evidenceRepository,
                      RagRetrieverPort ragRetrieverPort) {
        this(runRepository, ragExecutionRepository, payloadRepository, evidenceRepository, ragRetrieverPort,
                new RagEvidenceConverter(), null, null, new RuntimeFailureFactory(), 5, 10, 1200);
    }

    public RagRuntime(IRunRepository runRepository,
                      IRagExecutionRepository ragExecutionRepository,
                      IPayloadRepository payloadRepository,
                      IEvidenceRepository evidenceRepository,
                      RagRetrieverPort ragRetrieverPort,
                      RagEvidenceConverter evidenceConverter,
                      RunEventPublisher eventPublisher,
                      DeveloperTraceRecorder traceRecorder,
                      RuntimeFailureFactory failureFactory,
                      int defaultTopK,
                      int maxTopK,
                      int maxHitChars) {
        this.runRepository = runRepository;
        this.ragExecutionRepository = ragExecutionRepository;
        this.payloadRepository = payloadRepository;
        this.evidenceRepository = evidenceRepository;
        this.ragRetrieverPort = ragRetrieverPort;
        this.evidenceConverter = evidenceConverter == null ? new RagEvidenceConverter() : evidenceConverter;
        this.eventPublisher = eventPublisher;
        this.traceRecorder = traceRecorder;
        this.failureFactory = failureFactory == null ? new RuntimeFailureFactory() : failureFactory;
        this.defaultTopK = defaultTopK <= 0 ? 5 : defaultTopK;
        this.maxTopK = maxTopK <= 0 ? this.defaultTopK : maxTopK;
        this.maxHitChars = maxHitChars <= 0 ? 1200 : maxHitChars;
    }

    @Override
    public RagRuntimeResultVO retrieve(RagRuntimeCommandVO command) {
        validate(command);
        markRagUsed(command.getRunId());
        publish(command.getRunId(), "RETRIEVING_KNOWLEDGE", "Retrieving knowledge.");

        String ragQueryId = saveRequestedQuery(command);
        try {
            List<RagHitVO> hits = retrieveHits(command);
            List<RagHitVO> usableHits = persistHits(command.getRunId(), ragQueryId, hits);
            List<String> evidenceIds = persistEvidence(command.getRunId(), command.getSessionId(), ragQueryId, usableHits);
            RagRuntimeStatusEnumVO status = evidenceIds.isEmpty() ? RagRuntimeStatusEnumVO.NO_HIT : RagRuntimeStatusEnumVO.SUCCESS;
            ragExecutionRepository.updateRagQueryStatus(ragQueryId, status.code(), null, null);
            publish(command.getRunId(), status == RagRuntimeStatusEnumVO.SUCCESS ? "KNOWLEDGE_RETRIEVED" : "KNOWLEDGE_NOT_FOUND",
                    status == RagRuntimeStatusEnumVO.SUCCESS ? "Knowledge evidence is ready." : "No matching knowledge evidence was found.");
            return RagRuntimeResultVO.builder()
                    .status(status)
                    .evidenceIds(evidenceIds)
                    .message(status == RagRuntimeStatusEnumVO.SUCCESS ? "RAG evidence created." : "RAG returned no usable evidence.")
                    .build();
        } catch (Exception e) {
            ragExecutionRepository.updateRagQueryStatus(ragQueryId, RagQueryStatusEnumVO.FAILED.code(),
                    RuntimeFailureCodeEnumVO.RAG_RETRIEVAL_FAILED.code(), e.getMessage());
            traceError(command.getRunId(), command.getLoopIndex(), e.getMessage());
            return RagRuntimeResultVO.builder()
                    .status(RagRuntimeStatusEnumVO.FAILED)
                    .safeFailure(failureFactory.create(RuntimeFailureCodeEnumVO.RAG_RETRIEVAL_FAILED,
                            RuntimePhaseEnumVO.EXECUTING_RAG, e.getMessage(), true))
                    .message("RAG retrieval failed.")
                    .build();
        }
    }

    private void validate(RagRuntimeCommandVO command) {
        if (command == null) {
            throw new IllegalArgumentException("RagRuntimeCommand is required.");
        }
        if (isBlank(command.getRunId())) {
            throw new IllegalArgumentException("runId is required.");
        }
        if (isBlank(command.getQuery())) {
            throw new IllegalArgumentException("ragRequest.query is required.");
        }
        if (ragExecutionRepository == null || payloadRepository == null || evidenceRepository == null || ragRetrieverPort == null) {
            throw new IllegalStateException("RAG runtime dependencies are not fully configured.");
        }
    }

    private void markRagUsed(String runId) {
        if (runRepository != null) {
            runRepository.markRagWasUsed(runId);
        }
    }

    private String saveRequestedQuery(RagRuntimeCommandVO command) {
        RagQueryEntity query = RagQueryEntity.builder()
                .runId(command.getRunId())
                .queryText(command.getQuery())
                .knowledgeTag(command.getKnowledgeName())
                .filtersRef(saveFilters(command.getOptions()))
                .topK(resolveTopK(command.getOptions()))
                .status(RagQueryStatusEnumVO.REQUESTED.code())
                .createdAt(LocalDateTime.now())
                .build();
        return ragExecutionRepository.saveRagQuery(query);
    }

    private String saveFilters(Map<String, Object> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.JSON)
                .content(JSON.toJSONString(options))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private List<RagHitVO> retrieveHits(RagRuntimeCommandVO command) {
        return ragRetrieverPort.retrieve(RagRetrievalCommandVO.builder()
                .runId(command.getRunId())
                .sessionId(command.getSessionId())
                .loopIndex(command.getLoopIndex())
                .query(command.getQuery())
                .knowledgeName(command.getKnowledgeName())
                .topK(resolveTopK(command.getOptions()))
                .maxHitChars(maxHitChars)
                .runtimeFilters(command.getOptions())
                .build());
    }

    private List<RagHitVO> persistHits(String runId, String ragQueryId, List<RagHitVO> hits) {
        if (hits == null || hits.isEmpty()) {
            ragExecutionRepository.saveRagHits(List.of());
            return List.of();
        }
        List<RagHitEntity> entities = new ArrayList<>();
        List<RagHitVO> usableHits = new ArrayList<>();
        int rank = 1;
        for (RagHitVO hit : hits) {
            if (hit == null || isBlank(hit.getChunkText())) {
                continue;
            }
            if (isBlank(hit.getRagHitId())) {
                hit.setRagHitId("rag-hit-" + UUID.randomUUID());
            }
            if (hit.getRankNo() == null) {
                hit.setRankNo(rank);
            }
            hit.setChunkRef(saveChunk(hit.getChunkText()));
            entities.add(RagHitEntity.builder()
                    .ragHitId(hit.getRagHitId())
                    .ragQueryId(ragQueryId)
                    .runId(runId)
                    .chunkRef(hit.getChunkRef())
                    .score(hit.getScore() == null ? null : BigDecimal.valueOf(hit.getScore()))
                    .sourceTitle(hit.getTitle())
                    .sourceUri(firstNonBlank(hit.getSourceId(), hit.getSourceType()))
                    .createdAt(LocalDateTime.now())
                    .build());
            usableHits.add(hit);
            rank++;
        }
        ragExecutionRepository.saveRagHits(entities);
        return usableHits;
    }

    private String saveChunk(String chunkText) {
        return payloadRepository.savePayload(AgentPayloadEntity.builder()
                .payloadType(PayloadTypeEnumVO.RAG_CHUNK)
                .content(chunkText)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private List<String> persistEvidence(String runId, String sessionId, String ragQueryId, List<RagHitVO> hits) {
        List<AgentEvidenceEntity> evidenceList = evidenceConverter.convert(runId, sessionId, ragQueryId, hits);
        if (evidenceList.isEmpty()) {
            return List.of();
        }
        return evidenceList.stream().map(evidenceRepository::saveEvidence).toList();
    }

    private int resolveTopK(Map<String, Object> options) {
        Object value = options == null ? null : options.get("topK");
        int topK = value instanceof Number number ? number.intValue() : defaultTopK;
        if (topK <= 0) {
            topK = defaultTopK;
        }
        return Math.min(topK, maxTopK);
    }

    private void publish(String runId, String title, String summary) {
        if (eventPublisher != null) {
            eventPublisher.phase(runId, title, summary);
        }
    }

    private void traceError(String runId, Integer loopIndex, String message) {
        if (traceRecorder != null) {
            traceRecorder.error(runId, loopIndex, RuntimeFailureCodeEnumVO.RAG_RETRIEVAL_FAILED, message, null);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first;
        }
        return second;
    }
}
