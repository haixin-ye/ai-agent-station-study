package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeExecutionContext {

    private String runId;
    private String sessionId;
    private String userId;
    private String agentId;
    private String userMessageId;
    private String userInput;
    private RunStatusEnumVO runStatus;
    private RuntimePhaseEnumVO currentPhase;
    private Integer loopIndex;
    private Integer maxLoop;
    private RuntimeRecoveryCounters recoveryCounters;
    private MainAgentStateViewVO lastStateView;
    private List<ContextSelectionVO> lastContextSelections;
    private MainAgentActionVO lastAction;
    private Map<String, Object> runtimeFacts;

    public RuntimeRecoveryCounters countersOrInitial() {
        if (recoveryCounters == null) {
            recoveryCounters = RuntimeRecoveryCounters.initial();
        }
        return recoveryCounters;
    }
}
