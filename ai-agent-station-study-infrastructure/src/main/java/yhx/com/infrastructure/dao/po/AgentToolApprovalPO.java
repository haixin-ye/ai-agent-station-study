package yhx.com.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentToolApprovalPO {
    private Long id;
    private String approvalId;
    private String approvalKey;
    private String runId;
    private String toolCallId;
    private String status;
    private String permissionMode;
    private String argumentsHash;
    private String optionsRef;
    private String userAnswerRef;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
}
