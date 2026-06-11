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
public class AgentRunEventPO {

    private Long id;
    private String eventId;
    private String runId;
    private Long seq;
    private String eventType;
    private String payloadRef;
    private Integer userVisible;
    private LocalDateTime createdAt;
}
