package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.service.memory.gc.MemoryGcTaskQueryService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MemoryGcTaskQueryServiceTest {

    @Test
    public void list_tasks_filters_by_status_and_limit() {
        FakeMemoryTaskRepository repository = new FakeMemoryTaskRepository();
        repository.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-1")
                .taskType(MemoryTaskTypeEnumVO.TURN_SUMMARY.name())
                .status("FAILED")
                .attemptCount(2)
                .build());
        repository.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-2")
                .taskType(MemoryTaskTypeEnumVO.CONVERSATION_ROLLUP.name())
                .status("SUCCEEDED")
                .attemptCount(1)
                .build());
        repository.tasks.add(AgentMemoryTaskEntity.builder()
                .taskId("task-3")
                .taskType(MemoryTaskTypeEnumVO.LONG_TERM_MEMORY_EXTRACTION.name())
                .status("FAILED")
                .attemptCount(1)
                .build());
        MemoryGcTaskQueryService service = new MemoryGcTaskQueryService(repository);

        List<AgentMemoryTaskEntity> tasks = service.listTasks("FAILED", 1);

        Assert.assertEquals(1, tasks.size());
        Assert.assertEquals("task-1", tasks.get(0).getTaskId());
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
            return List.of();
        }

        @Override
        public List<AgentMemoryTaskEntity> listTasks(String status, int limit) {
            return tasks.stream()
                    .filter(task -> status == null || status.isBlank() || status.equals(task.getStatus()))
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
