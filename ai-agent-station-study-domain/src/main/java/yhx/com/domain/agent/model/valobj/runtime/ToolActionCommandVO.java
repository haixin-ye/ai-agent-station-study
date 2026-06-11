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
public class ToolActionCommandVO {

    private String runId;
    private String sessionId;
    private String userId;
    private Integer loopIndex;
    private String capabilityCode;
    private String toolName;
    private String goal;
    private Map<String, Object> arguments;
    private Map<String, Object> rawToolIntent;
}
