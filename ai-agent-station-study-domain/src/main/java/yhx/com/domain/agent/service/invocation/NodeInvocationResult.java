package yhx.com.domain.agent.service.invocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.contract.ContractValidationResult;
import yhx.com.domain.agent.model.valobj.contract.RawOutputParseResult;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeInvocationResult {

    private NodeInvocationStatusEnumVO status;
    private String componentCode;
    private String contractVersion;
    private Object typedOutput;
    private String rawOutput;
    private RawOutputParseResult parseResult;
    private ContractValidationResult validationResult;
    private List<NodeInvocationAttempt> attempts;
    private String failureCode;
    private String failureMessage;
}
