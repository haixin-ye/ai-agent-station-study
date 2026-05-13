package yhx.com.domain.agent.service.runtime;

import yhx.com.domain.agent.model.valobj.runtime.RuntimeResult;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeStartCommand;

public interface AutoAgentRuntimeService {

    RuntimeResult start(RuntimeStartCommand command);
}
