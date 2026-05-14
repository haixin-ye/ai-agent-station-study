package yhx.com.domain.agent.model.entity.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMemoryEventEntity {

    private String eventId;
    private String runId;
    private String sessionId;
    private String memoryId;
    private String eventType;
    private String payloadRef;
    private LocalDateTime createdAt;
}
