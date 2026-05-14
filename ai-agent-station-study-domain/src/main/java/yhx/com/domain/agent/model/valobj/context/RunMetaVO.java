package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunMetaVO {

    private String runId;
    private String sessionId;
    private String userId;
    private String agentId;
    private Integer loopIndex;
}
