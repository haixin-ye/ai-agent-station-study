package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentUserVisibleEventDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String runId;
    private Long seq;
    private String eventType;
    private String phase;
    private String status;
    private String title;
    private String message;
    private String summary;
    private String artifactId;
    private List<String> artifactRefs;
    private String pendingId;
    private String pendingInputId;
    private Map<String, Object> safePayload;
    private LocalDateTime createdAt;
}
