package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.service.memory.gc.MemoryGcRetryService;
import yhx.com.domain.agent.service.memory.gc.MemoryGcTaskDispatcher;
import yhx.com.domain.agent.service.memory.gc.worker.MemoryGcTaskWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MemoryGcRetryServiceTest {

    @Test
    public void retry_failed_tasks_dispatches_only_failed_tasks_below_max_attempts() {
        FakeMemoryTaskRepository repository = new FakeMemoryTaskRepository();
        repository.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-retry")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .status("FAILED")
                .attemptCount(1)
                .build());
        repository.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-exhausted")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .status("FAILED")
                .attemptCount(3)
                .build());
        RecordingWorker worker = new RecordingWorker(MemoryTaskTypeEnumVO.TURN_SUMMARY.name());
        MemoryGcRetryService retryService = new MemoryGcRetryService(repository,
                new MemoryGcTaskDispatcher(Runnable::run, List.of(worker)));

        int dispatched = retryService.retryFailedTasks(3, 10);

        Assert.assertEquals(1, dispatched);
        Assert.assertEquals(List.of("task-retry"), worker.handledTaskIds);
    }

    private static class RecordingWorker implements MemoryGcTaskWorker {
        private final String taskType;
        private final List<String> handledTaskIds = new ArrayList<>();

        private RecordingWorker(String taskType) {
            this.taskType = taskType;
        }

        @Override
        public String taskType() {
            return taskType;
        }

        @Override
        public void handle(String taskId) {
            handledTaskIds.add(taskId);
        }
    }

    private static class FakeMemoryTaskRepository implements IMemoryTaskRepository {
        private final List<AgentMemoryTaskEntity> tasks = new ArrayList<>();

        @Override
        public String createTask(AgentMemoryTaskEntity task) {
            tasks.add(task);
            return task.getTaskId();
        }

        @Override
        public Optional<AgentMemoryTaskEntity> findByTaskId(String taskId) {
            return tasks.stream().filter(task -> taskId.equals(task.getTaskId())).findFirst();
        }

        @Override
        public boolean hasOpenTask(String taskType, String sessionId) {
            return false;
        }

        @Override
        public List<AgentMemoryTaskEntity> listRetryableFailedTasks(int maxAttempts, int limit) {
            return tasks.stream()
                    .filter(task -> "FAILED".equals(task.getStatus()))
                    .filter(task -> task.getAttemptCount() == null || task.getAttemptCount() < maxAttempts)
                    .limit(limit)
                    .toList();
        }

        @Override
        public void markRunning(String taskId) {
        }

        @Override
        public void markSucceeded(String taskId, String outputRef) {
        }

        @Override
        public void markFailed(String taskId, String failureCode, String failureMessage) {
        }
    }
}
