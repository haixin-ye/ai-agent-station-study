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
public class ActionRequestSnapshotVO {

    private String actionType;
    private String capabilityCode;
    private String mcpServerCode;
    private String toolName;
    private Map<String, Object> arguments;
    private String argumentsRef;
    private String goal;
    private Map<String, Object> raw;
}
