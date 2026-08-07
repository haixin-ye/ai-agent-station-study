package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStepVO {
    private String stepId;
    private String description;
    private String status;
    private List<String> dependsOn;
    private List<String> affectedDeliverableIds;
    private List<String> resultRefs;
}
