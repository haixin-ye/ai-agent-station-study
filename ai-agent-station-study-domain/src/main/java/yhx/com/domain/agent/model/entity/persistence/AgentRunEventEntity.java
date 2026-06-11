package yhx.com.domain.agent.model.entity.persistence;

import yhx.com.domain.agent.model.valobj.enums.persistence.RunEventTypeEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunEventEntity {

    private String eventId;
    private String runId;
    private Long seq;
    private RunEventTypeEnumVO eventType;
    private String payloadRef;
    private Boolean userVisible;
    private LocalDateTime createdAt;
}
