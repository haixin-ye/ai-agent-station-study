package yhx.com.domain.agent.service.agent;

import yhx.com.domain.agent.model.valobj.agent.ParentChildRunRelationVO;
import yhx.com.domain.agent.model.valobj.runtime.RuntimeExecutionContext;

public class NoopChildAgentResultProjector extends ChildAgentResultProjector {

    public NoopChildAgentResultProjector() {
        super();
    }

    @Override
    public void project(RuntimeExecutionContext context, ParentChildRunRelationVO relation) {
        // Background child execution records terminal state in the registry.
        // The parent run projects child results when it resumes from WAITING_CHILDREN.
    }
}
