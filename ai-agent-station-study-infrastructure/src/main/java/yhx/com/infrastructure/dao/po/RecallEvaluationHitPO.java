package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecallEvaluationHitPO {
    private Long id;
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
