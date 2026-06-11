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
public class AgentTurnEntity {

    private String turnId;
    private String sessionId;
    private String runId;
    private String userId;
    private String agentId;
    private Long turnNo;
    private String userMessageId;
    private String assistantMessageId;
    private String userPayloadRef;
    private String assistantPayloadRef;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
