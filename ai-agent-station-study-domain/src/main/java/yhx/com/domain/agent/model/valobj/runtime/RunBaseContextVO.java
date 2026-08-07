package yhx.com.domain.agent.model.valobj.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.MainAgentStateViewVO;
import yhx.com.domain.agent.model.valobj.context.UserClarificationVO;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunBaseContextVO {
    private String runId;
    private String sessionId;
    private String userId;
    private String agentId;
    private String userMessageId;
    private String userInput;
    private MainAgentStateViewVO selectedSessionContext;
    private List<UserClarificationVO> userClarifications;
}
