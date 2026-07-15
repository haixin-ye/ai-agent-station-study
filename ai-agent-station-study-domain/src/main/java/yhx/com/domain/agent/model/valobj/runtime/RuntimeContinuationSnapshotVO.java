package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.ContextSelectionVO;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeContinuationSnapshotVO {

    private String runId;
    private String sessionId;
    private Integer loopIndex;
    private Integer maxLoop;
    private RuntimeRecoveryCounters recoveryCounters;
    private MainAgentStateViewVO lastStateView;
    private RunWorkingStateVO workingState;
    private List<ContextSelectionVO> lastContextSelections;
    private MainAgentActionVO lastAction;
    private Map<String, Object> resumableRuntimeFacts;
}
