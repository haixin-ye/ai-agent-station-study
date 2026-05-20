package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.adapter.repository.IArtifactRepository;
import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentArtifactEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.MessageCandidateVO;
import yhx.com.domain.agent.model.valobj.context.RunMetaVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;
import yhx.com.domain.agent.model.valobj.context.UserInputVO;
import yhx.com.domain.agent.service.artifact.ArtifactCandidateRanker;
import yhx.com.domain.agent.service.evidence.EvidenceCandidatePreselector;
import yhx.com.domain.agent.service.memory.MemoryCandidatePreselector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContextCandidatePreselector {

    private static final int DEFAULT_RECENT_MESSAGE_LIMIT = 16;
    private static final int DEFAULT_ARTIFACT_LIMIT = 5;
    private static final int DEFAULT_MEMORY_LIMIT = 5;
    private static final int DEFAULT_EVIDENCE_LIMIT = 5;
    private static final int MAX_MESSAGE_CONTEXT_CHARS = 1600;

    private final IConversationRepository conversationRepository;
    private final IArtifactRepository artifactRepository;
    private final IMemoryRepository memoryRepository;
    private final IEvidenceRepository evidenceRepository;
    private final IPayloadRepository payloadRepository;
    private final ContextTokenEstimator tokenEstimator;
    private final ArtifactCandidateRanker artifactCandidateRanker;
    private final MemoryCandidatePreselector memoryCandidatePreselector;
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
        this.conversationRepository = conversationRepository;
        this.artifactRepository = artifactRepository;
        this.memoryRepository = memoryRepository;
        this.evidenceRepository = evidenceRepository;
        this.payloadRepository = payloadRepository;
        this.tokenEstimator = new ContextTokenEstimator();
        this.artifactCandidateRanker = new ArtifactCandidateRanker(tokenEstimator);
        this.memoryCandidatePreselector = new MemoryCandidatePreselector();
        this.evidenceCandidatePreselector = new EvidenceCandidatePreselector();
    }

    public ContextCandidateBundleVO buildCandidates(ContextPreparationCommand command) {
        int messageLimit = defaultIfNull(command.getRecentMessageLimit(), DEFAULT_RECENT_MESSAGE_LIMIT);
        int artifactLimit = defaultIfNull(command.getArtifactCandidateLimit(), DEFAULT_ARTIFACT_LIMIT);
        int memoryLimit = defaultIfNull(command.getMemoryCandidateLimit(), DEFAULT_MEMORY_LIMIT);
        int evidenceLimit = defaultIfNull(command.getEvidenceCandidateLimit(), DEFAULT_EVIDENCE_LIMIT);

        List<MessageCandidateVO> messages = conversationRepository.listRecentVisibleMessages(command.getSessionId(), messageLimit).stream()
                .filter(message -> command.getUserMessageId() == null || !command.getUserMessageId().equals(message.getMessageId()))
                .map(this::toMessageCandidate)
                .toList();

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
                .recentMessages(messages)
                .sessionSummaries(List.of())
                .artifactCandidates(artifactCandidateRanker.rank(command.getUserInput(), artifactCandidates(command, artifactLimit), artifactLimit))
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

    private List<AgentArtifactEntity> artifactCandidates(ContextPreparationCommand command, int artifactLimit) {
        Map<String, AgentArtifactEntity> merged = new LinkedHashMap<>();
        if (artifactRepository != null) {
            artifactRepository.findArtifactCandidates(command.getSessionId(), command.getUserInput(), artifactLimit)
                    .forEach(artifact -> merged.put(artifact.getArtifactId(), artifact));
        }
        if (command.getArtifactSeeds() != null) {
            command.getArtifactSeeds().forEach(artifact -> merged.putIfAbsent(artifact.getArtifactId(), artifact));
        }
        return new ArrayList<>(merged.values());
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
}
