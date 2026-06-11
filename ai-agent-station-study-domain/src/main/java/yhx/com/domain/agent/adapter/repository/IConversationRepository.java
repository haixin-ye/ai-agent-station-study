package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentMessageEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentSessionEntity;

import java.util.List;
import java.util.Optional;

public interface IConversationRepository {

    String createSession(AgentSessionEntity session);

    Optional<AgentSessionEntity> findSession(String sessionId);

    void appendMessage(AgentMessageEntity message);

    Optional<AgentMessageEntity> findMessageById(String messageId);

    List<AgentMessageEntity> listRecentVisibleMessages(String sessionId, int limit);
}
