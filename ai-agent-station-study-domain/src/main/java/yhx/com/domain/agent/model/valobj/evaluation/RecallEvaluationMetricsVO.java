package yhx.com.domain.agent.model.valobj.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallEvaluationMetricsVO {
    private Integer evaluatedCaseCount;
    private Integer failedCaseCount;
    private Double hitRateAtK;
    private Double precisionAtK;
    private Double recallAtK;
    private Double meanReciprocalRank;
    private Double ndcgAtK;
    private Double mapAtK;
    private Double noHitRate;
    private Long retrievalLatencyAverageMs;
    private Long retrievalLatencyP50Ms;
    private Long retrievalLatencyP95Ms;
    private Integer plannerInvocationCount;
    private Double plannerPrecision;
    private Double plannerRecall;
    private Double plannerHitRateAtK;
    private Double plannerMeanReciprocalRank;
    private Double plannerNdcgAtK;
    private Double plannerAverageSelectedCount;
    private Double plannerRelevantRetentionRate;
    private Double plannerIrrelevantRemovalRate;
    private Integer plannerRelevantDroppedCount;
    private Double clarificationRate;
    private Double plannerFailureRate;
    private Long plannerLatencyAverageMs;
    private Long plannerLatencyP50Ms;
    private Long plannerLatencyP95Ms;
}
