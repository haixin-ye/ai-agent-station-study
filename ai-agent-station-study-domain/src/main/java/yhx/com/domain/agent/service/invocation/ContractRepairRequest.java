package yhx.com.domain.agent.service.invocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractRepairRequest {

    private String originalComponentCode;
    private String originalContractVersion;
    private String invalidRawOutput;
    private List<String> validationFailures;
    private String allowedRepairScope;
    private Integer currentRetryAttempt;
}
