package yhx.com.domain.agent.model.valobj.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.entity.evaluation.RecallEvaluationCorpusItemEntity;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallCorpusBatchActionResultVO {
    private Integer succeededCount;
    private Integer failedCount;
    private List<RecallEvaluationCorpusItemEntity> items;
    private List<String> errors;
}
