package yhx.com.domain.agent.model.valobj.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.agent.ChildAgentRunStatusEnumVO;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentActionHandlerResultVO {

    private String action;
    private Boolean terminal;
    private ChildAgentRunStatusEnumVO status;
    private SubAgentCommitVO commit;
    private String failureMessage;
    private String pendingInputId;
    private AskUserRequestVO askUserRequest;
    private String message;
    private Map<String, Object> resultSnapshot;

    public boolean isTerminal() {
        return Boolean.TRUE.equals(terminal);
    }
}
