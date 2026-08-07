package yhx.com.domain.agent.model.entity.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallEvaluationHitEntity {
    private String hitId;
    private String evaluationRunId;
    private String caseId;
    private Integer rankNo;
    private String retrievalChannel;
    private String collectionType;
    private String sourceType;
    private String sourceId;
    private String parentSourceId;
    private BigDecimal score;
    private Integer expectedGrade;
    private Boolean selectedByPlanner;
    private String candidateJson;
    private LocalDateTime createdAt;
}
