package yhx.com.domain.agent.service.memory.gc;

import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;

import java.util.List;

public class MemoryGcRetryService {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int DEFAULT_LIMIT = 20;

    private final IMemoryTaskRepository taskRepository;
    private final MemoryGcTaskDispatcher taskDispatcher;

    public MemoryGcRetryService(IMemoryTaskRepository taskRepository, MemoryGcTaskDispatcher taskDispatcher) {
        this.taskRepository = taskRepository;
        this.taskDispatcher = taskDispatcher;
    }

    public int retryFailedTasks(int maxAttempts, int limit) {
        if (taskRepository == null || taskDispatcher == null) {
            return 0;
        }
        int effectiveMaxAttempts = maxAttempts <= 0 ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : limit;
        List<AgentMemoryTaskEntity> tasks = taskRepository.listRetryableFailedTasks(effectiveMaxAttempts, effectiveLimit);
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }
        int dispatched = 0;
        for (AgentMemoryTaskEntity task : tasks) {
            if (task == null || task.getTaskType() == null || task.getTaskId() == null) {
                continue;
            }
            taskDispatcher.dispatch(task.getTaskType(), task.getTaskId());
            dispatched++;
        }
        return dispatched;
    }
}
