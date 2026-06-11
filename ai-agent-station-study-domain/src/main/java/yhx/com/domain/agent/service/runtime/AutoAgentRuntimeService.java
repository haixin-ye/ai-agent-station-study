package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.runtime.RuntimeResumeCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeChildrenResumeCommand;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStepResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;

public interface AutoAgentRuntimeService {

    RuntimeStepResult start(RuntimeStartCommand command);

    RuntimeStepResult resume(RuntimeResumeCommand command);

    RuntimeStepResult resumeChildren(RuntimeChildrenResumeCommand command);

    RuntimeStepResult reportUnexpectedFailure(String runId, String sessionId, Throwable error);
}
