package yhx.com.domain.agent.model.entity.modelruntime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentModelApiEntity {

    private String apiId;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String completionsPath;
    private String embeddingsPath;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
