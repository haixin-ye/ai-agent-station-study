package yhx.com.domain.agent.service.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeStartCommand {

    private String runId;
    private String sessionId;
    private String userId;
    private String agentId;
    private String userInput;
}
