package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.adapter.repository.IPayloadRepository;
import yhx.com.domain.agent.adapter.repository.ITurnRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentPayloadEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;
import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;
import yhx.com.domain.agent.model.valobj.context.ContextCandidateBundleVO;
import yhx.com.domain.agent.model.valobj.context.ContextMaterializationCommand;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewBuildCommand;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedArtifactContentVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedMemoryVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedRagVO;
import yhx.com.domain.agent.model.valobj.context.MemoryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.MessageCandidateVO;
import yhx.com.domain.agent.model.valobj.context.RagCandidateVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;
import yhx.com.domain.agent.service.artifact.ArtifactPayloadLoader;
import yhx.com.domain.agent.service.evidence.EvidencePackBuilder;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ContextMaterializer {

    private final ContextSelectionValidator selectionValidator;
    private final ContextSelectionMergePolicy selectionMergePolicy;
    private final ArtifactPayloadLoader artifactPayloadLoader;
    private final EvidencePackBuilder evidencePackBuilder;
    private final ContextBudgetManager budgetManager;
    private final MainAgentStateViewBuilder stateViewBuilder;
    private final ITurnRepository turnRepository;
    private final IPayloadRepository payloadRepository;

    public ContextMaterializer(ContextSelectionValidator selectionValidator,
                               ArtifactPayloadLoader artifactPayloadLoader,
                               EvidencePackBuilder evidencePackBuilder,
                               ContextBudgetManager budgetManager,
                               MainAgentStateViewBuilder stateViewBuilder) {
        this(selectionValidator, artifactPayloadLoader, evidencePackBuilder, budgetManager, stateViewBuilder, null, null);
    }

    public ContextMaterializer(ContextSelectionValidator selectionValidator,
                               ArtifactPayloadLoader artifactPayloadLoader,
                               EvidencePackBuilder evidencePackBuilder,
                               ContextBudgetManager budgetManager,
                               MainAgentStateViewBuilder stateViewBuilder,
                               ITurnRepository turnRepository,
                               IPayloadRepository payloadRepository) {
        this.selectionValidator = selectionValidator;
        this.selectionMergePolicy = new ContextSelectionMergePolicy();
        this.artifactPayloadLoader = artifactPayloadLoader;
        this.evidencePackBuilder = evidencePackBuilder;
        this.budgetManager = budgetManager;
        this.stateViewBuilder = stateViewBuilder;
        this.turnRepository = turnRepository;
        this.payloadRepository = payloadRepository;
    }

    public MainAgentStateViewVO materialize(ContextMaterializationCommand command) {
        List<ContextSelectionVO> selections = command.getForcedSelections() == null
                ? List.of()
                : selectionValidator.validate(command.getForcedSelections(), command.getCandidates());
        List<ContextSelectionVO> mergedSelections = selectionMergePolicy.merge(selections, command.getCandidates());
        TokenBudgetVO budget = command.getTokenBudget() == null ? command.getCandidates().getTokenBudget() : command.getTokenBudget();
        int maxInlineChars = budget.getMaxArtifactInlineChars() == null ? 4000 : budget.getMaxArtifactInlineChars();

        List<MaterializedArtifactContentVO> artifacts = mergedSelections.stream()
                .filter(selection -> "ARTIFACT".equals(selection.getSourceType()) || "ARTIFACT_CHUNK".equals(selection.getSourceType()))
                .sorted(Comparator.comparing(ContextSelectionVO::getPriority, Comparator.nullsLast(Integer::compareTo)))
                .map(selection -> findArtifact(selection, command))
                .filter(Objects::nonNull)
                .map(artifact -> artifactPayloadLoader.load(artifact, levelFor(artifact, mergedSelections), maxInlineChars))
                .filter(Objects::nonNull)
                .toList();

        List<MaterializedMemoryVO> memories = materializedMemories(command.getCandidates(), mergedSelections);
        List<MaterializedRagVO> ragPack = materializedRag(command.getCandidates(), mergedSelections);

        List<MaterializedEvidenceVO> evidence = evidencePackBuilder.buildFromCandidates(
                command.getCandidates().getEvidenceCandidates() == null ? List.of() :
                        command.getCandidates().getEvidenceCandidates().stream()
                                .filter(item -> selectedAny(item.getEvidenceId(), mergedSelections, "EVIDENCE", "RAG", "RAG_CHUNK", "RAG_DOCUMENT"))
                                .toList());
        List<MessageCandidateVO> materializedMessages = materializedSummaryMessages(command.getCandidates().getSessionSummaries(), mergedSelections, command.getCandidates());
        Set<String> materializedTurnIds = materializedMessages.stream()
                .map(MessageCandidateVO::getTurnId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<SummaryCandidateVO> summaries = selectedSummaries(command.getCandidates().getSessionSummaries(), mergedSelections, command.getCandidates(), materializedTurnIds);

        MainAgentStateViewVO stateView = stateViewBuilder.build(MainAgentStateViewBuildCommand.builder()
                .candidates(command.getCandidates())
                .selections(mergedSelections)
                .conversationSummaries(summaries)
                .materializedMessages(materializedMessages)
                .artifactContent(artifacts)
                .memoryPack(memories)
                .ragPack(ragPack)
                .evidencePack(evidence)
                .tokenBudget(budget)
                .build());
        return budgetManager.shrinkToFit(stateView, budget);
    }

    private ArtifactCandidateVO findArtifact(ContextSelectionVO selection, ContextMaterializationCommand command) {
        if (command.getCandidates().getArtifactCandidates() == null) {
            return null;
        }
        String sourceId = selection.getSourceId();
        if ("ARTIFACT_CHUNK".equals(selection.getSourceType())) {
            return command.getCandidates().getArtifactCandidates().stream()
                    .filter(artifact -> artifact.getMatchedChunks() != null)
                    .filter(artifact -> artifact.getMatchedChunks().stream()
                            .anyMatch(chunk -> sourceId.equals(chunk.getChunkId()) || sourceId.equals(chunk.getSourceId())))
                    .findFirst()
                    .orElse(null);
        }
        return command.getCandidates().getArtifactCandidates().stream()
                .filter(artifact -> sourceId.equals(artifact.getArtifactId()))
                .findFirst()
                .orElse(null);
    }

    private ContextLevelEnumVO levelFor(ArtifactCandidateVO artifact, List<ContextSelectionVO> selections) {
        return selections.stream()
                .filter(selection -> "ARTIFACT".equals(selection.getSourceType()) || "ARTIFACT_CHUNK".equals(selection.getSourceType()))
                .filter(selection -> artifact.getArtifactId().equals(selection.getSourceId()) || hasSelectedChunk(artifact, selection.getSourceId()))
                .map(ContextSelectionVO::getContextLevel)
                .findFirst()
                .orElse(ContextLevelEnumVO.SUMMARY_ONLY);
    }

    private boolean hasSelectedChunk(ArtifactCandidateVO artifact, String sourceId) {
        return artifact.getMatchedChunks() != null && artifact.getMatchedChunks().stream()
                .anyMatch(chunk -> sourceId.equals(chunk.getChunkId()) || sourceId.equals(chunk.getSourceId()));
    }

    private boolean selected(String sourceType, String sourceId, List<ContextSelectionVO> selections) {
        if (sourceType == null || sourceId == null) {
            return false;
        }
        return selections.stream().anyMatch(selection -> sourceType.equals(selection.getSourceType()) && sourceId.equals(selection.getSourceId()));
    }

    private boolean selectedAny(String sourceId, List<ContextSelectionVO> selections, String... sourceTypes) {
        for (String sourceType : sourceTypes) {
            if (selected(sourceType, sourceId, selections)) {
                return true;
            }
        }
        return false;
    }

    private List<SummaryCandidateVO> selectedSummaries(List<SummaryCandidateVO> summaries, List<ContextSelectionVO> selections, ContextCandidateBundleVO candidates, Set<String> materializedTurnIds) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }
        return summaries.stream()
                .filter(summary -> selected("TURN_SUMMARY", summary.getSummaryId(), selections)
                        || selected("SESSION_SUMMARY", summary.getSummaryId(), selections)
                        || selected("SUMMARY", summary.getSummaryId(), selections)
                        || selected("TURN", summary.getTurnId(), selections))
                .filter(summary -> !selectionMergePolicy.coveredByFixedContext(summary, candidates))
                .filter(summary -> summary.getTurnId() == null || materializedTurnIds == null || !materializedTurnIds.contains(summary.getTurnId()))
                .toList();
    }

    private List<MessageCandidateVO> materializedSummaryMessages(List<SummaryCandidateVO> summaries,
                                                                 List<ContextSelectionVO> selections,
                                                                 ContextCandidateBundleVO candidates) {
        if (summaries == null || summaries.isEmpty() || selections == null || selections.isEmpty()
                || turnRepository == null || payloadRepository == null) {
            return List.of();
        }
        return summaries.stream()
                .filter(summary -> summary != null && summary.getTurnId() != null)
                .filter(summary -> !selectionMergePolicy.coveredByFixedContext(summary, candidates))
                .filter(summary -> selectedFullTextSummary(summary, selections))
                .map(this::loadTurnMessages)
                .flatMap(List::stream)
                .toList();
    }

    private boolean selectedFullTextSummary(SummaryCandidateVO summary, List<ContextSelectionVO> selections) {
        return selections.stream()
                .anyMatch(selection -> selection != null
                        && selection.getContextLevel() == ContextLevelEnumVO.FULL_TEXT
                        && matchesSummarySelection(summary, selection));
    }

    private boolean matchesSummarySelection(SummaryCandidateVO summary, ContextSelectionVO selection) {
        String sourceType = selection.getSourceType();
        String sourceId = selection.getSourceId();
        if ("TURN_SUMMARY".equals(sourceType) || "SESSION_SUMMARY".equals(sourceType) || "SUMMARY".equals(sourceType)) {
            return Objects.equals(summary.getSummaryId(), sourceId);
        }
        if ("TURN".equals(sourceType)) {
            return Objects.equals(summary.getTurnId(), sourceId);
        }
        return false;
    }

    private List<MessageCandidateVO> loadTurnMessages(SummaryCandidateVO summary) {
        return turnRepository.findByTurnId(summary.getTurnId())
                .map(turn -> {
                    MessageCandidateVO user = toTurnMessage(turn, turn.getUserMessageId(), "USER", turn.getUserPayloadRef());
                    MessageCandidateVO assistant = toTurnMessage(turn, turn.getAssistantMessageId(), "ASSISTANT", turn.getAssistantPayloadRef());
                    return Stream.of(user, assistant).filter(Objects::nonNull).toList();
                })
                .orElse(List.of());
    }

    private MessageCandidateVO toTurnMessage(AgentTurnEntity turn, String messageId, String role, String payloadRef) {
        if (turn == null || messageId == null || messageId.isBlank()) {
            return null;
        }
        String content = loadPayloadText(payloadRef);
        if (content == null || content.isBlank()) {
            return null;
        }
        return MessageCandidateVO.builder()
                .messageId(messageId)
                .turnId(turn.getTurnId())
                .role(role)
                .contentRef(payloadRef)
                .summary(content)
                .seq(turn.getTurnNo())
                .createdAt(turn.getCompletedAt())
                .build();
    }

    private String loadPayloadText(String payloadRef) {
        if (payloadRef == null || payloadRef.isBlank() || payloadRepository == null) {
            return null;
        }
        return payloadRepository.findPayload(payloadRef)
                .map(AgentPayloadEntity::getContent)
                .filter(content -> content != null && !content.isBlank())
                .orElse(null);
    }

    private List<MaterializedMemoryVO> materializedMemories(ContextCandidateBundleVO candidates, List<ContextSelectionVO> selections) {
        if (candidates == null || candidates.getMemoryCandidates() == null) {
            return List.of();
        }
        return candidates.getMemoryCandidates().stream()
                .filter(memory -> memory != null && memory.getMemoryId() != null)
                .filter(memory -> selections != null && !selections.isEmpty()
                        && selectedAny(memory.getMemoryId(), selections, "MEMORY", "LONG_TERM_MEMORY", "USER_PREFERENCE"))
                .map(this::toMemory)
                .toList();
    }

    private List<MaterializedRagVO> materializedRag(ContextCandidateBundleVO candidates, List<ContextSelectionVO> selections) {
        if (candidates == null || candidates.getRagCandidates() == null || selections == null || selections.isEmpty()) {
            return List.of();
        }
        return candidates.getRagCandidates().stream()
                .filter(candidate -> candidate != null && selectedRag(candidate, selections))
                .map(candidate -> toRag(candidate, selections))
                .toList();
    }

    private boolean selectedRag(RagCandidateVO candidate, List<ContextSelectionVO> selections) {
        return selections.stream().anyMatch(selection -> matchesRag(candidate, selection));
    }

    private boolean matchesRag(RagCandidateVO candidate, ContextSelectionVO selection) {
        if (candidate == null || selection == null) {
            return false;
        }
        String sourceType = selection.getSourceType();
        String sourceId = selection.getSourceId();
        if (!isRagSourceType(sourceType)) {
            return false;
        }
        return Objects.equals(sourceId, candidate.getCandidateId())
                || Objects.equals(sourceId, candidate.getDocumentId())
                || Objects.equals(sourceId, candidate.getChunkId());
    }

    private MaterializedRagVO toRag(RagCandidateVO candidate, List<ContextSelectionVO> selections) {
        ContextLevelEnumVO level = ragLevel(candidate, selections);
        String content = null;
        if (level == ContextLevelEnumVO.FULL_TEXT || level == ContextLevelEnumVO.CHUNKED_CONTEXT) {
            content = loadPayloadText(firstNonBlank(candidate.getContentRef(), candidate.getRetrievalTextRef()));
        }
        return MaterializedRagVO.builder()
                .candidateId(candidate.getCandidateId())
                .sourceType(candidate.getSourceType())
                .documentId(candidate.getDocumentId())
                .chunkId(candidate.getChunkId())
                .title(candidate.getTitle())
                .summary(candidate.getSummary())
                .boundedSnippet(candidate.getSnippet())
                .content(content)
                .injectMode(firstNonBlank(candidate.getInjectMode(), defaultInjectMode(candidate, level)))
                .codeMeta(candidate.getCodeMeta())
                .contextLevel(level)
                .build();
    }

    private boolean isRagSourceType(String sourceType) {
        return "RAG_DOCUMENT".equals(sourceType)
                || "RAG_CHUNK".equals(sourceType)
                || "RAG_FILE_CHUNK".equals(sourceType)
                || "RAG_CODE_FILE_SUMMARY".equals(sourceType)
                || "RAG_CODE_CHUNK".equals(sourceType);
    }

    private String defaultInjectMode(RagCandidateVO candidate, ContextLevelEnumVO level) {
        if (candidate == null) {
            return level == null ? null : level.name();
        }
        if ("RAG_FILE_CHUNK".equals(candidate.getSourceType()) || "RAG_CODE_CHUNK".equals(candidate.getSourceType())) {
            return "CHUNK_TEXT";
        }
        if ("RAG_CODE_FILE_SUMMARY".equals(candidate.getSourceType())) {
            return level == ContextLevelEnumVO.FULL_TEXT ? "FULL_FILE" : "SUMMARY_ONLY";
        }
        return level == null ? null : level.name();
    }

    private ContextLevelEnumVO ragLevel(RagCandidateVO candidate, List<ContextSelectionVO> selections) {
        return selections.stream()
                .filter(selection -> matchesRag(candidate, selection))
                .map(ContextSelectionVO::getContextLevel)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(ContextLevelEnumVO.SUMMARY_ONLY);
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private MaterializedMemoryVO toMemory(MemoryCandidateVO memory) {
        return MaterializedMemoryVO.builder()
                .memoryId(memory.getMemoryId())
                .memoryType(memory.getMemoryType())
                .summary(memory.getSummary())
                .content(memory.getContent())
                .build();
    }
}
