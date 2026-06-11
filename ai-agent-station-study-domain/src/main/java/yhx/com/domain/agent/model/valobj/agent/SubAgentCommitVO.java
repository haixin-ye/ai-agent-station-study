package yhx.com.domain.agent.model.valobj.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentCommitVO {

    private String taskId;
    private String status;
    private String result;
    private String detail;
    private List<String> evidenceRefs;
    private List<String> inspectedResources;
    private List<String> assumptions;
    private List<String> blockers;
    private String suggestedParentNextStep;
    private Boolean safeForUserVisibleUse;
}
