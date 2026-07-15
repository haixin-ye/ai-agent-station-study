package yhx.com.domain.agent.model.valobj.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingInputCreateCommand {

    private String runId;
    private String sessionId;
    private String sourceComponent;
    private String pendingType;
    private AskUserRequestVO askUserRequest;
    private ContinuationCheckpointVO continuation;
    private RuntimeExecutionContext runtimeContext;
    private LocalDateTime expiresAt;
}
