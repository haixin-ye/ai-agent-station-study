package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimeFailureCodeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeSafeFailureVO {

    private RuntimeFailureCodeEnumVO failureCode;
    private String userMessage;
    private String developerMessage;
    private Boolean retryable;
    private RuntimePhaseEnumVO phase;
}
