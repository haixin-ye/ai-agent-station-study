package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeStepStatusEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeStepResult {

    private String runId;
    private String sessionId;
    private RuntimeStepStatusEnumVO status;
    private RuntimePhaseEnumVO nextPhase;
    private RunStatusEnumVO nextRunStatus;
    private MainAgentActionVO action;
    private MainActionHandlerResult actionResult;
    private AskUserRequestVO askUserRequest;
    private RuntimeSafeFailureVO safeFailure;
    private String finalAnswer;
    private String finalMessageId;
    private String pendingInputId;
    private String message;
}
