package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentTurnEntity;

import java.util.List;
import java.util.Optional;

public interface ITurnRepository {

    String saveCompletedTurn(AgentTurnEntity turn);

    Optional<AgentTurnEntity> findByTurnId(String turnId);

    Optional<AgentTurnEntity> findByRunId(String runId);

    List<AgentTurnEntity> listRecentCompletedTurns(String sessionId, int limit);

    default List<AgentTurnEntity> listRecentCompletedTurns(int limit) {
        return List.of();
    }

    List<AgentTurnEntity> listCompletedTurnsBefore(String sessionId, Long beforeTurnNo, int limit);
}
