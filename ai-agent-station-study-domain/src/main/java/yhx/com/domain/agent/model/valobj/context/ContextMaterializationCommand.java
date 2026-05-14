package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.invocation.ContextPlannerOutputVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextMaterializationCommand {

    private ContextCandidateBundleVO candidates;
    private ContextPlannerOutputVO plannerOutput;
    private List<ContextSelectionVO> forcedSelections;
    private TokenBudgetVO tokenBudget;
}
