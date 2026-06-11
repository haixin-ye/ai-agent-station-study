package yhx.com.domain.agent.model.valobj.finalresponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.runtime.FinalAnswerCandidateVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalResponseGuardInputVO {

    private String runId;
    private String sessionId;
    private Integer loopIndex;
    private FinalAnswerCandidateVO candidate;
    private List<String> evidenceRefs;
    private List<String> verifiedToolCallRefs;
    private String userFormatRequirement;
    private Integer maxOutputChars;
    private Boolean userAskedForInternals;
}
