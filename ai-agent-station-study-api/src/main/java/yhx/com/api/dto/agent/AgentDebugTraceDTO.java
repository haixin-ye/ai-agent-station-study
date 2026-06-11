package yhx.com.api.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDebugTraceDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String traceId;
    private String runId;
    private Long seq;
    private String traceType;
    private String componentName;
    private String actionType;
    private String severity;
    private String summary;
    private String payloadRef;
    private LocalDateTime createdAt;
}

