package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

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
    private String inputType;
    private Map<String, Object> requestMetadata;
}

