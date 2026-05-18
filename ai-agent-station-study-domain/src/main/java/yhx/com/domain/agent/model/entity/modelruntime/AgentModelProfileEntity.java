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
public class AgentModelProfileEntity {

    private String modelProfileId;
    private String apiId;
    private String modelName;
    private String modelType;
    private Double defaultTemperature;
    private Integer defaultMaxOutputTokens;
    private Integer timeoutMs;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
