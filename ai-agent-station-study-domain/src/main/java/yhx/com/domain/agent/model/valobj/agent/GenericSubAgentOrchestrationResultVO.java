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
public class GenericSubAgentOrchestrationResultVO {

    private String parentRunId;
    private String childRunId;
    private String taskId;
    private ChildAgentRunStatusEnumVO childStatus;
    private Boolean parentReady;
    private GenericSubAgentRunResultVO childRunResult;

    public boolean isParentReady() {
        return Boolean.TRUE.equals(parentReady);
    }
}
