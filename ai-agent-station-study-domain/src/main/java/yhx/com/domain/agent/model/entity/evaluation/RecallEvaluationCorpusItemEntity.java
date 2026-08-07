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
public class RecallEvaluationCorpusItemEntity {
    private String corpusItemId;
    private String datasetId;
    private String externalId;
    private String itemType;
    private String title;
    private String summary;
    private String contentRef;
    private String tagsJson;
    private String sourceType;
    private String sourceId;
    private String parentSourceId;
    private String sourceRefsJson;
    private String status;
    private String failureStage;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
