package yhx.com.domain.agent.service.memory.gc;

import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.service.memory.gc.worker.MemoryGcTaskWorker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class MemoryGcFollowupScheduler {

    private final IMemoryTaskRepository taskRepository;
    private final MemoryGcTaskDispatcher taskDispatcher;
    private final Executor executor;
    private final Supplier<List<MemoryGcTaskWorker>> workerSupplier;

    public MemoryGcFollowupScheduler(IMemoryTaskRepository taskRepository, MemoryGcTaskDispatcher taskDispatcher) {
        this.taskRepository = taskRepository;
        this.taskDispatcher = taskDispatcher;
        this.executor = null;
        this.workerSupplier = null;
    }

    public MemoryGcFollowupScheduler(IMemoryTaskRepository taskRepository,
                                     Executor executor,
                                     Supplier<List<MemoryGcTaskWorker>> workerSupplier) {
        this.taskRepository = taskRepository;
        this.taskDispatcher = null;
        this.executor = executor;
        this.workerSupplier = workerSupplier;
    }

    public String createAndDispatch(String taskType,
                                    String turnId,
                                    String runId,
                                    String sessionId,
                                    String inputRef) {
        if (taskRepository == null || taskType == null || taskType.isBlank()) {
            return null;
        }
        String taskId = taskRepository.createTask(AgentMemoryTaskEntity.builder()
                .taskType(taskType)
                .turnId(turnId)
                .runId(runId)
                .sessionId(sessionId)
                .inputRef(inputRef)
                .status("PENDING")
                .attemptCount(0)
                .createdAt(LocalDateTime.now())
                .build());
        if (taskDispatcher != null) {
            taskDispatcher.dispatch(taskType, taskId);
        } else if (executor != null && workerSupplier != null) {
            new MemoryGcTaskDispatcher(executor, workerSupplier.get()).dispatch(taskType, taskId);
        }
        return taskId;
    }

    public String createAndDispatchIfNoOpenSessionTask(String taskType,
                                                       String turnId,
                                                       String runId,
                                                       String sessionId,
                                                       String inputRef) {
        if (taskRepository == null || taskRepository.hasOpenTask(taskType, sessionId)) {
            return null;
        }
        return createAndDispatch(taskType, turnId, runId, sessionId, inputRef);
    }

    public String createAndDispatchIfNoOpenTaskType(String taskType,
                                                    String turnId,
                                                    String runId,
                                                    String sessionId,
                                                    String inputRef) {
        if (taskRepository == null || taskRepository.hasOpenTaskType(taskType)) {
            return null;
        }
        return createAndDispatch(taskType, turnId, runId, sessionId, inputRef);
    }

    public String createAndDispatchIfNoTurnTask(String taskType,
                                                String turnId,
                                                String runId,
                                                String sessionId,
                                                String inputRef) {
        if (taskRepository == null || taskRepository.hasTaskForTurn(taskType, turnId)) {
            return null;
        }
        return createAndDispatch(taskType, turnId, runId, sessionId, inputRef);
    }
}
