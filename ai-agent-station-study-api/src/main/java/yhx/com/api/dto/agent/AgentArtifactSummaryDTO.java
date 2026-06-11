package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentArtifactSummaryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String artifactId;
    private String sessionId;
    private String runId;
    private String artifactType;
    private String title;
    private String summary;
    private Integer version;
    private LocalDateTime updatedAt;
}

