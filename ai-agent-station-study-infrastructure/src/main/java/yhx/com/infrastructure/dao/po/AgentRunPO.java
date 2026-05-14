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
public class AgentRunPO {

    private Long id;
    private String runId;
    private String sessionId;
    private String userId;
    private String agentId;
    private String status;
    private String phase;
    private Integer ragWasUsed;
    private String finalMessageId;
    private String finalAnswerRef;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
