package yhx.com.domain.agent.service.memory.gc;

import lombok.extern.slf4j.Slf4j;

import yhx.com.domain.agent.service.memory.gc.worker.MemoryGcTaskWorker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
public class MemoryGcTaskDispatcher {

    private final Executor executor;
    private final Map<String, MemoryGcTaskWorker> workers = new ConcurrentHashMap<>();

    public MemoryGcTaskDispatcher(Executor executor, List<MemoryGcTaskWorker> workers) {
        this.executor = executor;
        if (workers != null) {
            workers.stream()
                    .filter(worker -> worker != null && worker.taskType() != null)
                    .forEach(worker -> this.workers.put(worker.taskType(), worker));
        }
    }

    public void dispatch(String taskType, String taskId) {
        if (taskType == null || taskId == null || executor == null) {
            return;
        }
        MemoryGcTaskWorker worker = workers.get(taskType);
        if (worker == null) {
            return;
        }
        try {
            executor.execute(() -> worker.handle(taskId));
        } catch (RejectedExecutionException error) {
            log.warn("[AutoAgent][memory-task-rejected] taskType={}, taskId={}; task remains PENDING",
                    taskType, taskId, error);
        }
    }
}
