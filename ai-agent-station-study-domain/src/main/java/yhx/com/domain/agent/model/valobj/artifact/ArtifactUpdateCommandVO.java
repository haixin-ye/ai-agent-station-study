package yhx.com.domain.agent.model.valobj.artifact;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactUpdateCommandVO {

    private String artifactId;
    private String title;
    private String summary;
    private String content;
    private String updateMode;
}
