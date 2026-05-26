package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentSessionTaskSummaryEntity;

import java.util.Optional;

public interface ISessionTaskSummaryRepository {

    String saveSummary(AgentSessionTaskSummaryEntity summary);

    Optional<AgentSessionTaskSummaryEntity> findActiveBySessionId(String sessionId);

    int nextVersionNo(String sessionId);

    void markActiveSuperseded(String sessionId);
}
