package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunLoopRecordVO {
    private String runId;
    private Integer loopIndex;
    private MainAgentStageEnumVO mainAgentStage;
    private String status;
    private Long recordVersion;
    private Long taskLedgerVersionBefore;
    private Long taskLedgerVersionAfter;
    private MainAgentActionVO mainOutput;
    private LoopRuntimeOutcomeVO runtimeOutcome;
    private Map<String, Object> userInteraction;
    private List<String> affectedStepIds;
    private List<String> affectedDeliverableIds;
    private String repeatGuardKey;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
