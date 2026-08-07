package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunContextEnvelopeVO {
    private RunBaseContextVO runBaseContext;
    private TaskLedgerVO taskLedger;
    private List<RunLoopRecordVO> loopTimeline;
    private Map<String, Object> runtimeControl;
    private Map<String, Object> payloadManifest;
    private Map<String, Object> activePayloads;
}
