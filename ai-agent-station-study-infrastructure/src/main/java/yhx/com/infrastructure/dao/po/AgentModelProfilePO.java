package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentModelProfilePO {

    private Long id;
    private String modelProfileId;
    private String apiId;
    private String modelName;
    private String modelType;
    private Double defaultTemperature;
    private Integer defaultMaxOutputTokens;
    private Integer embeddingDimensions;
    private Integer timeoutMs;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
