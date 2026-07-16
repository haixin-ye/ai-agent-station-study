package yhx.com.domain.agent.model.valobj.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;
import yhx.com.domain.agent.model.valobj.enums.runtime.RuntimePhaseEnumVO;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingInputPauseIntentVO {

    private String handler;
    private RuntimePhaseEnumVO resumePhase;
    private String sourceComponent;
    private String pendingType;
    private String expectedAnswerValueType;
    private AskUserRequestVO askUserRequest;
    private Map<String, Object> sourcePayload;
}
