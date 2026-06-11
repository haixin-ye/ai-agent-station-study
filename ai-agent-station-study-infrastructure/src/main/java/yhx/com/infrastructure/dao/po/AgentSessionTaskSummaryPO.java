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
public class AgentSessionTaskSummaryPO {

    private Long id;
    private String summaryId;
    private String sessionId;
    private String userId;
    private String summaryRef;
    private Integer versionNo;
    private Integer sourceTurnCount;
    private String sourceLatestTurnId;
    private Long sourceLatestTurnNo;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
