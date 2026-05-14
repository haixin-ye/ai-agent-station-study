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
public class ToolArgumentsMaterializationResultVO {

    private Map<String, Object> arguments;
    private String argumentsRef;
    private List<String> materializedArtifactIds;
    private List<String> materializedEvidenceIds;
    private String failureCode;
    private String failureMessage;
}
