package yhx.com.domain.agent.model.valobj.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCaseResultEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationHitEntity;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationRunEntity;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallEvaluationRunDetailVO {
    private RecallEvaluationRunEntity run;
    private RecallEvaluationMetricsVO metrics;
    private List<RecallEvaluationCaseResultEntity> results;
    private List<RecallEvaluationHitEntity> hits;
}
