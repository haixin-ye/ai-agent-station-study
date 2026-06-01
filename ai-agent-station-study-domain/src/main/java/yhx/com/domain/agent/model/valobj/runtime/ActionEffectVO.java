package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.context.PreviousLoopOutcomeVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionEffectVO {

    private String action;
    private String status;
    private String message;
    private Integer loopIndex;
    private List<String> createdEvidenceIds;
    private List<MaterializedEvidenceVO> createdEvidence;
    private List<String> createdArtifactIds;
    private List<UserClarificationVO> userClarifications;
    private PreviousLoopOutcomeVO previousLoopOutcome;
}
