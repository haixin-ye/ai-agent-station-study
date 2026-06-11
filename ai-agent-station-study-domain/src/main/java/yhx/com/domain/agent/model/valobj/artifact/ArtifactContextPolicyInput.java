package yhx.com.domain.agent.model.valobj.artifact;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.ArtifactCandidateVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactContextPolicyInput {

    private String userInput;
    private ArtifactCandidateVO artifact;
    private String requestedOperation;
    private Integer maxInlineTokens;
    private Boolean toolWillMaterializeLater;
}
