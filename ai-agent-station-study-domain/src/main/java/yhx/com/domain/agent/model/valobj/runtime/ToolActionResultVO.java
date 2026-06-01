package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.context.MaterializedEvidenceVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.ToolActionStatusEnumVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolActionResultVO {

    private ToolActionStatusEnumVO status;
    private String pendingInputId;
    private AskUserRequestVO askUserRequest;
    private List<String> evidenceIds;
    private List<MaterializedEvidenceVO> evidence;
    private RuntimeSafeFailureVO safeFailure;
    private String message;
}
