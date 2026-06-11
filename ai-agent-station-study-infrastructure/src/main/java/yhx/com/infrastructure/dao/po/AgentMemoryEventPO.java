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
public class AgentMemoryEventPO {
    private Long id;
    private String eventId;
    private String runId;
    private String sessionId;
    private String memoryId;
    private String eventType;
    private String payloadRef;
    private LocalDateTime createdAt;
}
