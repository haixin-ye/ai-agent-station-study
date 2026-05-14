package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentRagQueryPO {
    private Long id;
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
