package yhx.com.domain.agent.model.valobj.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryGovernanceItemVO {

    private String memoryId;
    private String memoryType;
    private String summary;
    private BigDecimal score;
    private String status;
    private String sourceRunId;
    private String sourceTurnId;
}
