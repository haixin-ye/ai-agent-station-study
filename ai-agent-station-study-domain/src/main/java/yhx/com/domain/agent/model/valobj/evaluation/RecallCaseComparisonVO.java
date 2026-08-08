package yhx.com.domain.agent.model.valobj.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallCaseComparisonVO {
    private String caseId;
    private String leftStatus;
    private String rightStatus;
    private Boolean leftHit;
    private Boolean rightHit;
    private Integer leftFirstRelevantRank;
    private Integer rightFirstRelevantRank;
    private Integer rankDelta;
    private String outcome;
}
