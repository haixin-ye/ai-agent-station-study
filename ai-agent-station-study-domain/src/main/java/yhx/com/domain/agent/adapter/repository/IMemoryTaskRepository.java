package yhx.com.domain.agent.adapter.repository;

import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;

import java.util.List;
import java.util.Optional;

public interface IMemoryTaskRepository {

    String createTask(AgentMemoryTaskEntity task);

    Optional<AgentMemoryTaskEntity> findByTaskId(String taskId);

    boolean hasOpenTask(String taskType, String sessionId);

    List<AgentMemoryTaskEntity> listRetryableFailedTasks(int maxAttempts, int limit);

    List<AgentMemoryTaskEntity> listTasks(String status, int limit);

    void markRunning(String taskId);

    void markSucceeded(String taskId, String outputRef);

    void markFailed(String taskId, String failureCode, String failureMessage);
}
