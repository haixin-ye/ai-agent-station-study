package yhx.com.domain.agent.model.entity.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunLoopEntity {
    private String runId;
    private Integer loopIndex;
    private MainAgentStageEnumVO mainAgentStage;
    private String status;
    private String recordRef;
    private Long recordVersion;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
