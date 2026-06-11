package yhx.com.domain.agent.model.valobj.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionTaskSummaryOutputVO {

    private Boolean shouldUpdate;
    private List<String> mainTasks;
    private String currentTask;
    private List<String> importantDecisions;
    private List<String> latestProgress;
    private List<String> openQuestions;
    private List<String> obsoleteTasks;
}
