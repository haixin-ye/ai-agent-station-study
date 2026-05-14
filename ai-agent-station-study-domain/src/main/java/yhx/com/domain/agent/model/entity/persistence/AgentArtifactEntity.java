package yhx.com.domain.agent.model.entity.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentArtifactEntity {

    private String artifactId;
    private String sessionId;
    private String runId;
    private String artifactType;
    private String title;
    private String summary;
    private String contentRef;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
