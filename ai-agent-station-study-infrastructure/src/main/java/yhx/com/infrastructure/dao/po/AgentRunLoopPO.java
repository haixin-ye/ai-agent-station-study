package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunLoopPO {
    private Long id;
    private String runId;
    private Integer loopIndex;
    private String mainAgentStage;
    private String status;
    private String recordRef;
    private Long recordVersion;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
