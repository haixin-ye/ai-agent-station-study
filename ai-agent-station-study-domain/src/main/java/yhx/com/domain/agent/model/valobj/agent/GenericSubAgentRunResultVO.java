package yhx.com.domain.agent.model.valobj.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericSubAgentRunResultVO {

    private String parentRunId;
    private String childRunId;
    private String taskId;
    private ChildAgentRunStatusEnumVO status;
    private SubAgentCommitVO commit;
    private String failureMessage;
    private String pendingInputId;
    private AskUserRequestVO askUserRequest;
    private Integer loopCount;
    private SubAgentFullContextVO fullContext;
}
