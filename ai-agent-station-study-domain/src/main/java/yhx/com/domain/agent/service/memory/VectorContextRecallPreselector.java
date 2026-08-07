package yhx.com.domain.agent.service.memory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;
import yhx.com.domain.agent.model.valobj.context.ArtifactChunkVO;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.MemoryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;
import yhx.com.domain.agent.model.valobj.enums.memory.ContextCandidateSourceChannelEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallFilterVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.model.valobj.evaluation.DetailedRecallResultVO;
import yhx.com.domain.agent.model.valobj.evaluation.RecallExecutionOptionsVO;
import yhx.com.domain.agent.service.observability.AutoAgentHumanLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class VectorContextRecallPreselector {

    private static final int DEFAULT_TOP_K = 50;
    private static final double DEFAULT_MIN_SCORE = 0.3D;

    private final IVectorMemoryRepository vectorMemoryRepository;
    private final ITurnSummaryRepository turnSummaryRepository;
    private final IArtifactRepository artifactRepository;
    private final IMemoryRepository memoryRepository;
    private final IPayloadRepository payloadRepository;

    public VectorContextRecallPreselector(IVectorMemoryRepository vectorMemoryRepository,
                                          ITurnSummaryRepository turnSummaryRepository,
                                          IArtifactRepository artifactRepository,
                                          IMemoryRepository memoryRepository,
                                          IPayloadRepository payloadRepository) {
        this.vectorMemoryRepository = vectorMemoryRepository;
        this.turnSummaryRepository = turnSummaryRepository;
        this.artifactRepository = artifactRepository;
        this.memoryRepository = memoryRepository;
        this.payloadRepository = payloadRepository;
    }

    public ContextCandidateBundleVO recall(ContextPreparationCommand command) {
        return recallDetailed(command, null).getCandidateBundle();
    }

    public DetailedRecallResultVO recallDetailed(ContextPreparationCommand command,
                                                  RecallExecutionOptionsVO options) {
        if (command == null || isBlank(command.getUserInput()) || vectorMemoryRepository == null) {
            return detailed(emptyBundle(), List.of(), List.of(), 0L);
        }
        long startedAt = System.currentTimeMillis();
        List<VectorRecallHitVO> hits = new ArrayList<>();
        List<VectorRecallHitVO> lexicalHits = new ArrayList<>();
        List<VectorRecallHitVO> sessionHits = new ArrayList<>();
        List<VectorRecallHitVO> memoryHits = new ArrayList<>();
        if (options != null && options.getCollectionTypes() != null && !options.getCollectionTypes().isEmpty()) {
            VectorRecallQueryVO query = query(command, options, options.getCollectionTypes(), command.getSessionId());
            List<VectorRecallHitVO> selectedHits = safe(vectorMemoryRepository.search(query));
            hits.addAll(selectedHits);
            memoryHits.addAll(selectedHits);
            if (Boolean.TRUE.equals(options.getLexicalEnabled())) {
                lexicalHits.addAll(safe(vectorMemoryRepository.lexicalSearch(query)));
                hits.addAll(lexicalHits);
            }
        } else {
            sessionHits.addAll(safe(vectorMemoryRepository.search(query(command, null, sessionScopedCollections(), command.getSessionId()))));
            hits.addAll(sessionHits);
            memoryHits.addAll(safe(vectorMemoryRepository.search(query(command, null, userScopedMemoryCollections(), null))));
            hits.addAll(memoryHits);
        }
        ContextCandidateBundleVO bundle = resolveHits(hits);
        log.info("[AutoAgent][memory-vector-recall] runId={}, sessionId={}, userId={}, query={}, sessionHits={}, memoryHits={}, resolvedSummaries={}, resolvedMemories={}, resolvedEvidence={}, elapsedMs={}",
                command.getRunId(),
                command.getSessionId(),
                command.getUserId(),
                preview(command.getUserInput()),
                sessionHits.size(),
                memoryHits.size(),
                bundle.getSessionSummaries() == null ? 0 : bundle.getSessionSummaries().size(),
                bundle.getMemoryCandidates() == null ? 0 : bundle.getMemoryCandidates().size(),
                bundle.getEvidenceCandidates() == null ? 0 : bundle.getEvidenceCandidates().size(),
                System.currentTimeMillis() - startedAt);
        AutoAgentHumanLog.stage("向量记忆召回", command.getRunId(), "召回完成：sessionHits="
                + sessionHits.size()
                + "，memoryHits=" + memoryHits.size()
                + "，可用摘要=" + (bundle.getSessionSummaries() == null ? 0 : bundle.getSessionSummaries().size())
                + "，可用长期记忆=" + (bundle.getMemoryCandidates() == null ? 0 : bundle.getMemoryCandidates().size())
                + "，耗时=" + (System.currentTimeMillis() - startedAt) + "ms。");
        return detailed(bundle, hits, lexicalHits, System.currentTimeMillis() - startedAt);
    }

    private VectorRecallQueryVO query(ContextPreparationCommand command,
                                      RecallExecutionOptionsVO options,
                                      List<VectorCollectionTypeEnumVO> collectionTypes,
                                      String sessionId) {
        return VectorRecallQueryVO.builder()
                .queryText(command.getUserInput())
                .topK(options == null || options.getTopK() == null ? DEFAULT_TOP_K : options.getTopK())
                .minScore(options == null || options.getMinScore() == null ? DEFAULT_MIN_SCORE : options.getMinScore())
                .filter(VectorRecallFilterVO.builder()
                        .userId(command.getUserId())
                        .sessionId(sessionId)
                        .collectionTypes(collectionTypes)
                        .metadataFilters(options == null ? null : options.getMetadataFilters())
                        .build())
                .build();
    }

    private DetailedRecallResultVO detailed(ContextCandidateBundleVO bundle,
                                             List<VectorRecallHitVO> vectorHits,
                                             List<VectorRecallHitVO> lexicalHits,
                                             long elapsedMs) {
        return DetailedRecallResultVO.builder()
                .candidateBundle(bundle)
                .ragCandidates(List.of())
                .vectorHits(vectorHits == null ? List.of() : vectorHits)
                .lexicalHits(lexicalHits == null ? List.of() : lexicalHits)
                .elapsedMs(elapsedMs)
                .diagnostics(Map.of("resolvedMemoryCount", bundle == null || bundle.getMemoryCandidates() == null
                        ? 0 : bundle.getMemoryCandidates().size()))
                .build();
    }

    private List<VectorRecallHitVO> safe(List<VectorRecallHitVO> values) {
        return values == null ? List.of() : values;
    }

    private ContextCandidateBundleVO resolveHits(List<VectorRecallHitVO> hits) {
        if (hits == null || hits.isEmpty()) {
            return emptyBundle();
        }
        Map<String, SummaryCandidateVO> summaries = new LinkedHashMap<>();
        Map<String, MemoryCandidateVO> memories = new LinkedHashMap<>();
        for (VectorRecallHitVO hit : hits) {
            if (hit == null || hit.getSourceType() == null || isBlank(hit.getSourceId())) {
                continue;
            }
            switch (hit.getSourceType()) {
                case TURN_SUMMARY, CONVERSATION_SUMMARY -> resolveSummary(hit).ifPresent(summary -> summaries.putIfAbsent(summary.getSummaryId(), summary));
                case ARTIFACT_SUMMARY, ARTIFACT_CHUNK -> {
                    // Artifact context is logically deprecated in the redesigned memory path.
                }
                case LONG_TERM_MEMORY, USER_PREFERENCE -> resolveMemory(hit).ifPresent(memory -> memories.putIfAbsent(memory.getMemoryId(), memory));
                case RAG_DOCUMENT, RAG_CHUNK, RAG_FILE_CHUNK, RAG_CODE_FILE_SUMMARY, RAG_CODE_CHUNK -> {
                    // RAG candidates are owned by RagContextRecallPreselector so that they can be materialized
                    // with RAG-specific source types and injection levels.
                }
            }
        }
        return ContextCandidateBundleVO.builder()
                .sessionSummaries(new ArrayList<>(summaries.values()))
                .artifactCandidates(List.of())
                .memoryCandidates(new ArrayList<>(memories.values()))
                .evidenceCandidates(List.of())
                .build();
    }

    private Optional<SummaryCandidateVO> resolveSummary(VectorRecallHitVO hit) {
        if (turnSummaryRepository == null) {
            return Optional.empty();
        }
        Optional<AgentTurnSummaryEntity> entity = turnSummaryRepository.findSummaryById(hit.getSourceId());
        if (entity.isEmpty() && hit.getSourceType() == VectorSourceTypeEnumVO.CONVERSATION_SUMMARY) {
            return Optional.empty();
        }
        return entity.map(summary -> SummaryCandidateVO.builder()
                .summaryId(summary.getSummaryId())
                .turnId(summary.getTurnId())
                .summary(readablePayloadText(resolvePayloadText(summary.getSummaryRef(), firstNonBlank(hit.getSummary(), hit.getSnippet())), firstNonBlank(hit.getSummary(), hit.getSnippet())))
                .summaryRef(summary.getSummaryRef())
                .artifactRefs(parseStringList(summary.getArtifactRefsJson()))
                .relevanceScore(hit.getScore())
                .sourceChannel(ContextCandidateSourceChannelEnumVO.VECTOR_SEMANTIC.name())
                .sourceScore(hit.getScore())
                .sourceReasons(List.of("vector-hit:" + safeName(hit.getCollectionType())))
                .createdAt(summary.getCreatedAt())
                .build());
    }

    private Optional<ArtifactCandidateVO> resolveArtifact(VectorRecallHitVO hit) {
        if (artifactRepository == null) {
            return Optional.empty();
        }
        return artifactRepository.findArtifact(hit.getSourceId()).map(this::toArtifactCandidate)
                .map(candidate -> {
                    candidate.setSourceChannel(ContextCandidateSourceChannelEnumVO.VECTOR_SEMANTIC.name());
                    candidate.setSourceScore(hit.getScore());
                    candidate.setTotalScore(hit.getScore());
                    candidate.setReasons(List.of("vector-hit:" + safeName(hit.getCollectionType())));
                    return candidate;
                });
    }

    private Optional<ArtifactCandidateVO> resolveArtifactChunk(VectorRecallHitVO hit) {
        if (artifactRepository == null) {
            return Optional.empty();
        }
        String artifactId = metadataValue(hit, "artifactId");
        if (isBlank(artifactId)) {
            artifactId = metadataValue(hit, "artifact_id");
        }
        if (isBlank(artifactId)) {
            artifactId = hit.getSourceId();
        }
        String finalArtifactId = artifactId;
        return artifactRepository.findArtifact(finalArtifactId).map(this::toArtifactCandidate)
                .map(candidate -> {
                    candidate.setSourceChannel(ContextCandidateSourceChannelEnumVO.VECTOR_SEMANTIC.name());
                    candidate.setSourceScore(hit.getScore());
                    candidate.setTotalScore(hit.getScore());
                    candidate.setReasons(List.of("vector-hit:" + safeName(hit.getCollectionType())));
                    candidate.setMatchedChunks(List.of(ArtifactChunkVO.builder()
                            .chunkId(hit.getSourceId())
                            .sourceId(hit.getSourceId())
                            .index(parseInteger(metadataValue(hit, "chunkNo")))
                            .content(firstNonBlank(hit.getSnippet(), hit.getSummary()))
                            .build()));
                    return candidate;
                });
    }

    private Optional<MemoryCandidateVO> resolveMemory(VectorRecallHitVO hit) {
        if (memoryRepository == null) {
            return Optional.empty();
        }
        return memoryRepository.findMemory(hit.getSourceId()).map(this::toMemoryCandidate)
                .map(candidate -> {
                    candidate.setSourceChannel(ContextCandidateSourceChannelEnumVO.VECTOR_SEMANTIC.name());
                    candidate.setSourceScore(hit.getScore());
                    candidate.setRelevanceScore(hit.getScore());
                    return candidate;
                });
    }

    private ArtifactCandidateVO toArtifactCandidate(AgentArtifactEntity artifact) {
        return ArtifactCandidateVO.builder()
                .artifactId(artifact.getArtifactId())
                .artifactType(artifact.getArtifactType())
                .title(artifact.getTitle())
                .summary(artifact.getSummary())
                .contentRef(artifact.getContentRef())
                .version(artifact.getVersion())
                .createdAt(artifact.getCreatedAt())
                .updatedAt(artifact.getUpdatedAt())
                .lastMentionedAt(artifact.getLastMentionedAt())
                .build();
    }

    private ArtifactCandidateVO mergeArtifactChunks(ArtifactCandidateVO existing, ArtifactCandidateVO incoming) {
        if (existing.getMatchedChunks() == null || existing.getMatchedChunks().isEmpty()) {
            existing.setMatchedChunks(incoming.getMatchedChunks());
            return existing;
        }
        if (incoming.getMatchedChunks() == null || incoming.getMatchedChunks().isEmpty()) {
            return existing;
        }
        Map<String, ArtifactChunkVO> chunks = new LinkedHashMap<>();
        existing.getMatchedChunks().forEach(chunk -> chunks.put(firstNonBlank(chunk.getChunkId(), chunk.getSourceId()), chunk));
        incoming.getMatchedChunks().forEach(chunk -> chunks.putIfAbsent(firstNonBlank(chunk.getChunkId(), chunk.getSourceId()), chunk));
        existing.setMatchedChunks(new ArrayList<>(chunks.values()));
        return existing;
    }

    private MemoryCandidateVO toMemoryCandidate(AgentMemoryEntity memory) {
        return MemoryCandidateVO.builder()
                .memoryId(memory.getMemoryId())
                .memoryType(memory.getMemoryType())
                .summary(memory.getSummary())
                .content(resolvePayloadText(memory.getContentRef(), null))
                .contentRef(memory.getContentRef())
                .score(memory.getScore())
                .build();
    }

    private String resolvePayloadText(String payloadRef, String fallback) {
        if (isBlank(payloadRef) || payloadRepository == null) {
            return fallback;
        }
        return payloadRepository.findPayload(payloadRef)
                .map(AgentPayloadEntity::getContent)
                .filter(text -> !isBlank(text))
                .orElse(fallback);
    }

    private String readablePayloadText(String content, String fallback) {
        String text = firstNonBlank(content, fallback);
        if (isBlank(text)) {
            return null;
        }
        try {
            JSONObject object = JSON.parseObject(text);
            if (object == null) {
                return text;
            }
            String summary = object.getString("summary");
            return isBlank(summary) ? text : summary;
        } catch (Exception ignored) {
            return text;
        }
    }

    private List<VectorCollectionTypeEnumVO> sessionScopedCollections() {
        return List.of(
                VectorCollectionTypeEnumVO.TURN_SUMMARY);
    }

    private List<VectorCollectionTypeEnumVO> userScopedMemoryCollections() {
        return List.of(
                VectorCollectionTypeEnumVO.LONG_TERM_MEMORY,
                VectorCollectionTypeEnumVO.USER_PREFERENCE);
    }

    private ContextCandidateBundleVO emptyBundle() {
        return ContextCandidateBundleVO.builder()
                .sessionSummaries(List.of())
                .artifactCandidates(List.of())
                .memoryCandidates(List.of())
                .evidenceCandidates(List.of())
                .build();
    }

    private List<String> parseStringList(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            List<String> values = JSON.parseArray(json, String.class);
            return values == null ? List.of() : values;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private String metadataValue(VectorRecallHitVO hit, String key) {
        Object value = hit.getMetadata() == null ? null : hit.getMetadata().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer parseInteger(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String preview(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private String safeName(VectorCollectionTypeEnumVO collectionType) {
        return collectionType == null ? "unknown" : collectionType.name();
    }
}
