package yhx.com.domain.agent.model.valobj.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallEvaluationRunConfigVO {
    private String datasetId;
    private String name;
    private String sourceScope;
    private Integer topK;
    private Double minScore;
    private String retrievalMode;
    private List<String> collectionTypes;
    private Boolean plannerEnabled;
    private Integer caseLimit;
    private Long caseTimeoutMs;
}
