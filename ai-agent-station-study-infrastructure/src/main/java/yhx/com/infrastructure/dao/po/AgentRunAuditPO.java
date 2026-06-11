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
public class AgentRunAuditPO {

    private Long id;
    private String auditId;
    private String runId;
    private String auditType;
    private String payloadRef;
    private LocalDateTime createdAt;
}
