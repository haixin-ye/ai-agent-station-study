package yhx.com.domain.agent.model.entity.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryEntity {

    private String ragQueryId;
    private String runId;
    private String queryText;
    private String knowledgeTag;
    private String filtersRef;
    private Integer topK;
    private String status;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
