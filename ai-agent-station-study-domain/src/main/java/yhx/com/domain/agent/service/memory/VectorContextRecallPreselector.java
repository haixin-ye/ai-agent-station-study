package yhx.com.domain.agent.service.memory;

import com.alibaba.fastjson.JSON;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VectorContextRecallPreselector {

    private static final int DEFAULT_TOP_K = 12;

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
        if (command == null || isBlank(command.getUserInput()) || vectorMemoryRepository == null) {
            return emptyBundle();
        }
        List<VectorRecallHitVO> hits = vectorMemoryRepository.search(VectorRecallQueryVO.builder()
                .queryText(command.getUserInput())
                .topK(DEFAULT_TOP_K)
                .filter(VectorRecallFilterVO.builder()
                        .userId(command.getUserId())
                        .sessionId(command.getSessionId())
                        .collectionTypes(defaultCollections())
                        .build())
                .build());
        return resolveHits(hits);
    }

    private ContextCandidateBundleVO resolveHits(List<VectorRecallHitVO> hits) {
        if (hits == null || hits.isEmpty()) {
            return emptyBundle();
        }
        Map<String, SummaryCandidateVO> summaries = new LinkedHashMap<>();
        Map<String, ArtifactCandidateVO> artifacts = new LinkedHashMap<>();
        Map<String, MemoryCandidateVO> memories = new LinkedHashMap<>();
        for (VectorRecallHitVO hit : hits) {
            if (hit == null || hit.getSourceType() == null || isBlank(hit.getSourceId())) {
                continue;
            }
            switch (hit.getSourceType()) {
                case TURN_SUMMARY, CONVERSATION_SUMMARY -> resolveSummary(hit).ifPresent(summary -> summaries.putIfAbsent(summary.getSummaryId(), summary));
                case ARTIFACT_SUMMARY, ARTIFACT_CHUNK -> resolveArtifact(hit).ifPresent(artifact -> artifacts.putIfAbsent(artifact.getArtifactId(), artifact));
                case LONG_TERM_MEMORY, USER_PREFERENCE -> resolveMemory(hit).ifPresent(memory -> memories.putIfAbsent(memory.getMemoryId(), memory));
                case RAG_DOCUMENT, RAG_CHUNK -> {
                    // RAG hits are resolved by the RAG pipeline; keep this preselector MySQL-backed for now.
                }
            }
        }
        return ContextCandidateBundleVO.builder()
                .sessionSummaries(new ArrayList<>(summaries.values()))
                .artifactCandidates(new ArrayList<>(artifacts.values()))
                .memoryCandidates(new ArrayList<>(memories.values()))
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
                .summary(resolvePayloadText(summary.getSummaryRef(), firstNonBlank(hit.getSummary(), hit.getSnippet())))
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

    private MemoryCandidateVO toMemoryCandidate(AgentMemoryEntity memory) {
        return MemoryCandidateVO.builder()
                .memoryId(memory.getMemoryId())
                .memoryType(memory.getMemoryType())
                .summary(memory.getSummary())
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

    private List<VectorCollectionTypeEnumVO> defaultCollections() {
        return List.of(
                VectorCollectionTypeEnumVO.TURN_SUMMARY,
                VectorCollectionTypeEnumVO.CONVERSATION_SUMMARY,
                VectorCollectionTypeEnumVO.LONG_TERM_MEMORY,
                VectorCollectionTypeEnumVO.USER_PREFERENCE,
                VectorCollectionTypeEnumVO.ARTIFACT_SUMMARY,
                VectorCollectionTypeEnumVO.ARTIFACT_CHUNK,
                VectorCollectionTypeEnumVO.RAG_DOCUMENT,
                VectorCollectionTypeEnumVO.RAG_CHUNK);
    }

    private ContextCandidateBundleVO emptyBundle() {
        return ContextCandidateBundleVO.builder()
                .sessionSummaries(List.of())
                .artifactCandidates(List.of())
                .memoryCandidates(List.of())
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safeName(VectorCollectionTypeEnumVO collectionType) {
        return collectionType == null ? "unknown" : collectionType.name();
    }
}
