package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;

public interface IMemoryTaskRepository {

    String createTask(AgentMemoryTaskEntity task);

    void markRunning(String taskId);

    void markSucceeded(String taskId, String outputRef);

    void markFailed(String taskId, String failureCode, String failureMessage);
}
