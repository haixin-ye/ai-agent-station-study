package yhx.com.domain.agent.service.api;

import yhx.com.domain.agent.model.entity.persistence.AgentRunEventEntity;

import java.util.List;

public class SseUserEventBridge {

    private final AgentQueryFacade agentQueryFacade;

    public SseUserEventBridge(AgentQueryFacade agentQueryFacade) {
        this.agentQueryFacade = agentQueryFacade;
    }

    public List<AgentRunEventEntity> replayUserVisibleEvents(String runId, Long lastSeq, int limit) {
        return agentQueryFacade.listUserVisibleEvents(runId, limit).stream()
                .filter(event -> lastSeq == null || event.getSeq() == null || event.getSeq() > lastSeq)
                .toList();
    }
}

