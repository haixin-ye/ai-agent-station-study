package yhx.com.domain.agent.model.valobj.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallCaseMetricsVO {
    private Boolean hit;
    private Double precisionAtK;
    private Double recallAtK;
    private Double reciprocalRank;
    private Double ndcgAtK;
    private Double averagePrecisionAtK;
    private Long retrievalLatencyMs;
    private Long plannerLatencyMs;
    private Double plannerPrecision;
    private Double plannerRecall;
    private Boolean plannerHit;
    private Double plannerReciprocalRank;
    private Double plannerNdcgAtK;
    private Integer plannerSelectedCount;
    private Double plannerRelevantRetentionRate;
    private Double plannerIrrelevantRemovalRate;
    private Integer plannerRelevantDroppedCount;
    private Boolean plannerInvoked;
    private Boolean clarificationRequested;
    private Boolean plannerFailed;
}
