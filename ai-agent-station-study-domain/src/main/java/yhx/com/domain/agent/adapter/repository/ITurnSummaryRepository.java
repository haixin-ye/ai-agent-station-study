package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;

import java.util.List;
import java.util.Optional;

public interface ITurnSummaryRepository {

    String saveSummary(AgentTurnSummaryEntity summary);

    Optional<AgentTurnSummaryEntity> findSummaryByTurnId(String turnId);

    List<AgentTurnSummaryEntity> listByTurnIds(List<String> turnIds);
}
