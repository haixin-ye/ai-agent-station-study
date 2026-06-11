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
public class AgentConversationSummaryEntity {

    private String summaryId;
    private String sessionId;
    private String userId;
    private String summaryRef;
    private Long messageStartSeq;
    private Long messageEndSeq;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
