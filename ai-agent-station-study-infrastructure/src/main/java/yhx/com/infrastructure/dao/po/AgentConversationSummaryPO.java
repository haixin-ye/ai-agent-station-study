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
public class AgentConversationSummaryPO {
    private Long id;
    private String summaryId;
    private String sessionId;
    private String userId;
    private String summaryRef;
    private Long messageStartSeq;
    private Long messageEndSeq;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
