package yhx.com.domain.agent.service.context;

import yhx.com.domain.agent.model.valobj.context.AskUserRequestVO;

public class ContextPlannerPendingInputHandler {

    public AskUserRequestVO passThrough(AskUserRequestVO askUserRequest) {
        return askUserRequest;
    }
}
