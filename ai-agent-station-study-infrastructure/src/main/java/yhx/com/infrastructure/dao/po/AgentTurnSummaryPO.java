package yhx.com.infrastructure.dao.po;

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
public class AgentTurnSummaryPO {

    private Long id;
    private String summaryId;
    private String turnId;
    private String sessionId;
    private String runId;
    private String userId;
    private String summaryRef;
    private String intent;
    private String topicsJson;
    private String entitiesJson;
    private String artifactRefsJson;
    private BigDecimal importanceScore;
    private Integer requiresLongTermExtraction;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
