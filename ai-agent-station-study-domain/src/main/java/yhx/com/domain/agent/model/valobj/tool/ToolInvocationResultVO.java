package yhx.com.domain.agent.model.valobj.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.enums.tool.ToolInvocationStatusEnumVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocationResultVO {

    private ToolInvocationStatusEnumVO status;
    private String toolCallId;
    private String toolInvocationId;
    private String receiptRef;
    private String resultSummary;
    private String failureCode;
    private String failureMessage;
}
