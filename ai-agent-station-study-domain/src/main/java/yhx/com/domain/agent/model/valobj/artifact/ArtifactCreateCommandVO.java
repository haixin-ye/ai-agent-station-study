package yhx.com.domain.agent.model.valobj.artifact;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactCreateCommandVO {

    private String sessionId;
    private String runId;
    private String artifactType;
    private String title;
    private String summary;
    private String content;
}
