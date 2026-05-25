package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.model.valobj.enums.memory.MemoryTaskTypeEnumVO;
import yhx.com.domain.agent.service.memory.gc.MemoryGcOrchestrator;
import yhx.com.domain.agent.service.memory.gc.MemoryGcTaskDispatcher;
import yhx.com.domain.agent.service.memory.gc.worker.MemoryGcTaskWorker;

import java.util.ArrayList;
import java.util.List;

public class MemoryGcOrchestratorTest {

    @Test
    public void turn_completed_creates_turn_summary_task_and_dispatches_worker() {
        FakeMemoryTaskRepository repository = new FakeMemoryTaskRepository();
        RecordingWorker worker = new RecordingWorker(MemoryTaskTypeEnumVO.TURN_SUMMARY.name());
        MemoryGcOrchestrator orchestrator = new MemoryGcOrchestrator(repository,
                new MemoryGcTaskDispatcher(Runnable::run, List.of(worker)));

        orchestrator.onTurnCompleted("turn-1");

        Assert.assertEquals(1, repository.tasks.size());
        Assert.assertEquals(MemoryTaskTypeEnumVO.TURN_SUMMARY.name(), repository.tasks.get(0).getTaskType());
        Assert.assertEquals("turn-1", repository.tasks.get(0).getTurnId());
        Assert.assertEquals("PENDING", repository.tasks.get(0).getStatus());
        Assert.assertEquals(List.of("task-1"), worker.handledTaskIds);
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
            task.setTaskId("task-" + (tasks.size() + 1));
            tasks.add(task);
            return task.getTaskId();
        }

        @Override
        public java.util.Optional<AgentMemoryTaskEntity> findByTaskId(String taskId) {
            return tasks.stream().filter(task -> taskId.equals(task.getTaskId())).findFirst();
        }

        @Override
        public boolean hasOpenTask(String taskType, String sessionId) {
            return tasks.stream()
                    .anyMatch(task -> taskType.equals(task.getTaskType())
                            && sessionId.equals(task.getSessionId())
                            && ("PENDING".equals(task.getStatus()) || "RUNNING".equals(task.getStatus())));
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
