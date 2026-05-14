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
public class ContextPlannerHandlingResult {

    private String nextStep;
    private MainAgentStateViewVO stateView;
    private AskUserRequestVO askUserRequest;
    private FailureVO failure;
    private List<ContextSelectionVO> effectiveSelections;
}
