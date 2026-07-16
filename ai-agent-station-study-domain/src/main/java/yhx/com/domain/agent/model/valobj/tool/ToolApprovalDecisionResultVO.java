package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.entity.persistence.ToolApprovalEntity;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolApprovalDecisionStatusEnumVO;
import yhx.com.domain.agent.model.valobj.interaction.PendingInputPauseIntentVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolApprovalDecisionResultVO {

    private ToolApprovalDecisionStatusEnumVO status;
    private ToolApprovalEntity approval;
    private String pendingInputId;
    private AskUserRequestVO askUserRequest;
    private PendingInputPauseIntentVO pauseIntent;
    private String failureCode;
    private String message;
}
