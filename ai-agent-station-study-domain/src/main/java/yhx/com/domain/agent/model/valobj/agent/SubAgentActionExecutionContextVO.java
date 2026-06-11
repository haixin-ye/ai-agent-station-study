package yhx.com.domain.agent.model.valobj.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentActionExecutionContextVO {

    private ParentChildRunRelationVO relation;
    private GenericSubAgentRunCommandVO command;
    private SubAgentFullContextVO fullContext;
    private Integer loopIndex;
}
