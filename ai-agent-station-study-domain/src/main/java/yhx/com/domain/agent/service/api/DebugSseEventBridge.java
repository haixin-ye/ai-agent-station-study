package yhx.com.domain.agent.service.api;

import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;

import java.util.List;

public class DebugSseEventBridge {

    private final AgentDebugFacade agentDebugFacade;

    public DebugSseEventBridge(AgentDebugFacade agentDebugFacade) {
        this.agentDebugFacade = agentDebugFacade;
    }

    public List<AgentRunTraceEntity> replayDebugEvents(String runId, Long lastSeq, int limit) {
        return agentDebugFacade.listTracesAfter(runId, lastSeq == null ? 0L : lastSeq, limit);
    }
}

