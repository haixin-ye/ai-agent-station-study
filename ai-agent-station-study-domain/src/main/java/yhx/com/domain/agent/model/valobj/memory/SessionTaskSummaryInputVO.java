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
public class SessionTaskSummaryInputVO {

    private String runId;
    private String sessionId;
    private String userId;
    private String previousTaskSummary;
    private List<SessionTaskSummaryItemVO> summaries;
}
