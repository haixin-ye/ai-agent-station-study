package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentStageEnumVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunContextStateVO {
    private Integer schemaVersion;
    private Long contextVersion;
    private MainAgentStageEnumVO mainAgentStage;
    private RunBaseContextVO baseContext;
    private TaskLedgerVO taskLedger;
    private RunRuntimeControlVO runtimeControl;
    private List<RunLoopRecordVO> loopTimeline;
}
