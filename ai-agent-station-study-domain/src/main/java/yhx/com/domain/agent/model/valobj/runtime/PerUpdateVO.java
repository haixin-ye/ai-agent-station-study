package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerUpdateVO {

    private String mode;
    private String goal;
    private List<PerStepUpdateVO> stepUpdates;
    private List<NotebookFactVO> factsLearned;
    private List<NotebookQuestionVO> openQuestions;
    private List<NotebookRiskVO> risks;
    private String nextStepId;
    private String lastDecision;
    private Map<String, Object> metadata;
}
