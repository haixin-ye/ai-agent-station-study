package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenBudgetVO {

    private Integer maxStateViewTokens;
    private Integer reservedOutputTokens;
    private Integer currentCandidateTokens;
    private Integer selectedContextTokens;
    private Integer remainingTokens;
    private Integer maxArtifactInlineChars;
    private Integer maxEvidenceSummaryChars;
    private Boolean overBudget;
}
