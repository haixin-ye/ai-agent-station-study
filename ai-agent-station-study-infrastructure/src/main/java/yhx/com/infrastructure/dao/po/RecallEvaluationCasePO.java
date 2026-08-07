package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecallEvaluationCasePO {
    private Long id;
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
