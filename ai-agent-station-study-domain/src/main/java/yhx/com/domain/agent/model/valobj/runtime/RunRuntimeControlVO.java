package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunRuntimeControlVO {
    private Integer currentLoopIndex;
    private Integer maxLoop;
    private RuntimeRecoveryCounters recoveryCounters;
}
