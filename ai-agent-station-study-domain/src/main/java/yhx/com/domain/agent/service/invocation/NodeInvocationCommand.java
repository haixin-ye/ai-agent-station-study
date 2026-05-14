package yhx.com.domain.agent.service.invocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeInvocationCommand {

    private String runId;
    private String agentId;
    private String componentCode;
    private String contractVersion;
    private String promptVersion;
    private String modelCode;
    private Double temperature;
    private Integer maxOutputTokens;
    private Object inputView;
    private Integer maxRepairAttempts;
    private Map<String, Object> invocationMetadata;
}
