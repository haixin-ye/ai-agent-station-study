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
public class MainAgentStateViewVO {

    private RunMetaVO runMeta;
    private UserInputVO userInput;
    private ConversationViewVO conversation;
    private List<MaterializedMemoryVO> memoryPack;
    private List<MaterializedRagVO> ragPack;
    private List<ArtifactCandidateVO> resolvedArtifacts;
    private List<MaterializedArtifactContentVO> artifactContent;
    private List<MaterializedEvidenceVO> evidencePack;
    private List<UserClarificationVO> userClarifications;
    private List<CapabilityCandidateVO> availableCapabilities;
    private PendingActionViewVO pendingAction;
    private PreviousLoopOutcomeVO previousLoopOutcome;
    private Object currentPlan;
    private VerifierFeedbackViewVO lastVerifierFeedback;
    private String outputContractVersion;
    private TokenBudgetVO tokenBudget;
    private FailureVO failure;
}
