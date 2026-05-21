package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;
import yhx.com.domain.agent.model.valobj.context.ContextMaterializationCommand;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewBuildCommand;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedArtifactContentVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedMemoryVO;
import yhx.com.domain.agent.model.valobj.context.MemoryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.SummaryCandidateVO;
import yhx.com.domain.agent.model.valobj.context.TokenBudgetVO;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;
import yhx.com.domain.agent.service.artifact.ArtifactPayloadLoader;
import yhx.com.domain.agent.service.evidence.EvidencePackBuilder;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class ContextMaterializer {

    private final ContextSelectionValidator selectionValidator;
    private final ArtifactPayloadLoader artifactPayloadLoader;
    private final EvidencePackBuilder evidencePackBuilder;
    private final ContextBudgetManager budgetManager;
    private final MainAgentStateViewBuilder stateViewBuilder;

    public ContextMaterializer(ContextSelectionValidator selectionValidator,
                               ArtifactPayloadLoader artifactPayloadLoader,
                               EvidencePackBuilder evidencePackBuilder,
                               ContextBudgetManager budgetManager,
                               MainAgentStateViewBuilder stateViewBuilder) {
        this.selectionValidator = selectionValidator;
        this.artifactPayloadLoader = artifactPayloadLoader;
        this.evidencePackBuilder = evidencePackBuilder;
        this.budgetManager = budgetManager;
        this.stateViewBuilder = stateViewBuilder;
    }

    public MainAgentStateViewVO materialize(ContextMaterializationCommand command) {
        List<ContextSelectionVO> selections = command.getForcedSelections() == null
                ? List.of()
                : selectionValidator.validate(command.getForcedSelections(), command.getCandidates());
        TokenBudgetVO budget = command.getTokenBudget() == null ? command.getCandidates().getTokenBudget() : command.getTokenBudget();
        int maxInlineChars = budget.getMaxArtifactInlineChars() == null ? 4000 : budget.getMaxArtifactInlineChars();

        List<MaterializedArtifactContentVO> artifacts = selections.stream()
                .filter(selection -> "ARTIFACT".equals(selection.getSourceType()))
                .sorted(Comparator.comparing(ContextSelectionVO::getPriority, Comparator.nullsLast(Integer::compareTo)))
                .map(selection -> findArtifact(selection.getSourceId(), command))
                .filter(Objects::nonNull)
                .map(artifact -> artifactPayloadLoader.load(artifact, levelFor(artifact, selections), maxInlineChars))
                .filter(Objects::nonNull)
                .toList();

        List<MaterializedMemoryVO> memories = command.getCandidates().getMemoryCandidates() == null ? List.of() :
                command.getCandidates().getMemoryCandidates().stream()
                        .filter(memory -> selected("MEMORY", memory.getMemoryId(), selections))
                        .map(this::toMemory)
                        .toList();

        List<MaterializedEvidenceVO> evidence = evidencePackBuilder.buildFromCandidates(
                command.getCandidates().getEvidenceCandidates() == null ? List.of() :
                        command.getCandidates().getEvidenceCandidates().stream()
                                .filter(item -> selected("EVIDENCE", item.getEvidenceId(), selections))
                                .toList());
        List<SummaryCandidateVO> summaries = selectedSummaries(command.getCandidates().getSessionSummaries(), selections);

        MainAgentStateViewVO stateView = stateViewBuilder.build(MainAgentStateViewBuildCommand.builder()
                .candidates(command.getCandidates())
                .selections(selections)
                .conversationSummaries(summaries)
                .artifactContent(artifacts)
                .memoryPack(memories)
                .evidencePack(evidence)
                .tokenBudget(budget)
                .build());
        return budgetManager.shrinkToFit(stateView, budget);
    }

    private ArtifactCandidateVO findArtifact(String artifactId, ContextMaterializationCommand command) {
        if (command.getCandidates().getArtifactCandidates() == null) {
            return null;
        }
        return command.getCandidates().getArtifactCandidates().stream()
                .filter(artifact -> artifactId.equals(artifact.getArtifactId()))
                .findFirst()
                .orElse(null);
    }

    private ContextLevelEnumVO levelFor(ArtifactCandidateVO artifact, List<ContextSelectionVO> selections) {
        return selections.stream()
                .filter(selection -> "ARTIFACT".equals(selection.getSourceType()))
                .filter(selection -> artifact.getArtifactId().equals(selection.getSourceId()))
                .map(ContextSelectionVO::getContextLevel)
                .findFirst()
                .orElse(ContextLevelEnumVO.SUMMARY_ONLY);
    }

    private boolean selected(String sourceType, String sourceId, List<ContextSelectionVO> selections) {
        return selections.stream().anyMatch(selection -> sourceType.equals(selection.getSourceType()) && sourceId.equals(selection.getSourceId()));
    }

    private List<SummaryCandidateVO> selectedSummaries(List<SummaryCandidateVO> summaries, List<ContextSelectionVO> selections) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }
        return summaries.stream()
                .filter(summary -> selected("TURN_SUMMARY", summary.getSummaryId(), selections)
                        || selected("SESSION_SUMMARY", summary.getSummaryId(), selections)
                        || selected("SUMMARY", summary.getSummaryId(), selections)
                        || selected("TURN", summary.getTurnId(), selections))
                .toList();
    }

    private MaterializedMemoryVO toMemory(MemoryCandidateVO memory) {
        return MaterializedMemoryVO.builder()
                .memoryId(memory.getMemoryId())
                .memoryType(memory.getMemoryType())
                .summary(memory.getSummary())
                .build();
    }
}
