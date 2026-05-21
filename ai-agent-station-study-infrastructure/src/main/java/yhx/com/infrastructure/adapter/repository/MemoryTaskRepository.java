package yhx.com.infrastructure.adapter.repository;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.infrastructure.dao.IAgentMemoryTaskDao;
import yhx.com.infrastructure.dao.po.AgentMemoryTaskPO;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public class MemoryTaskRepository implements IMemoryTaskRepository {

    @Resource
    private IAgentMemoryTaskDao agentMemoryTaskDao;

    @Override
    public String createTask(AgentMemoryTaskEntity task) {
        if (task.getTaskId() == null || task.getTaskId().isBlank()) {
            task.setTaskId("memory-task-" + UUID.randomUUID());
        }
        if (task.getCreatedAt() == null) {
            task.setCreatedAt(LocalDateTime.now());
        }
        if (task.getUpdatedAt() == null) {
            task.setUpdatedAt(LocalDateTime.now());
        }
        agentMemoryTaskDao.insert(toPO(task));
        return task.getTaskId();
    }

    @Override
    public void markRunning(String taskId) {
        agentMemoryTaskDao.updateRunning(taskId);
    }

    @Override
    public void markSucceeded(String taskId, String outputRef) {
        agentMemoryTaskDao.updateSucceeded(taskId, outputRef);
    }

    @Override
    public void markFailed(String taskId, String failureCode, String failureMessage) {
        agentMemoryTaskDao.updateFailed(taskId, failureCode, failureMessage);
    }

    private AgentMemoryTaskPO toPO(AgentMemoryTaskEntity entity) {
        return AgentMemoryTaskPO.builder()
                .taskId(entity.getTaskId())
                .taskType(entity.getTaskType())
                .sessionId(entity.getSessionId())
                .runId(entity.getRunId())
                .turnId(entity.getTurnId())
                .status(entity.getStatus())
                .attemptCount(entity.getAttemptCount())
                .failureCode(entity.getFailureCode())
                .failureMessage(entity.getFailureMessage())
                .inputRef(entity.getInputRef())
                .outputRef(entity.getOutputRef())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }
}
