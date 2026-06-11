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
public class PerStepUpdateVO {

    private String stepId;
    private String title;
    private String status;
    private String note;
    private List<String> relatedWorkIds;
    private List<String> relatedEvidenceIds;
    private Integer createdLoopIndex;
    private Integer updatedLoopIndex;
    private Long createdSequence;
    private Long updatedSequence;
    private Map<String, Object> metadata;
}
