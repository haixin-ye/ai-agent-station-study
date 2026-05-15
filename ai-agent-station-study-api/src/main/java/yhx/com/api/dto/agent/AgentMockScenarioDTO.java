package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMockScenarioDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String scenario;
    private String title;
    private String description;
    private Boolean debugScenario;
}

