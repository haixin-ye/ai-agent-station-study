package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentArtifactPO {

    private Long id;
    private String artifactId;
    private String sessionId;
    private String runId;
    private String artifactType;
    private String title;
    private String summary;
    private String contentRef;
    private Integer version;
    private LocalDateTime lastMentionedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
