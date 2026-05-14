package yhx.com.domain.agent.service.invocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.contract.ContractValidationResult;
import yhx.com.domain.agent.model.valobj.contract.RawOutputParseResult;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeInvocationAttempt {

    private Integer attemptNo;
    private String componentCode;
    private String prompt;
    private String rawOutput;
    private RawOutputParseResult parseResult;
    private ContractValidationResult validationResult;
    private NodeInvocationFailureTypeEnumVO failureType;
    private String failureMessage;
    private Boolean repairAttempt;
}
