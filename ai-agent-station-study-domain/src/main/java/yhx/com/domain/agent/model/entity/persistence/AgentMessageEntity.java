package yhx.com.domain.agent.model.entity.persistence;

import yhx.com.domain.agent.model.valobj.enums.persistence.MessageRoleEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessageEntity {

    private String messageId;
    private String sessionId;
    private String runId;
    private MessageRoleEnumVO role;
    private String contentRef;
    private String metadataRef;
    private Boolean visibleToUser;
    private LocalDateTime createdAt;
}
