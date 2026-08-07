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
public class RecallEvaluationCaseEntity {
    private String caseId;
    private String datasetId;
    private String externalId;
    private String queryText;
    private String sourceScope;
    private String expectedJson;
    private String tagsJson;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
