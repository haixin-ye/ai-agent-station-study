package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.context.PreviousLoopOutcomeVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunWorkingStateVO {

    private MainAgentStateViewVO baseStateView;
    private List<ActionEffectVO> actionHistory;
    private List<MaterializedEvidenceVO> evidencePack;
    private List<UserClarificationVO> userClarifications;
    private PreviousLoopOutcomeVO previousLoopOutcome;
}
