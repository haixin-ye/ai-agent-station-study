package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MainAgentStateViewBuildCommand {

    private ContextCandidateBundleVO candidates;
    private List<ContextSelectionVO> selections;
    private List<SummaryCandidateVO> conversationSummaries;
    private List<MessageCandidateVO> materializedMessages;
    private List<MaterializedArtifactContentVO> artifactContent;
    private List<MaterializedMemoryVO> memoryPack;
    private List<MaterializedRagVO> ragPack;
    private List<MaterializedEvidenceVO> evidencePack;
    private List<UserClarificationVO> userClarifications;
    private PreviousLoopOutcomeVO previousLoopOutcome;
    private TokenBudgetVO tokenBudget;
    private FailureVO failure;
}
