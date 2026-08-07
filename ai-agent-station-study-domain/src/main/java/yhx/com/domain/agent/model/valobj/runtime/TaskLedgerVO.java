package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskLedgerVO {
    private Long version;
    private String goal;
    private List<TaskDeliverableVO> deliverables;
    private List<TaskStepVO> steps;
    private String currentStepId;
    private List<TaskPlanRevisionVO> planRevisions;
    private Map<String, Object> facts;
    private List<String> blockers;
    private String lastDecision;
}
