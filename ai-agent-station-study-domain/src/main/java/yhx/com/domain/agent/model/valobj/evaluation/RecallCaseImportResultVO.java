package yhx.com.domain.agent.model.valobj.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseEntity;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallCaseImportResultVO {
    private Integer acceptedCount;
    private Integer failedCount;
    private List<RecallEvaluationCaseEntity> cases;
    private List<String> errors;
}
