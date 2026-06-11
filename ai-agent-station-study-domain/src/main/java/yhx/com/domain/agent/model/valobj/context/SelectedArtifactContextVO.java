package yhx.com.domain.agent.model.valobj.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.context.ContextLevelEnumVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectedArtifactContextVO {

    private String artifactId;
    private ContextLevelEnumVO contextLevel;
    private String reason;
}
