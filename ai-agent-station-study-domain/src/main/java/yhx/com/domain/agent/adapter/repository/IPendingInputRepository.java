package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentPendingInputEntity;

import java.util.Optional;

public interface IPendingInputRepository {

    String createPendingInput(AgentPendingInputEntity pendingInput);

    int markAnswered(String pendingId, String runId, String userAnswerRef);

    Optional<AgentPendingInputEntity> findActivePendingInput(String runId);

    Optional<AgentPendingInputEntity> findByPendingId(String pendingId);

    int markCancelled(String pendingId, String runId);

    int markExpired(String pendingId, String runId);
}
