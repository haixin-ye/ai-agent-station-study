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
public class AgentRunContextEntity {
    private String runId;
    private Integer schemaVersion;
    private MainAgentStageEnumVO mainAgentStage;
    private String baseContextRef;
    private String taskLedgerRef;
    private String runtimeControlRef;
    private Long contextVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
