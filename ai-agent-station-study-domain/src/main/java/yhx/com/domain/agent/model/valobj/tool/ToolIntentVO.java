package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolIntentVO {

    private String goal;
    private String capabilityCode;
    private String mcpServerCode;
    private String toolName;
    private Map<String, Object> arguments;
    private List<String> requiredArtifactIds;
    private List<String> requiredEvidenceIds;
    private Map<String, Object> expectedOutcome;
}
