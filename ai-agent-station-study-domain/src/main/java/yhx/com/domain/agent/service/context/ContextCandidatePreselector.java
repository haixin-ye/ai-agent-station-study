package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.adapter.repository.ITurnSummaryRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.MessageCandidateVO;
import yhx.com.domain.agent.model.valobj.context.RunMetaVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.context.UserInputVO;
import yhx.com.domain.agent.service.artifact.ArtifactCandidateRanker;
import yhx.com.domain.agent.service.evidence.EvidenceCandidatePreselector;
import yhx.com.domain.agent.service.memory.MemoryCandidatePreselector;
import yhx.com.domain.agent.service.memory.TurnSummaryRecallPreselector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class ContextCandidatePreselector {

    private static final int DEFAULT_RECENT_MESSAGE_LIMIT = 16;
    private static final int DEFAULT_FULL_TURN_LIMIT = 6;
    private static final int DEFAULT_SUMMARY_TURN_LIMIT = 6;
    private static final int DEFAULT_RECALL_SUMMARY_SCAN_LIMIT = 40;
    private static final int DEFAULT_RECALL_SUMMARY_LIMIT = 6;
    private static final int DEFAULT_ARTIFACT_LIMIT = 5;
    private static final int DEFAULT_MEMORY_LIMIT = 5;
    private static final int DEFAULT_EVIDENCE_LIMIT = 5;
    private static final int MAX_MESSAGE_CONTEXT_CHARS = 1600;

    private final IConversationRepository conversationRepository;
    private final IArtifactRepository artifactRepository;
    private final IMemoryRepository memoryRepository;
    private final IEvidenceRepository evidenceRepository;
    private final IPayloadRepository payloadRepository;
    private final ITurnRepository turnRepository;
    private final ITurnSummaryRepository turnSummaryRepository;
    private final ContextTokenEstimator tokenEstimator;
    private final ArtifactCandidateRanker artifactCandidateRanker;
    private final MemoryCandidatePreselector memoryCandidatePreselector;
    private final TurnSummaryRecallPreselector turnSummaryRecallPreselector;
    private final EvidenceCandidatePreselector evidenceCandidatePreselector;

    public ContextCandidatePreselector(IConversationRepository conversationRepository,
                                       IMemoryRepository memoryRepository,
                                       IEvidenceRepository evidenceRepository,
                                       IPayloadRepository payloadRepository) {
        this(conversationRepository, null, memoryRepository, evidenceRepository, payloadRepository);
    }

    public ContextCandidatePreselector(IConversationRepository conversationRepository,
                                       IArtifactRepository artifactRepository,
                                       IMemoryRepository memoryRepository,
                                       IEvidenceRepository evidenceRepository,
                                       IPayloadRepository payloadRepository) {
        this(conversationRepository, artifactRepository, memoryRepository, evidenceRepository, payloadRepository, null, null);
    }

    public ContextCandidatePreselector(IConversationRepository conversationRepository,
                                       IArtifactRepository artifactRepository,
                                       IMemoryRepository memoryRepository,
                                       IEvidenceRepository evidenceRepository,
                                       IPayloadRepository payloadRepository,
                                       ITurnRepository turnRepository,
                                       ITurnSummaryRepository turnSummaryRepository) {
        this.conversationRepository = conversationRepository;
        this.artifactRepository = artifactRepository;
        this.memoryRepository = memoryRepository;
        this.evidenceRepository = evidenceRepository;
        this.payloadRepository = payloadRepository;
        this.turnRepository = turnRepository;
        this.turnSummaryRepository = turnSummaryRepository;
        this.tokenEstimator = new ContextTokenEstimator();
        this.artifactCandidateRanker = new ArtifactCandidateRanker(tokenEstimator);
        this.memoryCandidatePreselector = new MemoryCandidatePreselector();
        this.turnSummaryRecallPreselector = new TurnSummaryRecallPreselector(payloadRepository);
        this.evidenceCandidatePreselector = new EvidenceCandidatePreselector();
    }

    public ContextCandidateBundleVO buildCandidates(ContextPreparationCommand command) {
        int messageLimit = defaultIfNull(command.getRecentMessageLimit(), DEFAULT_RECENT_MESSAGE_LIMIT);
        int artifactLimit = defaultIfNull(command.getArtifactCandidateLimit(), DEFAULT_ARTIFACT_LIMIT);
        int memoryLimit = defaultIfNull(command.getMemoryCandidateLimit(), DEFAULT_MEMORY_LIMIT);
        int evidenceLimit = defaultIfNull(command.getEvidenceCandidateLimit(), DEFAULT_EVIDENCE_LIMIT);

        TurnContextWindow turnWindow = buildTurnContextWindow(command, messageLimit);
        List<SummaryCandidateVO> sessionSummaries = mergeSummaries(turnWindow.summaries(),
                recallRelevantSummaries(command, turnWindow.summaries()));

        ContextCandidateBundleVO bundle = ContextCandidateBundleVO.builder()
                .runMeta(RunMetaVO.builder()
                        .runId(command.getRunId())
                        .sessionId(command.getSessionId())
                        .userId(command.getUserId())
                        .agentId(command.getAgentId())
                        .loopIndex(command.getLoopIndex())
                        .build())
                .userInput(UserInputVO.builder()
                        .messageId(command.getUserMessageId())
                        .content(command.getUserInput())
                        .build())
                .recentMessages(turnWindow.messages())
                .sessionSummaries(sessionSummaries)
                .artifactCandidates(artifactCandidateRanker.rank(command.getUserInput(), artifactCandidates(command, artifactLimit, sessionSummaries), artifactLimit))
                .memoryCandidates(memoryCandidatePreselector.select(command.getUserInput(),
                        memoryRepository.findMemoryCandidates(command.getUserId(), command.getSessionId(), command.getUserInput(), memoryLimit), memoryLimit))
                .evidenceCandidates(evidenceCandidatePreselector.select(command.getUserInput(),
                        evidenceRepository.listRunEvidence(command.getRunId()), evidenceLimit))
                .userClarifications(userClarifications(command.getRuntimeFacts()))
                .availableCapabilities(command.getAvailableCapabilities() == null ? List.of() : command.getAvailableCapabilities())
                .tokenBudget(defaultBudget(command.getTokenBudget()))
                .build();
        bundle.getTokenBudget().setCurrentCandidateTokens(tokenEstimator.estimateObjectTokens(bundle));
        return bundle;
    }

    private List<AgentArtifactEntity> artifactCandidates(ContextPreparationCommand command, int artifactLimit, List<SummaryCandidateVO> sessionSummaries) {
        Map<String, AgentArtifactEntity> merged = new LinkedHashMap<>();
        if (artifactRepository != null) {
            artifactRepository.findArtifactCandidates(command.getSessionId(), command.getUserInput(), artifactLimit)
                    .forEach(artifact -> merged.put(artifact.getArtifactId(), artifact));
        }
        if (artifactRepository != null && sessionSummaries != null) {
            sessionSummaries.stream()
                    .filter(summary -> summary.getArtifactRefs() != null)
                    .flatMap(summary -> summary.getArtifactRefs().stream())
                    .distinct()
                    .forEach(artifactId -> artifactRepository.findArtifact(artifactId)
                            .ifPresent(artifact -> merged.putIfAbsent(artifact.getArtifactId(), artifact)));
        }
        if (command.getArtifactSeeds() != null) {
            command.getArtifactSeeds().forEach(artifact -> merged.putIfAbsent(artifact.getArtifactId(), artifact));
        }
        return new ArrayList<>(merged.values());
    }

    private List<SummaryCandidateVO> recallRelevantSummaries(ContextPreparationCommand command, List<SummaryCandidateVO> fixedSummaries) {
        if (turnSummaryRepository == null) {
            return List.of();
        }
        Set<String> excludedIds = fixedSummaries == null ? Set.of() : fixedSummaries.stream()
                .map(SummaryCandidateVO::getSummaryId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return turnSummaryRecallPreselector.select(command.getUserInput(),
                turnSummaryRepository.listRecentActiveSummaries(command.getSessionId(), DEFAULT_RECALL_SUMMARY_SCAN_LIMIT),
                excludedIds,
                DEFAULT_RECALL_SUMMARY_LIMIT);
    }

    private List<SummaryCandidateVO> mergeSummaries(List<SummaryCandidateVO> fixedSummaries, List<SummaryCandidateVO> recalledSummaries) {
        Map<String, SummaryCandidateVO> merged = new LinkedHashMap<>();
        if (fixedSummaries != null) {
            fixedSummaries.forEach(summary -> merged.put(summary.getSummaryId(), summary));
        }
        if (recalledSummaries != null) {
            recalledSummaries.forEach(summary -> merged.putIfAbsent(summary.getSummaryId(), summary));
        }
        return new ArrayList<>(merged.values());
    }

    private TurnContextWindow buildTurnContextWindow(ContextPreparationCommand command, int messageLimit) {
        if (turnRepository == null || turnSummaryRepository == null) {
            List<MessageCandidateVO> messages = conversationRepository.listRecentVisibleMessages(command.getSessionId(), messageLimit).stream()
                    .filter(message -> command.getUserMessageId() == null || !command.getUserMessageId().equals(message.getMessageId()))
                    .map(this::toMessageCandidate)
                    .toList();
            return new TurnContextWindow(messages, List.of());
        }

        List<AgentTurnEntity> recentTurns = turnRepository.listRecentCompletedTurns(command.getSessionId(), DEFAULT_FULL_TURN_LIMIT).stream()
                .filter(turn -> "COMPLETED".equals(turn.getStatus()))
                .sorted(Comparator.comparing(AgentTurnEntity::getTurnNo, Comparator.nullsLast(Long::compareTo)))
                .toList();
        List<MessageCandidateVO> messages = recentTurns.stream()
                .flatMap(turn -> Stream.of(
                        toTurnMessageCandidate(turn.getUserMessageId(), "USER", turn.getUserPayloadRef(), turn.getCompletedAt()),
                        toTurnMessageCandidate(turn.getAssistantMessageId(), "ASSISTANT", turn.getAssistantPayloadRef(), turn.getCompletedAt())))
                .filter(Objects::nonNull)
                .filter(message -> command.getUserMessageId() == null || !command.getUserMessageId().equals(message.getMessageId()))
                .toList();

        Long beforeTurnNo = recentTurns.stream()
                .map(AgentTurnEntity::getTurnNo)
                .filter(Objects::nonNull)
                .min(Long::compareTo)
                .orElse(null);
        List<String> previousTurnIds = beforeTurnNo == null
                ? List.of()
                : turnRepository.listCompletedTurnsBefore(command.getSessionId(), beforeTurnNo, DEFAULT_SUMMARY_TURN_LIMIT).stream()
                .sorted(Comparator.comparing(AgentTurnEntity::getTurnNo, Comparator.nullsLast(Long::compareTo)))
                .map(AgentTurnEntity::getTurnId)
                .toList();
        List<SummaryCandidateVO> summaries = previousTurnIds.isEmpty()
                ? List.of()
                : turnSummaryRepository.listByTurnIds(previousTurnIds).stream()
                .sorted(Comparator.comparing(summary -> previousTurnIds.indexOf(summary.getTurnId())))
                .map(this::toSummaryCandidate)
                .toList();
        return new TurnContextWindow(messages, summaries);
    }

    private MessageCandidateVO toTurnMessageCandidate(String messageId, String role, String contentRef, java.time.LocalDateTime createdAt) {
        if (messageId == null || messageId.isBlank()) {
            return null;
        }
        String summary = contentRef == null ? null : payloadRepository.findPayload(contentRef)
                .map(payload -> compactVisibleMessage(payload.getContent(), payload.getPreview()))
                .orElse(null);
        return MessageCandidateVO.builder()
                .messageId(messageId)
                .role(role)
                .contentRef(contentRef)
                .summary(summary)
                .createdAt(createdAt)
                .build();
    }

    private SummaryCandidateVO toSummaryCandidate(AgentTurnSummaryEntity summary) {
        String text = summary.getSummaryRef() == null ? null : payloadRepository.findPayload(summary.getSummaryRef())
                .map(payload -> compactVisibleMessage(payload.getContent(), payload.getPreview()))
                .orElse(null);
        return SummaryCandidateVO.builder()
                .summaryId(summary.getSummaryId())
                .turnId(summary.getTurnId())
                .summary(text)
                .summaryRef(summary.getSummaryRef())
                .artifactRefs(parseStringList(summary.getArtifactRefsJson()))
                .createdAt(summary.getCreatedAt())
                .build();
    }

    private MessageCandidateVO toMessageCandidate(AgentMessageEntity message) {
        String summary = payloadRepository.findPayload(message.getContentRef())
                .map(payload -> compactVisibleMessage(payload.getContent(), payload.getPreview()))
                .orElse(null);
        return MessageCandidateVO.builder()
                .messageId(message.getMessageId())
                .role(message.getRole() == null ? null : message.getRole().code())
                .contentRef(message.getContentRef())
                .summary(summary)
                .createdAt(message.getCreatedAt())
                .build();
    }

    private String compactVisibleMessage(String content, String preview) {
        String text = firstNonBlank(content, preview);
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.length() <= MAX_MESSAGE_CONTEXT_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_MESSAGE_CONTEXT_CHARS) + "... [truncated]";
    }

    private TokenBudgetVO defaultBudget(TokenBudgetVO budget) {
        if (budget != null) {
            return budget;
        }
        return TokenBudgetVO.builder()
                .maxStateViewTokens(6000)
                .reservedOutputTokens(1000)
                .maxArtifactInlineChars(4000)
                .maxEvidenceSummaryChars(800)
                .overBudget(false)
                .build();
    }

    private int defaultIfNull(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = com.alibaba.fastjson.JSON.parseArray(json, String.class);
            return values == null ? List.of() : values;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<UserClarificationVO> userClarifications(Map<String, Object> runtimeFacts) {
        if (runtimeFacts == null) {
            return List.of();
        }
        Object value = runtimeFacts.get("userClarifications");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(UserClarificationVO.class::isInstance)
                .map(item -> (UserClarificationVO) item)
                .toList();
    }

    private record TurnContextWindow(List<MessageCandidateVO> messages, List<SummaryCandidateVO> summaries) {
    }
}
