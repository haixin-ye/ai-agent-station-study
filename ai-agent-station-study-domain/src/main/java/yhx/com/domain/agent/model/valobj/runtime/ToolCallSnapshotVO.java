package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallSnapshotVO {

    private String toolCallId;
    private String toolInvocationId;
    private String approvalId;
    private String approvalStatus;
    private String receiptRef;
    private String failureCode;
    private String failureMessage;
}
