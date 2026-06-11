package yhx.com.domain.agent.model.valobj.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.invocation.VerificationResultVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagVerificationRouteResultVO {

    private boolean verificationRequired;
    private VerificationResultVO verificationResult;
    private RuntimeFailureCodeEnumVO failureCode;
    private String message;
}
