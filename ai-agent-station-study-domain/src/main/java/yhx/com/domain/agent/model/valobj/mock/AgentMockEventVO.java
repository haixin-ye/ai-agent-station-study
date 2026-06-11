package yhx.com.domain.agent.model.valobj.mock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMockEventVO {

    private String eventId;
    private String runId;
    private Long seq;
    private String eventType;
    private String title;
    private String message;
    private String artifactId;
    private String pendingId;
    private Map<String, Object> safePayload;
    private LocalDateTime createdAt;
}

