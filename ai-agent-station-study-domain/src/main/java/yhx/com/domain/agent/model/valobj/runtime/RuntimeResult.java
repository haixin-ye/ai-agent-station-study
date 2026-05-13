package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeResult {

    private String runId;
    private String sessionId;
    private RunStatusEnumVO runStatus;
    private String finalAnswer;
    private String failureCode;
}


