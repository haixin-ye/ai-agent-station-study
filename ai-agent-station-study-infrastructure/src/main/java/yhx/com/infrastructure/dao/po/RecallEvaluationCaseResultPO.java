package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecallEvaluationCaseResultPO {
    private Long id;
    private String caseResultId;
    private String evaluationRunId;
    private String caseId;
    private String status;
    private Long retrievalLatencyMs;
    private Long plannerLatencyMs;
    private Boolean hit;
    private BigDecimal precisionAtK;
    private BigDecimal recallAtK;
    private BigDecimal reciprocalRank;
    private BigDecimal ndcgAtK;
    private BigDecimal averagePrecisionAtK;
    private String plannerStatus;
    private String plannerReason;
    private String plannerSelectedIdsJson;
    private String plannerOutputJson;
    private String failureStage;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
