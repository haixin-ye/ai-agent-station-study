package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeRecoveryStateVO {

    private RuntimeRecoveryCounters counters;
    private Integer maxLoop;
    private Integer maxContractRepair;
    private Integer maxFinalRepair;
    private Integer maxToolRetry;
    private Integer maxRagRetry;
    private Integer maxContextCompression;
}
