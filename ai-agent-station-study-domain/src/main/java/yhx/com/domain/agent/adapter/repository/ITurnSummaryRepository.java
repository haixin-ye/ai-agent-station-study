package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentTurnSummaryEntity;

import java.util.List;
import java.util.Optional;

public interface ITurnSummaryRepository {

    String saveSummary(AgentTurnSummaryEntity summary);

    Optional<AgentTurnSummaryEntity> findSummaryById(String summaryId);

    Optional<AgentTurnSummaryEntity> findSummaryByTurnId(String turnId);

    List<AgentTurnSummaryEntity> listByTurnIds(List<String> turnIds);

    List<AgentTurnSummaryEntity> listRecentActiveSummaries(String sessionId, int limit);

    default int countActiveSummaries(String sessionId) {
        return listRecentActiveSummaries(sessionId, Integer.MAX_VALUE).size();
    }

    default int countAllActiveSummaries() {
        return 0;
    }

    void markSummariesRolledUp(List<String> summaryIds);
}
