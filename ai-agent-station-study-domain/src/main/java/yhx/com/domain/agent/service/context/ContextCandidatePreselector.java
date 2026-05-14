package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.adapter.repository.IConversationRepository;
import yhx.com.domain.agent.adapter.repository.IEvidenceRepository;
import yhx.com.domain.agent.adapter.repository.IMemoryRepository;
import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextPreparationCommand;
import yhx.com.domain.agent.model.valobj.context.MessageCandidateVO;
import yhx.com.domain.agent.model.valobj.context.RunMetaVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.context.UserInputVO;
import yhx.com.domain.agent.service.artifact.ArtifactCandidateRanker;
import yhx.com.domain.agent.service.evidence.EvidenceCandidatePreselector;
import yhx.com.domain.agent.service.memory.MemoryCandidatePreselector;

import java.util.List;

public class ContextCandidatePreselector {

    private static final int DEFAULT_RECENT_MESSAGE_LIMIT = 8;
    private static final int DEFAULT_ARTIFACT_LIMIT = 5;
    private static final int DEFAULT_MEMORY_LIMIT = 5;
    private static final int DEFAULT_EVIDENCE_LIMIT = 5;

    private final IConversationRepository conversationRepository;
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
        this.conversationRepository = conversationRepository;
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
                .filter(message -> !command.getUserMessageId().equals(message.getMessageId()))
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
                .artifactCandidates(artifactCandidateRanker.rank(command.getUserInput(), command.getArtifactSeeds(), artifactLimit))
                .memoryCandidates(memoryCandidatePreselector.select(command.getUserInput(),
                        memoryRepository.findMemoryCandidates(command.getUserId(), command.getSessionId(), command.getUserInput(), memoryLimit), memoryLimit))
                .evidenceCandidates(evidenceCandidatePreselector.select(command.getUserInput(),
                        evidenceRepository.listRunEvidence(command.getRunId()), evidenceLimit))
                .availableCapabilities(command.getAvailableCapabilities() == null ? List.of() : command.getAvailableCapabilities())
                .tokenBudget(defaultBudget(command.getTokenBudget()))
                .build();
        bundle.getTokenBudget().setCurrentCandidateTokens(tokenEstimator.estimateObjectTokens(bundle));
        return bundle;
    }

    private MessageCandidateVO toMessageCandidate(AgentMessageEntity message) {
        String summary = payloadRepository.findPayload(message.getContentRef())
                .map(payload -> payload.getPreview() == null ? payload.getContent() : payload.getPreview())
                .orElse(null);
        return MessageCandidateVO.builder()
                .messageId(message.getMessageId())
                .role(message.getRole() == null ? null : message.getRole().code())
                .contentRef(message.getContentRef())
                .summary(summary)
                .createdAt(message.getCreatedAt())
                .build();
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
}
