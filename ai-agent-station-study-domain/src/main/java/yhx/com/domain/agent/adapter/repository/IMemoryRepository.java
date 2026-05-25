package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentConversationSummaryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryEventEntity;

import java.util.List;
import java.util.Optional;

public interface IMemoryRepository {

    List<AgentMemoryEntity> findMemoryCandidates(String userId, String sessionId, String query, int limit);

    Optional<AgentMemoryEntity> findMemory(String memoryId);

    String saveConversationSummary(AgentConversationSummaryEntity summary);

    String saveLongTermMemory(AgentMemoryEntity memory);

    String recordMemoryEvent(AgentMemoryEventEntity event);
}
