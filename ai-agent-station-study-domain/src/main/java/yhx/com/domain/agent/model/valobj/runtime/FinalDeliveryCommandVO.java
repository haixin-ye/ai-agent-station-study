package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.FailureVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.MainAgentActionTypeEnumVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalDeliveryCommandVO {

    private String runId;
    private String sessionId;
    private Integer loopIndex;
    private MainAgentActionTypeEnumVO sourceAction;
    private FinalAnswerCandidateVO finalAnswerCandidate;
    private FailureVO failure;
    private List<String> evidenceIds;
}
