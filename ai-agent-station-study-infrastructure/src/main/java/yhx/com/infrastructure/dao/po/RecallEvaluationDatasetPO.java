package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecallEvaluationDatasetPO {
    private Long id;
    private String datasetId;
    private String name;
    private String description;
    private String status;
    private String evalUserId;
    private String evalSessionId;
    private Integer corpusCount;
    private Integer readyCorpusCount;
    private Integer caseCount;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
