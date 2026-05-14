package yhx.com.domain.agent.model.entity.persistence;

import yhx.com.domain.agent.model.valobj.enums.runtime.RunStatusEnumVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunEntity {

    private String runId;
    private String sessionId;
    private String userId;
    private String agentId;
    private RunStatusEnumVO status;
    private RuntimePhaseEnumVO phase;
    private Boolean ragWasUsed;
    private String finalAnswerRef;
    private String failureCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
