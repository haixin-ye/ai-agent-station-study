package yhx.com.domain.agent.model.valobj.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericSubAgentContinuationVO {

    private String parentRunId;
    private String childRunId;
    private String taskId;
    private GenericSubAgentRunCommandVO command;
    private SubAgentFullContextVO fullContext;
    private String fullContextSnapshotRef;
    private Integer loopCount;
    private String pendingInputId;
}
