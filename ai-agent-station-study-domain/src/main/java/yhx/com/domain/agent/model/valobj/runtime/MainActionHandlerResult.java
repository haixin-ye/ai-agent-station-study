package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainActionHandlerStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputPauseIntentVO;
import yhx.com.domain.agent.model.valobj.agent.DelegateAgentsRequestVO;
import yhx.com.domain.agent.model.valobj.agent.GenericSubAgentDispatchOrchestrationResultVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MainActionHandlerResult {

    private MainActionHandlerStatusEnumVO status;
    private RuntimePhaseEnumVO nextPhase;
    private AskUserRequestVO askUserRequest;
    private String pendingInputId;
    private PendingInputPauseIntentVO pauseIntent;
    private DelegateAgentsRequestVO deferredAgentRequest;
    private GenericSubAgentDispatchOrchestrationResultVO deferredAgentDispatch;
    private FinalAnswerCandidateVO finalAnswerCandidate;
    private String finalMessageId;
    private String finalAnswerRef;
    private RuntimeSafeFailureVO safeFailure;
    private List<String> createdEvidenceIds;
    private List<MaterializedEvidenceVO> createdEvidence;
    private List<String> createdArtifactIds;
    private ActionEffectVO actionEffect;
    private String message;
}
