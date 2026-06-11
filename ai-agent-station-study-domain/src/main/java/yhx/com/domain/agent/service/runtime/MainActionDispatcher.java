package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.MainActionHandlerResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

public interface MainActionDispatcher {

    MainActionHandlerResult dispatch(RuntimeExecutionContext context, MainAgentActionVO action);
}
