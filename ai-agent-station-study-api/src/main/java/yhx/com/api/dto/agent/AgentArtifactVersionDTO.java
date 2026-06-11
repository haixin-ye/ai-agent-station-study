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
public class AgentArtifactVersionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String artifactId;
    private Integer version;
    private String title;
    private String summary;
    private LocalDateTime createdAt;
}

