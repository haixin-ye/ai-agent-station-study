package yhx.com.domain.agent.model.entity.persistence;

import yhx.com.domain.agent.model.valobj.enums.persistence.AuditTypeEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunAuditEntity {

    private String auditId;
    private String runId;
    private AuditTypeEnumVO auditType;
    private String payloadRef;
    private LocalDateTime createdAt;
}
