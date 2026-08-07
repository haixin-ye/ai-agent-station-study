package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentRunContextEntity;
import yhx.com.domain.agent.model.entity.persistence.AgentRunLoopEntity;

import java.util.List;
import java.util.Optional;

public interface IRunContextRepository {
    void createContext(AgentRunContextEntity context);

    boolean updateContext(AgentRunContextEntity context, long expectedVersion);

    Optional<AgentRunContextEntity> findContext(String runId);

    void saveLoop(AgentRunLoopEntity loop);

    Optional<AgentRunLoopEntity> findLoop(String runId, Integer loopIndex);

    List<AgentRunLoopEntity> listLoops(String runId);
}
