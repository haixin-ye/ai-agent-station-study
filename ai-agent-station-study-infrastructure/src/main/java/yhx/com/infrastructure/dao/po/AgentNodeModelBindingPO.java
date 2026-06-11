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
public class AgentNodeModelBindingPO {

    private Long id;
    private String bindingId;
    private String nodeCode;
    private String modelProfileId;
    private String promptVersion;
    private String contractVersion;
    private Double temperature;
    private Integer maxOutputTokens;
    private Integer maxRepairAttempts;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
