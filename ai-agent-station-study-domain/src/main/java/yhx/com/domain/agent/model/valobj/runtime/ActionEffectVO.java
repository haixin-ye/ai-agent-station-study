package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.context.PreviousLoopOutcomeVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionEffectVO {

    private String action;
    private String status;
    private String message;
    private Integer loopIndex;
    private Map<String, Object> toolIntent;
    private String workId;
    private String repeatGuardKey;
    private String resultRef;
    private Map<String, Object> requestSnapshot;
    private Map<String, Object> resultSnapshot;
    private List<String> createdEvidenceIds;
    private List<MaterializedEvidenceVO> createdEvidence;
    private List<String> createdArtifactIds;
    private List<UserClarificationVO> userClarifications;
    private PreviousLoopOutcomeVO previousLoopOutcome;
}
