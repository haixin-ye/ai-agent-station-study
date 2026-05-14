package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.context.ContextPlannerHandlingResult;
import yhx.com.domain.agent.model.valobj.invocation.MainAgentActionVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

public interface RuntimeComponentPorts {

    ContextPlannerHandlingResult prepareContext(RuntimeExecutionContext context);

    MainAgentActionVO invokeMainAgent(RuntimeExecutionContext context);
}
