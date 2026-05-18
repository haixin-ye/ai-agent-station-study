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
public class AgentNodeModelBindingEntity {

    private String bindingId;
    private String nodeCode;
    private String modelProfileId;
    private String promptVersion;
    private String contractVersion;
    private Double temperature;
    private Integer maxOutputTokens;
    private Integer maxRepairAttempts;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
