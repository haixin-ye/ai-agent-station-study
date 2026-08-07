package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskPlanRevisionVO {
    private Long revisionNo;
    private String reason;
    private List<String> retainedStepIds;
    private List<String> addedStepIds;
    private List<String> cancelledStepIds;
    private Integer loopIndex;
    private LocalDateTime createdAt;
}
