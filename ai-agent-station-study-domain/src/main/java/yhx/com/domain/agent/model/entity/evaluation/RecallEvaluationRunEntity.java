package yhx.com.domain.agent.model.entity.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallEvaluationRunEntity {
    private String evaluationRunId;
    private String datasetId;
    private String name;
    private String status;
    private String configJson;
    private String metricsJson;
    private Integer totalCaseCount;
    private Integer completedCaseCount;
    private Integer failedCaseCount;
    private Boolean cancelRequested;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
