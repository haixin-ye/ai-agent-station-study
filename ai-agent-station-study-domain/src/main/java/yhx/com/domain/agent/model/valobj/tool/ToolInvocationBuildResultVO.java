package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocationBuildResultVO {

    private String status;
    private ToolInvocationRequestVO request;
    private String toolCallId;
    private String pendingInputId;
    private AskUserRequestVO askUserRequest;
    private String failureCode;
    private String failureMessage;
}
