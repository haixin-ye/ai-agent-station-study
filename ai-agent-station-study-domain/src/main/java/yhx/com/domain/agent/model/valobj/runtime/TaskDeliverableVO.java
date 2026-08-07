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
public class TaskDeliverableVO {
    private String deliverableId;
    private String description;
    private List<String> acceptanceCriteria;
    private String status;
    private List<String> relatedStepIds;
    private List<String> evidenceRefs;
    private List<String> payloadRefs;
}
