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
public class AgentRunContextPO {
    private Long id;
    private String runId;
    private Integer schemaVersion;
    private String mainAgentStage;
    private String baseContextRef;
    private String taskLedgerRef;
    private String runtimeControlRef;
    private Long contextVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
