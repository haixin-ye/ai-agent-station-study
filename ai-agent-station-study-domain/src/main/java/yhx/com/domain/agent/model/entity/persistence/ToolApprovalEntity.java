package yhx.com.domain.agent.model.entity.persistence;

import yhx.com.domain.agent.model.valobj.enums.persistence.ToolApprovalStatusEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolApprovalEntity {

    private String approvalId;
    private String approvalKey;
    private String runId;
    private String toolCallId;
    private ToolApprovalStatusEnumVO status;
    private String permissionMode;
    private String argumentsHash;
    private String optionsRef;
    private String userAnswerRef;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
}
