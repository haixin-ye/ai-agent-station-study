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

    /** Returns only events after the supplied cursor, ordered by sequence. */
    default List<AgentRunTraceEntity> listDebugTraceAfter(String runId, long lastSeq, int limit) {
        return listDebugTrace(runId, limit).stream()
                .filter(trace -> trace.getSeq() == null || trace.getSeq() > lastSeq)
                .toList();
    }
}
