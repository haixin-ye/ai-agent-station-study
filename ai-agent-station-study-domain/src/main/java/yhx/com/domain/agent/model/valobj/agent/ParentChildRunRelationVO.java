package yhx.com.domain.agent.model.valobj.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentChildRunRelationVO {

    private String parentRunId;
    private String childRunId;
    private String taskId;
    private String childName;
    private String dispatchBatchId;
    private String waitMode;
    private ChildAgentRunStatusEnumVO status;
    private SubAgentCommitVO commit;
    private String failureMessage;
    private String pendingInputId;
    private String fullContextSnapshotRef;
}
