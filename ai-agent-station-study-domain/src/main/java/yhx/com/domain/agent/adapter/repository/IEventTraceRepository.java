package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentRunAuditEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunEventEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunTraceEntity;

import java.util.List;

public interface IEventTraceRepository {

    void appendUserVisibleEvent(AgentRunEventEntity event);

    void appendTrace(AgentRunTraceEntity trace);

    void appendAudit(AgentRunAuditEntity audit);

    List<AgentRunEventEntity> listUserVisibleEvents(String runId, int limit);

    List<AgentRunTraceEntity> listDebugTrace(String runId, int limit);
}
