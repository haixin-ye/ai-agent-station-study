package yhx.com.domain.agent.model.entity.persistence;

import yhx.com.domain.agent.model.valobj.enums.persistence.TraceTypeEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunTraceEntity {

    private String traceId;
    private String runId;
    private Long seq;
    private TraceTypeEnumVO traceType;
    private String payloadRef;
    private LocalDateTime createdAt;
}
