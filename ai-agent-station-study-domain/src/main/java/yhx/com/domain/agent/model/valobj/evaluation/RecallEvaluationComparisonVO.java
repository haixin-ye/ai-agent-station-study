package yhx.com.domain.agent.model.valobj.evaluation;

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
public class RecallEvaluationComparisonVO {
    private String leftRunId;
    private String rightRunId;
    private RecallEvaluationRunConfigVO leftConfig;
    private RecallEvaluationRunConfigVO rightConfig;
    private RecallEvaluationMetricsVO leftMetrics;
    private RecallEvaluationMetricsVO rightMetrics;
    private Map<String, Double> metricDeltas;
    private List<RecallCaseComparisonVO> cases;
}
