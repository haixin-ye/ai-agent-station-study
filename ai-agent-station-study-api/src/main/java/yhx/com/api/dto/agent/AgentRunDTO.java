package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String runId;
    private String sessionId;
    private String userId;
    private String agentId;
    private String status;
    private String currentPhase;
    private Integer loopIndex;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}

