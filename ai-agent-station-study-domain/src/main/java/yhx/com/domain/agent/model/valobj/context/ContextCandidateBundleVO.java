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
public class ContextCandidateBundleVO {

    private RunMetaVO runMeta;
    private UserInputVO userInput;
    private List<MessageCandidateVO> recentMessages;
    private List<SummaryCandidateVO> sessionSummaries;
    private List<ArtifactCandidateVO> artifactCandidates;
    private List<MemoryCandidateVO> memoryCandidates;
    private List<EvidenceCandidateVO> evidenceCandidates;
    private List<CapabilityCandidateVO> availableCapabilities;
    private PendingActionViewVO pendingAction;
    private TokenBudgetVO tokenBudget;
}
