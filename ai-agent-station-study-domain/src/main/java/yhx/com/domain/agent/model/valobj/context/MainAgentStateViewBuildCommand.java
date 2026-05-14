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
    private List<MaterializedArtifactContentVO> artifactContent;
    private List<MaterializedMemoryVO> memoryPack;
    private List<MaterializedEvidenceVO> evidencePack;
    private TokenBudgetVO tokenBudget;
    private FailureVO failure;
}
