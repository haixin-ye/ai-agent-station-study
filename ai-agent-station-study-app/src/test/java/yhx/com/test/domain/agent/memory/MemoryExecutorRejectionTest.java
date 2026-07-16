package yhx.com.test.domain.agent.memory;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import yhx.com.domain.agent.adapter.repository.IMemoryTaskRepository;
import yhx.com.domain.agent.model.entity.persistence.AgentMemoryTaskEntity;
import yhx.com.domain.agent.service.memory.gc.MemoryGcFollowupScheduler;
import yhx.com.domain.agent.service.memory.gc.MemoryGcTaskDispatcher;
import yhx.com.domain.agent.service.memory.gc.worker.MemoryGcTaskWorker;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MemoryExecutorRejectionTest {

    @Test
    public void rejected_gc_submission_leaves_created_task_pending() {
        IMemoryTaskRepository repository = mock(IMemoryTaskRepository.class);
        when(repository.createTask(org.mockito.ArgumentMatchers.any())).thenReturn("task-1");
        MemoryGcTaskWorker worker = mock(MemoryGcTaskWorker.class);
        when(worker.taskType()).thenReturn("TURN_SUMMARY");
        MemoryGcTaskDispatcher dispatcher = new MemoryGcTaskDispatcher(command -> {
            throw new RejectedExecutionException("saturated");
        }, List.of(worker));
        MemoryGcFollowupScheduler scheduler = new MemoryGcFollowupScheduler(repository, dispatcher);

        String taskId = scheduler.createAndDispatch("TURN_SUMMARY", "turn-1", "run-1", "sess-1", null);

        Assert.assertEquals("task-1", taskId);
        ArgumentCaptor<AgentMemoryTaskEntity> taskCaptor = ArgumentCaptor.forClass(AgentMemoryTaskEntity.class);
        verify(repository).createTask(taskCaptor.capture());
        Assert.assertEquals("PENDING", taskCaptor.getValue().getStatus());
        verify(worker, never()).handle("task-1");
    }
}
