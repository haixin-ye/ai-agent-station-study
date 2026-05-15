package yhx.com.domain.agent.model.valobj.finalresponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalRepairPromptContextVO {

    private String runId;
    private String agentId;
    private Integer loopIndex;
    private String userInput;
    private FinalAnswerCandidateVO failedCandidate;
    private String failureCode;
    private String guardSummary;
    private String repairInstruction;
}
