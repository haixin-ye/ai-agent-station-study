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
public class AgentRunTracePO {

    private Long id;
    private String traceId;
    private String runId;
    private Long seq;
    private String traceType;
    private String payloadRef;
    private String summary;
    private LocalDateTime createdAt;
}
