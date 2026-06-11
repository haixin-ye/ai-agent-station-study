package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentNodePromptEntity;

import java.util.List;
import java.util.Optional;

public interface INodePromptRepository {

    List<AgentNodePromptEntity> listEnabledPrompts(String agentId, String nodeCode);

    Optional<AgentNodePromptEntity> findPromptByVersion(String agentId, String nodeCode, String promptVersion);
}
