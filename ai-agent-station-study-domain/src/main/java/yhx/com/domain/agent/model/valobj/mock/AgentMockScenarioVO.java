package yhx.com.domain.agent.model.valobj.mock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMockScenarioVO {

    private String scenario;
    private String title;
    private String description;
    private Boolean debugScenario;
}

