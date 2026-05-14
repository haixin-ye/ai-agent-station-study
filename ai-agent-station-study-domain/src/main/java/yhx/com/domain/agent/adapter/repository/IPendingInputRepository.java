package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;

import java.util.Optional;

public interface IPendingInputRepository {

    String createPendingInput(AgentPendingInputEntity pendingInput);

    void markAnswered(String pendingId, String userAnswerRef);

    Optional<AgentPendingInputEntity> findActivePendingInput(String runId);

    Optional<AgentPendingInputEntity> findByPendingId(String pendingId);

    void markCancelled(String pendingId);

    void markExpired(String pendingId);
}
