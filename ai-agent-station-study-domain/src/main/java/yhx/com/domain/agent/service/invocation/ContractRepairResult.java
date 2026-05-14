package yhx.com.domain.agent.service.invocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractRepairResult {

    private Boolean repaired;
    private Object typedOutput;
    private String rawOutput;
    private String failureMessage;
}
